package com.yumyumcoach.domain.ai.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumyumcoach.domain.ai.review.dto.ExerciseReviewResponse;
import com.yumyumcoach.domain.ai.review.entity.AiExerciseReview;
import com.yumyumcoach.domain.ai.review.mapper.AiExerciseReviewMapper;
import com.yumyumcoach.domain.ai.service.GeminiClient;
import com.yumyumcoach.domain.stats.dto.DailyExerciseStat;
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
public class AiExerciseReviewService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiExerciseReviewMapper aiExerciseReviewMapper;
    private final GeminiClient geminiClient;
    private final WeeklyStatsService weeklyStatsService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ExerciseReviewResponse evaluate(String email, LocalDate referenceDate) {
        LocalDate evaluationDate = determineLimitDate(referenceDate);
        AiExerciseReview existing = aiExerciseReviewMapper.findByEmailAndEvaluationDate(email, evaluationDate);
        if (existing != null) {
            return toResponse(existing);
        }

        MyPageResponse profile = userService.getMyPage(email);
        WeeklyStatsResponse weeklyStats = weeklyStatsService.getWeeklyStats(email, evaluationDate);

        String prompt = buildPrompt(profile, weeklyStats, evaluationDate);
        String answer = geminiClient.generate(prompt);

        AiExerciseReview parsed = parseAnswer(email, referenceDate, evaluationDate, answer);
        aiExerciseReviewMapper.insertReview(parsed);
        return toResponse(parsed);
    }

    @Transactional(readOnly = true)
    public ExerciseReviewResponse get(String email, LocalDate referenceDate) {
        LocalDate evaluationDate = determineLimitDate(referenceDate);
        AiExerciseReview review = aiExerciseReviewMapper.findByEmailAndEvaluationDate(email, evaluationDate);
        if (review == null) {
            return ExerciseReviewResponse.builder()
                    .weekStartDate(toWeekStart(referenceDate))
                    .evaluationDate(evaluationDate)
                    .summary(null)
                    .recommendation(null)
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
        sb.append("이번 주 운동량을 평가해주세요. 기준일은 ").append(evaluationDate)
                .append("이고 요일은 ").append(evaluationDate.getDayOfWeek()).append("입니다.\n");
        sb.append("운동량을 '부족', '적당', '많음' 중 하나로 요약하고, 앞으로 남은 요일에 추천할 운동을 한글 문장으로 제시하세요.\n");
        sb.append("JSON으로 {summary, recommendation}을 반환하세요.\n");

        Optional.ofNullable(profile).map(MyPageResponse::getHealth).ifPresent(health -> sb
                .append("[건강 정보] 키 ").append(Optional.ofNullable(health.getHeight()).orElse(0.0)).append("cm, 현재 체중 ")
                .append(Optional.ofNullable(health.getWeight()).orElse(0.0)).append("kg, 목표 체중 ")
                .append(Optional.ofNullable(health.getGoalWeight()).orElse(0.0)).append("kg, 활동 수준 ")
                .append(Optional.ofNullable(health.getActivityLevel()).orElse("미입력")).append("\n"));

        sb.append("[주간 운동 데이터]\n");
        weeklyStats.getExerciseStats().stream()
                .filter(stat -> !stat.getDate().isAfter(evaluationDate))
                .sorted(Comparator.comparing(DailyExerciseStat::getDate))
                .forEach(stat -> sb.append(stat.getDate()).append("(").append(stat.getDayOfWeekKr()).append(") ")
                        .append("총 운동시간 ").append(stat.getTotalDurationMinutes()).append("분, 소모 칼로리 ")
                        .append(stat.getTotalCalories()).append("kcal\n"));

        sb.append("오늘 이후 남은 요일이 있다면 그날에 적합한 운동을 1-2문장으로 제안하세요.");
        return sb.toString();
    }

    private AiExerciseReview parseAnswer(String email, LocalDate referenceDate, LocalDate evaluationDate, String answer) {
        AiExerciseReview.AiExerciseReviewBuilder builder = AiExerciseReview.builder()
                .email(email)
                .weekStartDate(toWeekStart(referenceDate))
                .evaluationDate(evaluationDate)
                .summary(answer)
                .recommendation(null)
                .createdAt(LocalDateTime.now(KST));

        try {
            JsonNode root = objectMapper.readTree(answer);
            builder.summary(root.path("summary").asText(answer));
            builder.recommendation(root.path("recommendation").asText(null));
        } catch (Exception ignored) {
        }

        AiExerciseReview review = builder.build();
        if (review.getSummary() == null || review.getSummary().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "운동 평가 결과 파싱에 실패했습니다.");
        }
        return review;
    }

    private ExerciseReviewResponse toResponse(AiExerciseReview review) {
        return ExerciseReviewResponse.builder()
                .weekStartDate(review.getWeekStartDate())
                .evaluationDate(review.getEvaluationDate())
                .summary(review.getSummary())
                .recommendation(review.getRecommendation())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private LocalDate toWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
