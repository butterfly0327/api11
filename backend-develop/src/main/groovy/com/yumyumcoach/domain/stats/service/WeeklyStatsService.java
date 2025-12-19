package com.yumyumcoach.domain.stats.service;

import com.yumyumcoach.domain.diet.dto.DietFoodDto;
import com.yumyumcoach.domain.diet.dto.DietRecordDto;
import com.yumyumcoach.domain.diet.service.DietRecordService;
import com.yumyumcoach.domain.exercise.dto.ExerciseRecordResponse;
import com.yumyumcoach.domain.exercise.service.ExerciseService;
import com.yumyumcoach.domain.stats.dto.DailyDietStat;
import com.yumyumcoach.domain.stats.dto.DailyExerciseStat;
import com.yumyumcoach.domain.stats.dto.WeeklyStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyStatsService {
    private static final int MAX_RECORDS_PER_DAY = 1000;

    private final DietRecordService dietRecordService;
    private final ExerciseService exerciseService;

    public WeeklyStatsResponse getWeeklyStats(String email, LocalDate baseDate) {
        LocalDate startDate = toWeekStart(baseDate);
        LocalDate endDate = startDate.plusDays(6);

        List<DailyDietStat> dietStats = new ArrayList<>();
        List<DailyExerciseStat> exerciseStats = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate current = startDate.plusDays(i);
            dietStats.add(calculateDietStat(email, current));
            exerciseStats.add(calculateExerciseStat(email, current));
        }

        return WeeklyStatsResponse.builder()
                .weekStartDate(startDate)
                .weekEndDate(endDate)
                .dietStats(Collections.unmodifiableList(dietStats))
                .exerciseStats(Collections.unmodifiableList(exerciseStats))
                .build();
    }

    private DailyDietStat calculateDietStat(String email, LocalDate date) {
        List<DietRecordDto> diets = dietRecordService.getMyDiets(email, date, 0, MAX_RECORDS_PER_DAY);
        double carbs = 0;
        double protein = 0;
        double fat = 0;
        double calories = 0;

        for (DietRecordDto diet : diets) {
            List<DietFoodDto> items = Optional.ofNullable(diet.getItems()).orElseGet(Collections::emptyList);
            for (DietFoodDto item : items) {
                carbs += Optional.ofNullable(item.getCarbs()).orElse(0.0);
                protein += Optional.ofNullable(item.getProtein()).orElse(0.0);
                fat += Optional.ofNullable(item.getFat()).orElse(0.0);
                calories += Optional.ofNullable(item.getCalories()).orElse(0.0);
            }
        }

        return DailyDietStat.builder()
                .date(date)
                .dayOfWeekKr(toKoreanDay(date))
                .totalCarbohydrate(roundOneDecimal(carbs))
                .totalProtein(roundOneDecimal(protein))
                .totalFat(roundOneDecimal(fat))
                .totalCalories(roundOneDecimal(calories))
                .build();
    }

    private DailyExerciseStat calculateExerciseStat(String email, LocalDate date) {
        List<ExerciseRecordResponse> exercises = exerciseService.getMyExerciseRecords(email, date);
        double duration = 0;
        double calories = 0;

        for (ExerciseRecordResponse exercise : exercises) {
            duration += Optional.ofNullable(exercise.getDurationMinutes()).orElse(0.0);
            calories += Optional.ofNullable(exercise.getCalories()).orElse(0.0);
        }

        return DailyExerciseStat.builder()
                .date(date)
                .dayOfWeekKr(toKoreanDay(date))
                .totalDurationMinutes(roundOneDecimal(duration))
                .totalCalories(roundOneDecimal(calories))
                .build();
    }

    private LocalDate toWeekStart(LocalDate baseDate) {
        return baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String toKoreanDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
