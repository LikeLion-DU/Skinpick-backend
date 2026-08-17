package com.skinplate.api.domain.food.entity;

/**
 * 음식 분류. 자유 문자열인 foodCategory("한식/찌개")는 표시용으로 그대로 두고,
 * 집계·패턴 분석이 쓸 축은 이 enum 으로 고정한다 — 자유 문자열은 같은 찌개가
 * "한식/찌개"와 "찌개류"로 갈려 세어지지 않는다.
 *
 * OpenAI Structured Outputs 의 enum 과 값이 정확히 일치해야 한다(FoodAnalysisPrompt).
 * 점수 계산에는 쓰지 않는다 — 룰의 입력은 여전히 영양값·조리법·재료 태그·특성뿐이다.
 */
public enum FoodGroup {
    RICE,            // 밥류 (덮밥·볶음밥·비빔밥)
    NOODLE,          // 면류
    SOUP_STEW,       // 국·탕·찌개
    MEAT_DISH,       // 고기 요리
    SEAFOOD_DISH,    // 해산물 요리
    VEGETABLE_DISH,  // 채소·나물 요리
    FRIED_FOOD,      // 튀김류
    DESSERT,         // 디저트·빵과자
    BEVERAGE,        // 음료
    SNACK,           // 간식·분식
    SALAD,           // 샐러드
    ETC              // 애매하면 여기 — "모르겠다"의 자리
}
