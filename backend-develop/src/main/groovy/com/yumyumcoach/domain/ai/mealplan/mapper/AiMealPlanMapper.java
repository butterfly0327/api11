package com.yumyumcoach.domain.ai.mealplan.mapper;

import com.yumyumcoach.domain.ai.mealplan.entity.AiMealPlan;
import com.yumyumcoach.domain.ai.mealplan.entity.AiMealPlanItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AiMealPlanMapper {
    AiMealPlan findByEmailAndDate(@Param("email") String email, @Param("planDate") LocalDate planDate);

    List<AiMealPlanItem> findItemsByPlanId(@Param("planId") Long planId);

    int insertPlan(@Param("plan") AiMealPlan plan);

    int insertItems(@Param("planId") Long planId, @Param("items") List<AiMealPlanItem> items);
}
