package com.yumyumcoach.domain.ai.review.controller;

import com.yumyumcoach.domain.ai.review.dto.ExerciseReviewResponse;
import com.yumyumcoach.domain.ai.review.service.AiExerciseReviewService;
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
@RequestMapping("/api/me/ai-workout-evaluations")
public class AiExerciseReviewController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final AiExerciseReviewService aiExerciseReviewService;

    @PostMapping("/run")
    public ExerciseReviewResponse runEvaluation(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate target = date != null ? date : LocalDate.now(KST);
        return aiExerciseReviewService.evaluate(email, target);
    }

    @GetMapping("/summary")
    public ExerciseReviewResponse getEvaluation(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate target = date != null ? date : LocalDate.now(KST);
        return aiExerciseReviewService.get(email, target);
    }
}
