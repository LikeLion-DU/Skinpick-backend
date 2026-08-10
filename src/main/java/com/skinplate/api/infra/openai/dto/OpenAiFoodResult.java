package com.skinplate.api.infra.openai.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpenAiFoodResult(
        boolean foodDetected,
        String foodName,
        String foodCategory,
        String cookingMethod,          // CookingMethod enum 이름과 일치
        boolean spicy,
        List<Ingredient> ingredients,
        Nutrition nutrition
) {
    public record Ingredient(String name, String tag) {}   // tag는 IngredientTag 이름

    public record Nutrition(
            int caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbG,
            int sodiumMg,
            BigDecimal sugarG
    ) {}
}
