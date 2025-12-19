package com.yumyumcoach.domain.ai.mealplan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMealPlanResponse {
    private LocalDate planDate;
    private boolean generated;
    private LocalDateTime generatedAt;
    private List<MealPlanItemDto> meals;
    private String rawText;
}
