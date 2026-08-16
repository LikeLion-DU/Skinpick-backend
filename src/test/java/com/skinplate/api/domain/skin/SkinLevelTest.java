package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.entity.SkinLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등급을 AI 가 아니라 Backend 가 만드는 이유가 여기 있다 — 경계가 코드에 고정돼야
 * 같은 점수에 항상 같은 등급이 붙는다. 경계를 옮기면 이 테스트가 먼저 깨진다.
 */
class SkinLevelTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("구간 경계는 20/40/60/80 에서 갈린다")
    @CsvSource({
            "0,   SEVERE",
            "20,  SEVERE",
            "21,  CAUTION",
            "40,  CAUTION",
            "41,  NORMAL",
            "60,  NORMAL",
            "61,  GOOD",
            "80,  GOOD",
            "81,  EXCELLENT",
            "100, EXCELLENT"
    })
    void bandBoundaries(int alignedScore, SkinLevel expected) {
        assertThat(SkinLevel.of(alignedScore)).isEqualTo(expected);
    }

    @Test
    @DisplayName("정렬 점수를 넣어야 한다 — 유분 90 을 그대로 넣으면 EXCELLENT 가 된다")
    void needsDirectionAlignedInput() {
        int oil = 90;

        assertThat(SkinLevel.of(oil)).isEqualTo(SkinLevel.EXCELLENT);       // 잘못 쓴 경우
        assertThat(SkinLevel.of(100 - oil)).isEqualTo(SkinLevel.SEVERE);    // 올바른 사용
    }
}
