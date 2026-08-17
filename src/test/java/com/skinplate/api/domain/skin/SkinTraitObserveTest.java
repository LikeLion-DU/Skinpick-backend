package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오늘의 피부 상태. 임계값은 전부 이미 있던 것을 그대로 쓴다 —
 * 여기서 숫자를 새로 정하면 뱃지·룰 엔진·추천이 보는 "건조"와 상태가 보는 "건조"가
 * 갈라지고, 같은 화면에서 한쪽만 켜진다.
 */
class SkinTraitObserveTest {

    /** 상태 판정에 안 쓰이는 값은 중립으로. 나이 축이 없는 예전 기록 상황도 겸한다. */
    private static List<SkinTrait> observe(int hydration, int oil, int redness,
                                           int trouble, int barrier) {
        return SkinTrait.observe(SkinMetrics.of(hydration, oil, redness, trouble, barrier), null, null);
    }

    @Test
    @DisplayName("아무 데도 안 걸리면 빈 목록이다 — 멀쩡한 피부에 상태 칩이 뜨지 않는다")
    void healthySkinHasNoConditions() {
        assertThat(observe(70, 40, 30, 20, 80)).isEmpty();
    }

    @Test
    @DisplayName("붉은기 60 초과 → REDNESS_PRONE (뱃지·룰 엔진과 같은 임계)")
    void redness() {
        assertThat(observe(70, 40, 61, 20, 80)).containsExactly(SkinTrait.REDNESS_PRONE);
        assertThat(observe(70, 40, 60, 20, 80)).isEmpty();
    }

    @Test
    @DisplayName("트러블 60 초과 → TROUBLE_PRONE")
    void trouble() {
        assertThat(observe(70, 40, 30, 61, 80)).containsExactly(SkinTrait.TROUBLE_PRONE);
        assertThat(observe(70, 40, 30, 60, 80)).isEmpty();
    }

    @Test
    @DisplayName("장벽 40 미만 → BARRIER_WEAK")
    void barrier() {
        assertThat(observe(70, 40, 30, 20, 39)).containsExactly(SkinTrait.BARRIER_WEAK);
        assertThat(observe(70, 40, 30, 20, 40)).isEmpty();
    }

    @Test
    @DisplayName("수분 부족은 유분이 올라와 있을 때만 붙는다 — 건성에 붙이면 타입과 같은 말을 두 번 한다")
    void dehydratedNeedsOilToBeElevated() {
        // 수분 30 · 유분 65 → 복합성인데 수분이 모자란다. 이게 '수부지'다.
        assertThat(observe(30, 65, 30, 20, 80)).containsExactly(SkinTrait.DEHYDRATED);

        // 수분 30 · 유분 30 → 타입이 이미 건성이다. "건성 · 수분 부족" 은 같은 말이다.
        assertThat(observe(30, 30, 30, 20, 80)).isEmpty();
    }

    @Test
    @DisplayName("피부결 40 미만 → TEXTURE_CONCERN · 색소 60 초과 → PIGMENTATION_CONCERN")
    void appearanceAxesBecomeConditions() {
        SkinMetrics healthy = SkinMetrics.of(70, 40, 30, 20, 80);

        assertThat(SkinTrait.observe(healthy, 39, 61))
                .containsExactlyInAnyOrder(SkinTrait.TEXTURE_CONCERN, SkinTrait.PIGMENTATION_CONCERN);

        // 경계 바깥 — 피부결 40 은 아직 괜찮고 색소 60 도 아직 괜찮다
        assertThat(SkinTrait.observe(healthy, 40, 60)).isEmpty();
    }

    @Test
    @DisplayName("나이 축이 없는 예전 기록이면 그 두 상태만 빠진다 — 나머지 넷은 그대로 나온다")
    void missingAgeAxesDropOnlyTheirConditions() {
        SkinMetrics metrics = SkinMetrics.of(70, 40, 90, 20, 80);

        assertThat(SkinTrait.observe(metrics, null, null))
                .containsExactly(SkinTrait.REDNESS_PRONE);
    }

    @Test
    @DisplayName("심각한 순으로 정렬한다 — 선언 순서로 자르면 무너진 장벽이 라벨에서 사라진다")
    void sortedBySeverity() {
        // 장벽 5(정렬 95) > 붉은기 90 > 트러블 70. 선언 순서라면 붉은기·트러블이 먼저다.
        assertThat(observe(70, 40, 90, 70, 5))
                .containsExactly(SkinTrait.BARRIER_WEAK,
                                 SkinTrait.REDNESS_PRONE,
                                 SkinTrait.TROUBLE_PRONE);
    }

    @Test
    @DisplayName("동점이면 이름으로 고정한다 — 같은 사진에 다른 문구가 나오지 않는다")
    void tiesAreBrokenDeterministically() {
        // 붉은기 70 · 트러블 70 → 심각도가 같다
        assertThat(observe(70, 40, 70, 70, 80))
                .containsExactly(SkinTrait.REDNESS_PRONE, SkinTrait.TROUBLE_PRONE);
    }

    @Test
    @DisplayName("여섯이 한꺼번에 걸려도 전부 돌려준다 — 자르는 것은 라벨이지 목록이 아니다")
    void everyConditionCanFireAtOnce() {
        assertThat(SkinTrait.observe(SkinMetrics.of(10, 90, 90, 90, 10), 10, 90))
                .hasSize(SkinTrait.values().length);
    }
}
