package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;

/**
 * 룰 평가에 필요한 모든 입력. 룰은 이것 외에 아무것도 보지 않는다.
 * DB도, 외부 API도 건드리지 않으므로 순수 함수처럼 테스트할 수 있다.
 */
public record PlateContext(SkinMetrics skin, FoodAnalysis food) {

    public static PlateContext of(SkinAnalysis skinAnalysis, FoodAnalysis food) {
        return new PlateContext(skinAnalysis.getMetrics(), food);
    }

    public Nutrition nutrition() {
        return food.getNutrition();
    }
}
