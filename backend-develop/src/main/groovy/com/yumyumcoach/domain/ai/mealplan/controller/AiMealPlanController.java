package com.yumyumcoach.domain.ai.mealplan.controller;

import com.yumyumcoach.domain.ai.mealplan.dto.AiMealPlanResponse;
import com.yumyumcoach.domain.ai.mealplan.service.AiMealPlanService;
import com.yumyumcoach.global.common.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/ai-meal-plans")
public class AiMealPlanController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final AiMealPlanService aiMealPlanService;

    @PostMapping("/generate")
    public AiMealPlanResponse generateDailyPlan(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate targetDate = date != null ? date : LocalDate.now(KST);
        return aiMealPlanService.generatePlan(email, targetDate);
    }

    @GetMapping("/daily")
    public AiMealPlanResponse getDailyPlan(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate targetDate = date != null ? date : LocalDate.now(KST);
        return aiMealPlanService.getExistingPlan(email, targetDate);
    }
}
