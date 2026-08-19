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
 * SENSITIVE · DEHYDRATED_OILY 는 declared 전용이다. 둘 다 하루 사이에도 오르내리는
 * <b>상태</b>라 {@code observe} 가 내지 않는다 — {@link com.skinplate.api.domain.skin.entity.SkinTrait}
 * 의 REDNESS_PRONE · DEHYDRATED 가 그 자리를 맡는다.
 *
 * <p><b>왜 상태를 선택지에는 두는가.</b> 사용자는 "저는 민감성이에요" · "저는 수부지예요"
 * 라고 말한다 — 자기 피부를 그 이름으로 알고 있다. 그 말을 받을 칸이 없으면 건너뛰기밖에
 * 남지 않고, 그러면 "아직 안 정함(NULL)"과 섞여 갭 카드가 영영 안 뜬다. 받는 것과
 * 관찰로 내는 것은 다른 일이다.
 */
public enum SkinType {

    DRY        ("건성"),
    OILY       ("지성"),
    COMBINATION("복합성"),
    SENSITIVE  ("민감성"),
    /**
     * 수분은 부족한데 유분은 올라와 있는 상태. 사용자가 부르는 이름이 '수부지' 다.
     *
     * <p>{@code observe} 는 이 값을 내지 않는다 — 관찰 쪽에서 그 상태를 맡는 것은
     * {@link com.skinplate.api.domain.skin.entity.SkinTrait#DEHYDRATED} 이고, 그쪽이
     * 이미 {@code SkinTypeDto} 라벨에 '(수부지)' 별칭까지 붙인다. 여기 있는 것은
     * <b>자가 신고 칸</b>이다.
     */
    DEHYDRATED_OILY("수부지"),
    NORMAL     ("보통"),      // 관찰 전용. 선택지에는 노출하지 않는다
    UNKNOWN    ("잘 모르겠어요");

    private final String label;

    SkinType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /**
     * S01c 화면에 노출할 선택지 (NORMAL 제외).
     *
     * <p>순서가 화면 순서다 — 시안이 건성·지성·복합성·민감성·수부지·모르겠어요 여섯 칸을
     * 2열로 그린다. UNKNOWN 이 마지막인 것은 "고르지 못했다"가 목록 중간에 오면
     * 그 아래 칸을 안 읽게 되기 때문이다.
     */
    public static SkinType[] selectable() {
        return new SkinType[]{ DRY, OILY, COMBINATION, SENSITIVE, DEHYDRATED_OILY, UNKNOWN };
    }

    /**
     * {@code observe} 가 낼 수 없는 값인가. 자가 신고 전용 상태 둘이 여기 해당한다.
     *
     * <p>갭 분석이 이 둘을 따로 다뤄야 한다 — 그냥 비교하면 무슨 사진을 찍어도
     * "일치하지 않음"이고, 실제로 그 상태가 관찰된 날조차 그 사실이 화면에 안 나온다.
     */
    public boolean isDeclaredOnly() {
        return this == SENSITIVE || this == DEHYDRATED_OILY;
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
