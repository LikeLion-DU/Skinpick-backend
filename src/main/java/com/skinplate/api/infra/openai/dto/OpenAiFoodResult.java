package com.skinplate.api.infra.openai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 신규 특성 5종(foodGroup~processingLevel)은 String 이고 null 일 수 있다 —
 * 배포 경계 30분 창의 구 analysisToken 에는 이 키가 없어서, non-null 로 두면
 * 저장이 500 이 된다. enum 변환과 UNKNOWN fallback 은 FoodAnalysisService 가 맡는다
 * (cookingMethod·tag 와 같은 관례).
 */
public record OpenAiFoodResult(
        boolean foodDetected,
        String foodName,
        String foodCategory,
        String cookingMethod,          // CookingMethod enum 이름과 일치
        boolean spicy,
        List<Ingredient> ingredients,
        Nutrition nutrition,
        String foodGroup,              // FoodGroup enum 이름
        String portionSize,            // PortionSize enum 이름
        String spiciness,              // Spiciness enum 이름
        String oiliness,               // Oiliness enum 이름
        String processingLevel         // ProcessingLevel enum 이름
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
