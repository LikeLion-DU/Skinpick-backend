package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.FoodAnalysis;

import java.util.List;

public record FoodAnalysisDto(
        Long foodAnalysisId,
        String foodName,
        String foodCategory,
        String cookingMethod,
        boolean spicy,
        List<IngredientDto> ingredients,
        NutritionDto nutrition
) {
    public static FoodAnalysisDto from(FoodAnalysis entity) {
        return new FoodAnalysisDto(
                entity.getId(),
                entity.getFoodName(),
                entity.getFoodCategory(),
                entity.getCookingMethod().name(),
                entity.isSpicy(),
                entity.getIngredients().stream().map(IngredientDto::from).toList(),
                NutritionDto.from(entity.getNutrition()));
    }
}
