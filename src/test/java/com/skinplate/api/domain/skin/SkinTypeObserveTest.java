package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.SkinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 피부 타입은 <b>수분과 유분 둘</b>로만 정해진다. (PRD §4.4.1)
 *
 * 이 테스트가 지키는 것은 두 가지다. 유분을 두 단계로 읽어 T존 복합성이 NORMAL 로
 * 떨어지지 않는 것, 그리고 붉은기·트러블·장벽·나이 축이 타입을 흔들지 않는 것.
 * 후자가 깨지면 컨디션이 나쁜 날마다 "당신의 피부 타입"이 바뀌고, 갭 카드가
 * 비교하려던 대상 자체가 사라진다.
 */
class SkinTypeObserveTest {

    @ParameterizedTest(name = "수분 {0} · 유분 {1} → {2}")
    @DisplayName("수분·유분 조합별 타입")
    @CsvSource({
            // 건성 — 유분이 올라오지 않았고 수분이 부족하다
            "30, 30, DRY",
            "20, 10, DRY",
            "38, 52, DRY",      // 시연 지표. 무대에서 말할 값이라 바뀌면 안 된다

            // 지성 — 얼굴 전반이 번들거린다
            "65, 85, OILY",
            "55, 80, OILY",
            "20, 90, OILY",     // 수분이 바닥이어도 전면 유분이면 지성이다 (수부지는 상태로 붙는다)

            // 복합성 — 유분이 부위별로만 올라와 있다
            "50, 65, COMBINATION",   // 전형적인 T존 복합성. 예전 규칙에서는 NORMAL 로 떨어졌다
            "75, 65, COMBINATION",
            "30, 62, COMBINATION",

            // 보통
            "55, 50, NORMAL",
            "90, 10, NORMAL",
            "45, 45, NORMAL"
    })
    void typeFollowsHydrationAndOil(int hydration, int oil, SkinType expected) {
        assertThat(SkinType.observe(metrics(hydration, oil))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "유분 {0} → {1}")
    @DisplayName("유분 경계는 60·70 에서 갈린다 — 60 이상 부분 유분, 70 초과 전면 유분")
    @CsvSource({
            "59, NORMAL",        // 수분 50 이라 건성도 아니다
            "60, COMBINATION",
            "70, COMBINATION",   // isOily() 는 70 '초과' — 정확히 70 은 아직 복합성이다
            "71, OILY"
    })
    void oilBandBoundaries(int oil, SkinType expected) {
        assertThat(SkinType.observe(metrics(50, oil))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "수분 {0} → {1}")
    @DisplayName("수분 경계는 40 에서 갈린다 — 유분이 낮을 때만 본다")
    @CsvSource({
            "39, DRY",
            "40, NORMAL"
    })
    void hydrationBoundary(int hydration, SkinType expected) {
        assertThat(SkinType.observe(metrics(hydration, 30))).isEqualTo(expected);
    }

    @Test
    @DisplayName("붉은기·트러블·장벽은 타입을 바꾸지 않는다 — 오늘의 상태이지 피부의 성질이 아니다")
    void conditionMetricsNeverChangeTheType() {
        // 수분 50 · 유분 65 = 복합성. 나머지 셋을 최악으로 밀어도 복합성이어야 한다.
        // 예전 규칙은 붉은기 70 초과에서 SENSITIVE 로 갈아탔다.
        assertThat(SkinType.observe(SkinMetrics.of(50, 65, 100, 100, 0)))
                .isEqualTo(SkinType.COMBINATION);

        assertThat(SkinType.observe(SkinMetrics.of(50, 65, 0, 0, 100)))
                .isEqualTo(SkinType.COMBINATION);
    }

    @Test
    @DisplayName("SENSITIVE 는 관찰값으로 나오지 않는다 — 자가 신고 선택지로만 남는다")
    void sensitiveIsDeclaredOnly() {
        assertThat(SkinType.observe(SkinMetrics.of(50, 50, 100, 50, 50)))
                .isNotEqualTo(SkinType.SENSITIVE);

        assertThat(SkinType.selectable()).contains(SkinType.SENSITIVE);
    }

    /** 타입 판정에 안 쓰이는 셋은 중립값으로 고정한다 — 값이 새면 무엇이 타입을 정했는지 흐려진다. */
    private static SkinMetrics metrics(int hydration, int oil) {
        return SkinMetrics.of(hydration, oil, 50, 50, 50);
    }
}
