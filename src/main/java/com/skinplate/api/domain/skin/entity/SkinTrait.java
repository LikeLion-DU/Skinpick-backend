package com.skinplate.api.domain.skin.entity;

/**
 * AI 가 관찰한 피부 타입의 보조 경향. primary 하나로 표현되지 않는 상태를 담는다.
 *
 * "수부지"를 primary 로 만들지 않는 이유 — 복합성이면서 수분이 부족한 상태이지
 * 다섯 번째 타입이 아니다. {@code primary=COMBINATION + DEHYDRATED} 로 두면 앱이
 * "복합성 · 수분 부족 경향(수부지)" 으로 합쳐 보여줄 수 있고, primary 목록이
 * 늘어나지 않는다.
 *
 * 이 값은 표시 전용이다. Skin Score · Plate Score · Recommendation 어디에도 들어가지 않는다.
 */
public enum SkinTrait {

    DEHYDRATED         ("수분 부족 경향"),
    OILY_T_ZONE        ("T존 유분 경향"),
    SENSITIVE_TENDENCY ("민감 경향"),
    TROUBLE_TENDENCY   ("트러블 경향");

    private final String label;

    SkinTrait(String label) { this.label = label; }

    public String getLabel() { return label; }
}
