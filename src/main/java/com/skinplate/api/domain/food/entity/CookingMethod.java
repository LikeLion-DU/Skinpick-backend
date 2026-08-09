package com.skinplate.api.domain.food.entity;

/** OpenAI Structured Outputs의 enum과 값이 정확히 일치해야 한다. */
public enum CookingMethod {
    FRIED,      // 튀김
    BOILED,     // 국물 요리 — "국물을 절반만 남기세요" 행동의 조건
    GRILLED,    // 구이
    RAW,        // 생식
    STEAMED,    // 찜
    ETC
}
