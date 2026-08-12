package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.Nutrition;

import java.math.BigDecimal;

public record NutritionDto(
        int caloriesKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbG,
        int sodiumMg,
        BigDecimal sugarG
) {
    public static NutritionDto from(Nutrition nutrition) {
        return new NutritionDto(
                nutrition.getCaloriesKcal(),
                nutrition.getProteinG(),
                nutrition.getFatG(),
                nutrition.getCarbG(),
                nutrition.getSodiumMg(),
                nutrition.getSugarG());
    }
}
