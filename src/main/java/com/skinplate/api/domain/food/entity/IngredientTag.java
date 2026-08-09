package com.skinplate.api.domain.food.entity;

/**
 * 재료 태그. Rule Engine이 음식을 이해하는 유일한 통로다.
 *
 * AI가 태그를 자유롭게 만들면 룰이 아무것도 매칭하지 못한다.
 * 그래서 OpenAI JSON Schema에서도 이 목록을 enum으로 강제한다.
 * 값을 추가할 때는 스키마(food-analysis-schema.json)도 함께 고쳐야 한다.
 */
public enum IngredientTag {
    VITAMIN_C,      // 키위, 브로콜리, 파프리카
    VITAMIN_A,      // 당근, 시금치
    OMEGA3,         // 연어, 고등어, 견과류
    ANTIOXIDANT,    // 베리류, 녹차, 토마토
    PROBIOTIC,      // 김치, 된장, 요거트
    DAIRY,          // 우유, 치즈
    GLUTEN,         // 밀가루
    CAPSAICIN,      // 고춧가루, 청양고추
    CAFFEINE,       // 커피, 홍차
    ALCOHOL,
    HIGH_GI,        // 흰쌀, 설탕
    ETC
}
