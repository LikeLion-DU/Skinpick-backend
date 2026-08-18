package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MILD 구간(0.6)을 더하면서 <b>기존 점수가 하나도 바뀌지 않는다</b>는 근거를 코드로 고정한다.
 *
 * <p>근거는 "게이트가 남아 있는 룰의 임계값이 전부 심각도 60 이상에 놓여 있다"는 것 하나다.
 * 누군가 {@link SkinMetrics} 의 경계를 옮기면 그 근거가 조용히 무너지므로 여기서 깨뜨린다.
 */
class SeverityCalculatorTest {

    @Test
    @DisplayName("게이트가 남은 룰은 MILD 구간에 닿지 않는다 — 경계를 옮기면 이 테스트가 깨진다")
    void gatedRulesNeverReachMildBand() {
        // R02 홍조 — 게이트를 막 통과하는 값(임계 초과 첫 정수)
        assertThat(SeverityCalculator.isMild(SkinMetrics.REDNESS_THRESHOLD + 1, true)).isFalse();
        // R01 수분 · R08 장벽 — 낮을수록 나쁘므로 임계 미만 첫 정수
        assertThat(SeverityCalculator.isMild(SkinMetrics.DRY_THRESHOLD - 1, false)).isFalse();
        assertThat(SeverityCalculator.isMild(SkinMetrics.BARRIER_WEAK_THRESHOLD - 1, false)).isFalse();
    }

    @Test
    @DisplayName("게이트를 뗀 룰은 정상 피부에서 0 이 아니라 약하게 걸린다")
    void ungatedRulesStillApplyOnCalmSkin() {
        // 튀김 -10 기준 · 정상 -6 → 중간 -12 → 심함 -15
        assertThat(SeverityCalculator.apply(-10, 50, true)).isEqualTo(-6);
        assertThat(SeverityCalculator.apply(-10, 75, true)).isEqualTo(-12);
        assertThat(SeverityCalculator.apply(-10, 85, true)).isEqualTo(-15);
    }

    @Test
    @DisplayName("심각도 3단계가 서로 겹치지 않는다")
    void bandsAreDisjoint() {
        assertThat(SeverityCalculator.isMild(59, true)).isTrue();
        assertThat(SeverityCalculator.isMild(60, true)).isFalse();
        assertThat(SeverityCalculator.isSevere(79, true)).isFalse();
        assertThat(SeverityCalculator.isSevere(80, true)).isTrue();
    }
}
