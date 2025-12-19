package com.yumyumcoach.domain.ai.mealplan.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMealPlanItem {
    private Long id;
    private Long mealPlanId;
    private String mealTime;
    private String menuDescription;
    private Double calories;
    private String highlight;
    private LocalDateTime createdAt;
}
