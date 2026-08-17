package com.skinplate.api.domain.food.entity;

/**
 * 매운맛 강도. 기존 spicy(boolean)를 대체하지 않고 <b>보탠다</b> —
 * R02 의 발동 여부는 여전히 spicy/CAPSAICIN 이 정하고(표준 테이블이 이긴다),
 * 이 값은 발동한 감점의 <b>강도 계수</b>로만 쓰인다.
 *
 * UNKNOWN(그리고 CAPSAICIN 으로 발동한 NONE)은 계수 1.0 — 기존 동작과 동일하다.
 * 이 값이 AI 유래라 표준 테이블 밖이라는 재현성 한계는 계수 폭(±30%)으로 상한한다.
 */
public enum Spiciness {
    NONE,
    MILD,
    MEDIUM,
    HOT,
    UNKNOWN
}
