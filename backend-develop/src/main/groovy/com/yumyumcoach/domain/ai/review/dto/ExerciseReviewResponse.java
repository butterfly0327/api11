package com.yumyumcoach.domain.ai.review.dto;

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
public class ExerciseReviewResponse {
    private LocalDate weekStartDate;
    private LocalDate evaluationDate;
    private String summary;
    private String recommendation;
    private LocalDateTime createdAt;
}
