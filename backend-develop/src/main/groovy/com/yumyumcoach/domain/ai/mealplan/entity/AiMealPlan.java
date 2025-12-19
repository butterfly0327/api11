package com.yumyumcoach.domain.ai.mealplan.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMealPlan {
    private Long id;
    private String email;
    private LocalDate planDate;
    private String promptContext;
    private String rawResponse;
    private LocalDateTime createdAt;
}
