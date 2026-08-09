package com.skinplate.api.domain.user.entity;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

/**
 * 피부 타입. 두 가지 용도로 쓰인다.
 *
 *   declared : 사용자가 스스로 고른 값 (S01c)
 *   observed : 5개 지표에서 규칙으로 도출한 값 — AI 에게 묻지 않는다
 *
 * 어느 쪽도 Skin Plate Score 계산에는 들어가지 않는다.
 * 둘의 차이를 보여주는 것이 이 타입의 존재 이유다.
 */
public enum SkinType {

    DRY        ("건성"),
    OILY       ("지성"),
    COMBINATION("복합성"),
    SENSITIVE  ("민감성"),
    NORMAL     ("보통"),      // 관찰 전용. 선택지에는 노출하지 않는다
    UNKNOWN    ("잘 모르겠어요");

    private final String label;

    SkinType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** S01c 화면에 노출할 선택지 (NORMAL 제외) */
    public static SkinType[] selectable() {
        return new SkinType[]{ DRY, OILY, COMBINATION, SENSITIVE, UNKNOWN };
    }

    /**
     * 5개 지표에서 오늘의 관찰 타입을 도출한다. (PRD §4.4.1)
     * 같은 지표면 항상 같은 타입이 나와야 갭 코멘트도 재현 가능하다.
     */
    public static SkinType observe(SkinMetrics metrics) {
        if (metrics.getRedness() > 70)              return SENSITIVE;
        if (metrics.isDry() && metrics.isOily())    return COMBINATION;   // 수분 부족형 지성
        if (metrics.isOily())                       return OILY;
        if (metrics.isDry())                        return DRY;
        return NORMAL;
    }
}
