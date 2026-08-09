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
    public static final int OILY_THRESHOLD         = 70;   // 초과면 유분 과다
    public static final int REDNESS_THRESHOLD      = 60;   // 초과면 홍조
    public static final int TROUBLE_THRESHOLD      = 60;   // 초과면 트러블
    public static final int BARRIER_WEAK_THRESHOLD = 40;   // 미만이면 장벽 약화

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
    public boolean hasRedness()    { return redness > REDNESS_THRESHOLD; }
    public boolean hasTrouble()    { return trouble > TROUBLE_THRESHOLD; }
    public boolean isBarrierWeak() { return barrier < BARRIER_WEAK_THRESHOLD; }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
