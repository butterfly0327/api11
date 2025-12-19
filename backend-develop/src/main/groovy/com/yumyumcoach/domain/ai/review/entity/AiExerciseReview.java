package com.yumyumcoach.domain.ai.review.entity;

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
public class AiExerciseReview {
    private Long id;
    private String email;
    private LocalDate weekStartDate;
    private LocalDate evaluationDate;
    private String summary;
    private String recommendation;
    private LocalDateTime createdAt;
}
