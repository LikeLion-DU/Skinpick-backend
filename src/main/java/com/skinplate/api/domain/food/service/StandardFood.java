package com.skinplate.api.domain.food.service;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 표준 DB 에서 찾은 음식 한 건.
 *
 * 영양값만 있는 게 아니라 조리법·매운맛·재료 태그까지 들고 있다. 룰 엔진의 절반이
 * 그 셋을 보기 때문이다 — 영양값만 덮어써도 R02(매운맛)·R07(튀김)·R09(발효)는
 * 여전히 AI 추정에 흔들린다.
 *
 * @param measured 실측('분석'·'수집') 기반인지. false 면 레시피 계산값이라
 *                 나트륨이 낮게 잡히는 경향이 있다 — 로그로만 쓴다
 */
public record StandardFood(
        String name,
        Integer caloriesKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbG,
        Integer sodiumMg,
        BigDecimal sugarG,
        CookingMethod cookingMethod,
        boolean spicy,
        List<IngredientTag> tags,
        boolean measured,
        int sampleCount
) {

    /**
     * 원본에 없는 항목은 null 로 남아 있다. 0 으로 채우면 "지방 0g" 이라는
     * 거짓이 되므로, 비어 있으면 AI 추정치를 그대로 쓴다.
     */
    public Nutrition toNutrition(Nutrition fallback) {
        return Nutrition.of(
                caloriesKcal != null ? caloriesKcal : fallback.getCaloriesKcal(),
                proteinG != null ? proteinG : fallback.getProteinG(),
                fatG != null ? fatG : fallback.getFatG(),
                carbG != null ? carbG : fallback.getCarbG(),
                sodiumMg != null ? sodiumMg : fallback.getSodiumMg(),
                sugarG != null ? sugarG : fallback.getSugarG());
    }
}
