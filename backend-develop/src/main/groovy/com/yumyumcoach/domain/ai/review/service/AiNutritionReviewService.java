package com.yumyumcoach.domain.ai.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumyumcoach.domain.ai.review.dto.NutritionReviewResponse;
import com.yumyumcoach.domain.ai.review.entity.AiNutritionReview;
import com.yumyumcoach.domain.ai.review.mapper.AiNutritionReviewMapper;
import com.yumyumcoach.domain.ai.service.GeminiClient;
import com.yumyumcoach.domain.stats.dto.DailyDietStat;
import com.yumyumcoach.domain.stats.dto.WeeklyStatsResponse;
import com.yumyumcoach.domain.stats.service.WeeklyStatsService;
import com.yumyumcoach.domain.user.dto.MyPageResponse;
import com.yumyumcoach.domain.user.service.UserService;
import com.yumyumcoach.global.exception.BusinessException;
import com.yumyumcoach.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiNutritionReviewService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiNutritionReviewMapper aiNutritionReviewMapper;
    private final GeminiClient geminiClient;
    private final WeeklyStatsService weeklyStatsService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public NutritionReviewResponse evaluate(String email, LocalDate referenceDate) {
        LocalDate evaluationDate = determineLimitDate(referenceDate);
        AiNutritionReview existing = aiNutritionReviewMapper.findByEmailAndEvaluationDate(email, evaluationDate);
        if (existing != null) {
            return toResponse(existing);
        }

        MyPageResponse profile = userService.getMyPage(email);
        WeeklyStatsResponse weeklyStats = weeklyStatsService.getWeeklyStats(email, evaluationDate);

        String prompt = buildPrompt(profile, weeklyStats, evaluationDate);
        String answer = geminiClient.generate(prompt);

        AiNutritionReview parsed = parseAnswer(email, referenceDate, evaluationDate, answer);
        aiNutritionReviewMapper.insertReview(parsed);
        return toResponse(parsed);
    }

    @Transactional(readOnly = true)
    public NutritionReviewResponse get(String email, LocalDate referenceDate) {
        LocalDate evaluationDate = determineLimitDate(referenceDate);
        AiNutritionReview review = aiNutritionReviewMapper.findByEmailAndEvaluationDate(email, evaluationDate);
        if (review == null) {
            return NutritionReviewResponse.builder()
                    .weekStartDate(toWeekStart(referenceDate))
                    .evaluationDate(evaluationDate)
                    .summary(null)
                    .createdAt(null)
                    .build();
        }
        return toResponse(review);
    }

    private LocalDate determineLimitDate(LocalDate referenceDate) {
        LocalDate now = LocalDate.now(KST);
        LocalDate weekStart = toWeekStart(referenceDate);
        LocalDate weekEnd = weekStart.plusDays(6);
        if (now.isBefore(weekStart)) {
            return weekStart;
        }
        if (now.isAfter(weekEnd)) {
            return weekEnd;
        }
        if (referenceDate.isBefore(weekStart)) {
            return weekStart;
        }
        if (referenceDate.isAfter(now)) {
            return now;
        }
        return referenceDate;
    }

    private String buildPrompt(MyPageResponse profile, WeeklyStatsResponse weeklyStats, LocalDate evaluationDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("이번 주(월요일 시작) 영양 섭취를 평가해주세요. 평가 기준일은 ").append(evaluationDate)
                .append("이고 요일은 ").append(evaluationDate.getDayOfWeek()).append("입니다.\n");
        sb.append("탄수화물, 단백질, 지방, 칼로리 네 가지를 각각 '부족', '적당', '과다' 중 하나로 판단하고 간단한 한글 문장으로 사유를 작성하세요.\n");
        sb.append("JSON으로 {summary, carbohydrateStatus, proteinStatus, fatStatus, calorieStatus} 를 반환하세요. summary는 문단입니다.\n");

        Optional.ofNullable(profile).map(MyPageResponse::getHealth).ifPresent(health -> sb
                .append("[건강 정보] 키 ").append(Optional.ofNullable(health.getHeight()).orElse(0.0)).append("cm, 현재 체중 ")
                .append(Optional.ofNullable(health.getWeight()).orElse(0.0)).append("kg, 목표 체중 ")
                .append(Optional.ofNullable(health.getGoalWeight()).orElse(0.0)).append("kg, 활동 수준 ")
                .append(Optional.ofNullable(health.getActivityLevel()).orElse("미입력")).append("\n"));

        sb.append("[주간 식단 데이터]\n");
        weeklyStats.getDietStats().stream()
                .filter(stat -> !stat.getDate().isAfter(evaluationDate))
                .sorted(Comparator.comparing(DailyDietStat::getDate))
                .forEach(stat -> sb.append(stat.getDate()).append("(").append(stat.getDayOfWeekKr()).append(") ")
                        .append("탄수화물 ").append(stat.getTotalCarbohydrate()).append("g, 단백질 ")
                        .append(stat.getTotalProtein()).append("g, 지방 ")
                        .append(stat.getTotalFat()).append("g, 칼로리 ")
                        .append(stat.getTotalCalories()).append("kcal\n"));

        sb.append("평가할 때 현재 요일 이전 데이터만 활용하고, 오늘 남은 끼니를 고려한 조언을 덧붙여주세요.");
        return sb.toString();
    }

    private AiNutritionReview parseAnswer(String email, LocalDate referenceDate, LocalDate evaluationDate, String answer) {
        AiNutritionReview.AiNutritionReviewBuilder builder = AiNutritionReview.builder()
                .email(email)
                .weekStartDate(toWeekStart(referenceDate))
                .evaluationDate(evaluationDate)
                .summary(answer)
                .createdAt(LocalDateTime.now(KST));

        try {
            JsonNode root = objectMapper.readTree(answer);
            builder.summary(root.path("summary").asText(answer));
            builder.carbohydrateStatus(root.path("carbohydrateStatus").asText(null));
            builder.proteinStatus(root.path("proteinStatus").asText(null));
            builder.fatStatus(root.path("fatStatus").asText(null));
            builder.calorieStatus(root.path("calorieStatus").asText(null));
        } catch (Exception ignored) {
        }

        AiNutritionReview review = builder.build();
        if (review.getSummary() == null || review.getSummary().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "영양 평가 결과 파싱에 실패했습니다.");
        }
        return review;
    }

    private NutritionReviewResponse toResponse(AiNutritionReview review) {
        return NutritionReviewResponse.builder()
                .weekStartDate(review.getWeekStartDate())
                .evaluationDate(review.getEvaluationDate())
                .summary(review.getSummary())
                .carbohydrateStatus(review.getCarbohydrateStatus())
                .proteinStatus(review.getProteinStatus())
                .fatStatus(review.getFatStatus())
                .calorieStatus(review.getCalorieStatus())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private LocalDate toWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
