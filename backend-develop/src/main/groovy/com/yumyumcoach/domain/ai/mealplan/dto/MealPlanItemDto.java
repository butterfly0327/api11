package com.yumyumcoach.domain.ai.mealplan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealPlanItemDto {
    private String mealTime;
    private String menuDescription;
    private Double calories;
    private String highlight;
}
