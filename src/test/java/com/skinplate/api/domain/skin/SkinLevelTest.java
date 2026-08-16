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

    @Test
    @DisplayName("뱃지와 정확히 40·60 에서 한 칸 어긋난다 — 의도한 것이라 여기 못 박는다")
    void badgeBoundariesDifferByOnePointOnPurpose() {
        // 뱃지(SkinHighlightBuilder)는 3단이고 등급은 5단이라 경계가 완전히 겹칠 수 없다.
        // 정확히 60 이면 뱃지는 GOOD("수분 충분"), 등급은 NORMAL 이다. 굵기가 다른 두 눈금이지
        // 어느 한쪽이 틀린 게 아니다 — 누가 "버그"로 보고 한쪽만 옮기면 화면이 깨진다.
        assertThat(SkinLevel.of(60)).isEqualTo(SkinLevel.NORMAL);   // 뱃지는 >= 60 → GOOD
        assertThat(SkinLevel.of(61)).isEqualTo(SkinLevel.GOOD);
        assertThat(SkinLevel.of(40)).isEqualTo(SkinLevel.CAUTION);  // 뱃지는 >= 40 → WARN
        assertThat(SkinLevel.of(41)).isEqualTo(SkinLevel.NORMAL);
    }
}
