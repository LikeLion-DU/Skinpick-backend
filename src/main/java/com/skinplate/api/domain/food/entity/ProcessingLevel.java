package com.skinplate.api.domain.food.entity;

/**
 * 가공도 (NOVA 분류를 4단계로 단순화).
 *
 * <p><b>아직 점수에 쓰지 않는다.</b> "초가공식품 = 감점"을 확정할 근거가 이 앱의
 * 룰표에 없고, 근거 없는 감점은 "왜 이 점수인가"를 설명 못 하게 만든다.
 * 지금은 저장·표시 전용이며, 음식×피부 패턴 분석의 집계 축으로 쌓는다.
 * 룰을 붙이는 날 이 주석을 지우고 RuleConstants 에 델타를 더한다.
 */
public enum ProcessingLevel {
    WHOLE,                 // 자연식품 그대로
    MINIMALLY_PROCESSED,   // 최소 가공 (조리한 일반 식사)
    PROCESSED,             // 가공식품
    ULTRA_PROCESSED,       // 초가공식품 (라면·과자·탄산음료)
    UNKNOWN
}
