package com.yumyumcoach.domain.ai.review.controller;

import com.yumyumcoach.domain.ai.review.dto.NutritionReviewResponse;
import com.yumyumcoach.domain.ai.review.service.AiNutritionReviewService;
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
@RequestMapping("/api/me/ai-nutrition-evaluations")
public class AiNutritionReviewController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final AiNutritionReviewService aiNutritionReviewService;

    @PostMapping("/run")
    public NutritionReviewResponse runEvaluation(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate target = date != null ? date : LocalDate.now(KST);
        return aiNutritionReviewService.evaluate(email, target);
    }

    @GetMapping("/summary")
    public NutritionReviewResponse getEvaluation(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate target = date != null ? date : LocalDate.now(KST);
        return aiNutritionReviewService.get(email, target);
    }
}
