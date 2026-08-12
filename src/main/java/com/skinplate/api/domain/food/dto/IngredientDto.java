package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.FoodIngredient;

public record IngredientDto(String name, String tag) {

    public static IngredientDto from(FoodIngredient ingredient) {
        return new IngredientDto(ingredient.getName(), ingredient.getTag().name());
    }
}
