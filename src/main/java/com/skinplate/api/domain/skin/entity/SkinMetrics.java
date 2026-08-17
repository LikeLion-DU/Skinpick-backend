package com.skinplate.api.domain.skin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피부 5개 지표 (0~100).
 *
 *   높을수록 좋음 : hydration, barrier
 *   높을수록 나쁨 : oil, redness, trouble
 *
 * 판정 기준값을 이 클래스 안에 모아두면 Rule 구현체들이
 * metrics.isDry() 처럼 의미로 질문하게 되고, 기준 변경이 한 곳에서 끝난다.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinMetrics {

    public static final int DRY_THRESHOLD          = 40;   // 미만이면 건조
    public static final int OILY_THRESHOLD         = 70;   // 초과면 얼굴 전반이 번들거림
    public static final int REDNESS_THRESHOLD      = 60;   // 초과면 홍조
    public static final int TROUBLE_THRESHOLD      = 60;   // 초과면 트러블
    public static final int BARRIER_WEAK_THRESHOLD = 40;   // 미만이면 장벽 약화

    /**
     * 이상이면 <b>유분이 부분적으로</b> 올라와 있다 — T존만 번들거리는 상태가 여기다.
     *
     * {@link #OILY_THRESHOLD}(70 초과)와 뜻이 다르다. 그쪽은 "얼굴 전반이 번들거린다"이고
     * 룰 엔진(R07 튀김)과 추천이 쓴다. 부위별 유분을 그 임계로 재면 T존 복합성이
     * 전부 "유분 정상"으로 떨어진다 — 타입 판정이 이 둘을 갈라 써야 하는 이유다.
     *
     * 60 은 새로 만든 값이 아니다. {@code SkinHighlightBuilder} 가 이미 유분 60 초과에서
     * "유분 많음" 뱃지를 단다 — 화면에 이미 그어져 있던 선을 타입도 같이 쓰는 것이다.
     *
     * 정확히 60 한 점에서만 한 칸 어긋난다(여기는 이상, 뱃지는 초과). 유분 60이면 타입은
     * 복합성이고 뱃지는 "약간 번들거림"이다 — 뱃지 3단과 타입 경계가 완전히 겹칠 수 없는
     * 것이라 {@code SkinLevel} 이 뱃지와 40·60 에서 어긋나는 것과 같은 종류다.
     * 어느 한쪽이 틀린 게 아니므로 "버그"로 보고 한쪽만 옮기면 화면이 깨진다.
     */
    public static final int OIL_ELEVATED_THRESHOLD = 60;

    @Column(nullable = false) private int hydration;
    @Column(nullable = false) private int oil;
    @Column(nullable = false) private int redness;
    @Column(nullable = false) private int trouble;
    @Column(nullable = false) private int barrier;

    public static SkinMetrics of(int hydration, int oil, int redness, int trouble, int barrier) {
        return new SkinMetrics(
                clamp(hydration), clamp(oil), clamp(redness), clamp(trouble), clamp(barrier));
    }

    // ---- 상태 판정 ----

    public boolean isDry()         { return hydration < DRY_THRESHOLD; }
    public boolean isOily()        { return oil > OILY_THRESHOLD; }
    public boolean isOilElevated() { return oil >= OIL_ELEVATED_THRESHOLD; }
    public boolean hasRedness()    { return redness > REDNESS_THRESHOLD; }
    public boolean hasTrouble()    { return trouble > TROUBLE_THRESHOLD; }
    public boolean isBarrierWeak() { return barrier < BARRIER_WEAK_THRESHOLD; }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
