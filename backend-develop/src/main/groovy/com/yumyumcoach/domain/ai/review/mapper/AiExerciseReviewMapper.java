package com.yumyumcoach.domain.ai.review.mapper;

import com.yumyumcoach.domain.ai.review.entity.AiExerciseReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface AiExerciseReviewMapper {
    AiExerciseReview findByEmailAndEvaluationDate(@Param("email") String email,
                                                  @Param("evaluationDate") LocalDate evaluationDate);

    int insertReview(@Param("review") AiExerciseReview review);
}
