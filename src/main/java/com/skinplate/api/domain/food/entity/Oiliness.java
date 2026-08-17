package com.skinplate.api.domain.food.entity;

/**
 * 기름진 정도. cookingMethod=FRIED 가 못 보는 사각지대를 메운다 —
 * 삼겹살 구이는 GRILLED 라 R07(유분×튀김)이 한 번도 안 걸렸다.
 *
 * HIGH 면 튀김이 아니어도 R07 이 약한 계수로 발동한다. UNKNOWN 은 기존 동작과 동일하다.
 */
public enum Oiliness {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}
