package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodTraits;

import java.util.List;

/**
 * 특성 5종(foodGroup~processingLevel)은 V7 이전 행에서도 UNKNOWN/ETC 로 채워 내린다 —
 * 키가 있다 없다 하면 앱 파서가 행마다 다른 모양을 다뤄야 한다. "모른다"는 값이지
 * 누락이 아니다. portionSize 는 표시·리포트 환산 전용이고 점수와 무관하다.
 */
public record FoodAnalysisDto(
        Long foodAnalysisId,
        String foodName,
        String foodCategory,
        String cookingMethod,
        boolean spicy,
        String foodGroup,
        String portionSize,
        String spiciness,
        String oiliness,
        String processingLevel,
        List<IngredientDto> ingredients,
        NutritionDto nutrition
) {
    public static FoodAnalysisDto from(FoodAnalysis entity) {
        FoodTraits traits = entity.getTraits();

        return new FoodAnalysisDto(
                entity.getId(),
                entity.getFoodName(),
                entity.getFoodCategory(),
                entity.getCookingMethod().name(),
                entity.isSpicy(),
                traits.getFoodGroup().name(),
                traits.getPortionSize().name(),
                traits.getSpiciness().name(),
                traits.getOiliness().name(),
                traits.getProcessingLevel().name(),
                entity.getIngredients().stream().map(IngredientDto::from).toList(),
                NutritionDto.from(entity.getNutrition()));
    }
}
