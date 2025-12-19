package com.yumyumcoach.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyExerciseStat {
    private LocalDate date;
    private String dayOfWeekKr;
    private double totalDurationMinutes;
    private double totalCalories;
}
