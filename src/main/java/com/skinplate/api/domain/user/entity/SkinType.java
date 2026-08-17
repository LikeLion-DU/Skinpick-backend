package com.skinplate.api.domain.user.entity;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

/**
 * 피부 타입. 두 가지 용도로 쓰인다.
 *
 *   declared : 사용자가 스스로 고른 값 (S01c)
 *   observed : 수분·유분에서 규칙으로 도출한 값 — AI 에게 묻지 않는다
 *
 * 어느 쪽도 Skin Plate Score 계산에는 들어가지 않는다.
 * 둘의 차이를 보여주는 것이 이 타입의 존재 이유다.
 *
 * SENSITIVE 는 declared 전용이다. 붉은기는 하루 사이에도 오르내리는 상태라
 * {@code observe} 가 내지 않는다 — {@link com.skinplate.api.domain.skin.entity.SkinTrait}
 * 의 REDNESS_PRONE 이 그 자리를 맡는다.
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
     * 오늘의 관찰 타입을 도출한다. (PRD §4.4.1)
     * 같은 지표면 항상 같은 타입이 나와야 갭 코멘트도 재현 가능하다.
     *
     * <b>근거는 유분과 수분 둘뿐이다.</b> 붉은기·트러블·장벽은 타입을 바꾸지 않는다 —
     * 그것들은 오늘의 상태이지 피부의 성질이 아니고, 하루 컨디션 때문에 "당신은 지성"이
     * "당신은 민감성"으로 바뀌면 갭 카드가 말하려는 것이 사라진다. 그 셋은
     * {@link com.skinplate.api.domain.skin.entity.SkinTrait} 이 상태로 따로 낸다.
     *
     * 유분을 두 단계로 읽는 것이 핵심이다. 전역 유분 점수 하나로도 부위별 유분과
     * 전면 유분은 갈린다 — T존만 번들거리면 얼굴 전체 평균이 중간대에 머물고,
     * 전반이 번들거려야 높은 값이 된다. 예전 규칙은 복합성에 {@code 수분<40 && 유분>70}
     * 을 요구해서 전형적인 T존 복합성(수분 50 · 유분 65)이 NORMAL 로 떨어졌다.
     *
     * SENSITIVE 는 여기서 나오지 않는다. 자가 신고 선택지로만 남는다.
     */
    public static SkinType observe(SkinMetrics metrics) {
        if (metrics.isOily())        return OILY;          // 유분 > 70 · 얼굴 전반이 번들거림
        if (metrics.isOilElevated()) return COMBINATION;   // 유분 ≥ 60 · 부위별로 유분이 다름
        if (metrics.isDry())         return DRY;           // 수분 < 40
        return NORMAL;
    }
}
