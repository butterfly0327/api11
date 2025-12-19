package com.yumyumcoach.domain.ai.mealplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumyumcoach.domain.ai.mealplan.dto.AiMealPlanResponse;
import com.yumyumcoach.domain.ai.mealplan.dto.MealPlanItemDto;
import com.yumyumcoach.domain.ai.mealplan.entity.AiMealPlan;
import com.yumyumcoach.domain.ai.mealplan.entity.AiMealPlanItem;
import com.yumyumcoach.domain.ai.mealplan.mapper.AiMealPlanMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMealPlanService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiMealPlanMapper aiMealPlanMapper;
    private final GeminiClient geminiClient;
    private final WeeklyStatsService weeklyStatsService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public AiMealPlanResponse getExistingPlan(String email, LocalDate planDate) {
        AiMealPlan plan = aiMealPlanMapper.findByEmailAndDate(email, planDate);
        if (plan == null) {
            return AiMealPlanResponse.builder()
                    .planDate(planDate)
                    .generated(false)
                    .meals(Collections.emptyList())
                    .rawText(null)
                    .build();
        }
        List<AiMealPlanItem> items = aiMealPlanMapper.findItemsByPlanId(plan.getId());
        return toResponse(plan, items, true);
    }

    @Transactional
    public AiMealPlanResponse generatePlan(String email, LocalDate planDate) {
        AiMealPlan existing = aiMealPlanMapper.findByEmailAndDate(email, planDate);
        if (existing != null) {
            List<AiMealPlanItem> items = aiMealPlanMapper.findItemsByPlanId(existing.getId());
            return toResponse(existing, items, true);
        }

        MyPageResponse profile = userService.getMyPage(email);
        WeeklyStatsResponse weeklyStats = weeklyStatsService.getWeeklyStats(email, planDate);

        List<DailyDietStat> untilToday = weeklyStats.getDietStats().stream()
                .filter(stat -> !stat.getDate().isAfter(planDate))
                .sorted(Comparator.comparing(DailyDietStat::getDate))
                .toList();

        String prompt = buildPrompt(planDate, profile, untilToday);
        String rawText = geminiClient.generate(prompt);

        List<AiMealPlanItem> items = parseMealItems(rawText);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini 식단 계획 파싱에 실패했습니다.");
        }

        AiMealPlan plan = AiMealPlan.builder()
                .email(email)
                .planDate(planDate)
                .promptContext(prompt)
                .rawResponse(rawText)
                .createdAt(LocalDateTime.now(KST))
                .build();
        aiMealPlanMapper.insertPlan(plan);
        aiMealPlanMapper.insertItems(plan.getId(), items);

        return toResponse(plan, items, true);
    }

    private String buildPrompt(LocalDate planDate, MyPageResponse profile, List<DailyDietStat> dietStats) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 한국인 건강 코치입니다. 오늘 날짜는 ").append(planDate)
                .append("이며 요일은 ").append(planDate.getDayOfWeek()).append("입니다. ");
        sb.append("아래 사용자 건강 정보를 참고해 한국 식단을 아침/점심/저녁 3끼 추천하고, 각 끼니별 예상 칼로리와 한 줄 평을 제공합니다. ");
        sb.append("JSON만 반환하세요. key는 mealTime, menu, calories, highlight 입니다. mealTime은 BREAKFAST, LUNCH, DINNER 중 하나여야 합니다.\n");

        if (profile != null && profile.getHealth() != null) {
            sb.append("[건강 정보] 키: ").append(Optional.ofNullable(profile.getHealth().getHeight()).orElse(0.0))
                    .append("cm, 현재 체중: ").append(Optional.ofNullable(profile.getHealth().getWeight()).orElse(0.0))
                    .append("kg, 목표 체중: ").append(Optional.ofNullable(profile.getHealth().getGoalWeight()).orElse(0.0))
                    .append("kg, 활동 수준: ").append(Optional.ofNullable(profile.getHealth().getActivityLevel()).orElse("미입력"))
                    .append(". 질환: 당뇨=").append(profile.getHealth().getHasDiabetes())
                    .append(" 고혈압=").append(profile.getHealth().getHasHypertension())
                    .append(" 고지혈증=").append(profile.getHealth().getHasHyperlipidemia())
                    .append(" 기타=").append(Optional.ofNullable(profile.getHealth().getOtherDisease()).orElse("없음"))
                    .append(". 목표: ").append(Optional.ofNullable(profile.getHealth().getGoal()).orElse("미입력"))
                    .append(".\n");
        }

        sb.append("[이번 주 섭취 추이]\n");
        for (DailyDietStat stat : dietStats) {
            sb.append(stat.getDate()).append("(").append(stat.getDayOfWeekKr()).append(") : ")
                    .append("탄수화물 ").append(stat.getTotalCarbohydrate()).append("g, 단백질 ")
                    .append(stat.getTotalProtein()).append("g, 지방 ")
                    .append(stat.getTotalFat()).append("g, 칼로리 ")
                    .append(stat.getTotalCalories()).append("kcal\n");
        }

        sb.append("출력 예시: [{\"mealTime\":\"BREAKFAST\",\"menu\":\"현미밥+닭가슴살\",\"calories\":420,\"highlight\":\"고단백 저지방\"}, ...]");
        return sb.toString();
    }

    private List<AiMealPlanItem> parseMealItems(String rawText) {
        try {
            JsonNode root = objectMapper.readTree(rawText);
            if (root.isArray()) {
                return readArray(root);
            }
            if (root.has("meals") && root.get("meals").isArray()) {
                return readArray(root.get("meals"));
            }
        } catch (Exception ignored) {
        }

        // fallback: split by lines
        List<AiMealPlanItem> fallback = new ArrayList<>();
        String[] lines = rawText.split("\\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            fallback.add(AiMealPlanItem.builder()
                    .mealTime(null)
                    .menuDescription(line.trim())
                    .calories(null)
                    .highlight(null)
                    .createdAt(LocalDateTime.now(KST))
                    .build());
        }
        return fallback;
    }

    private List<AiMealPlanItem> readArray(JsonNode arrayNode) {
        List<AiMealPlanItem> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String mealTime = Optional.ofNullable(node.path("mealTime").asText(null)).orElse(null);
            if (mealTime == null) {
                mealTime = Optional.ofNullable(node.path("meal_time").asText(null)).orElse(null);
            }
            String menu = Optional.ofNullable(node.path("menu").asText(null))
                    .orElse(Optional.ofNullable(node.path("menuDescription").asText(null)).orElse(null));
            Double calories = node.hasNonNull("calories") ? node.get("calories").asDouble() : null;
            String highlight = Optional.ofNullable(node.path("highlight").asText(null)).orElse(null);

            String safeMenu = (menu == null || menu.isBlank())
                    ? "추천 식단 세부 내용을 확인할 수 없습니다."
                    : menu;
            String safeMealTime = (mealTime == null || mealTime.isBlank()) ? "MEAL" : mealTime;

            result.add(AiMealPlanItem.builder()
                    .mealTime(safeMealTime)
                    .menuDescription(safeMenu)
                    .calories(calories)
                    .highlight(highlight)
                    .createdAt(LocalDateTime.now(KST))
                    .build());
        }
        return result;
    }

    private AiMealPlanResponse toResponse(AiMealPlan plan, List<AiMealPlanItem> items, boolean generated) {
        List<MealPlanItemDto> mealDtos = items.stream()
                .map(item -> MealPlanItemDto.builder()
                        .mealTime(item.getMealTime())
                        .menuDescription(item.getMenuDescription())
                        .calories(item.getCalories())
                        .highlight(item.getHighlight())
                        .build())
                .collect(Collectors.toList());

        return AiMealPlanResponse.builder()
                .planDate(plan.getPlanDate())
                .generated(generated)
                .generatedAt(plan.getCreatedAt())
                .meals(mealDtos)
                .rawText(plan.getRawResponse())
                .build();
    }
}
