package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.dto.SkinTypeDto;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨을 서버가 만드는 이유가 여기 있다 — 앱이 primary 4개 × 상태 6개를 각자 조합하면
 * 문구를 바꿀 때 두 곳이 어긋나고, 어긋난 쪽이 화면이다. 조합 규칙을 여기 고정한다.
 */
class SkinTypeDtoTest {

    @Test
    @DisplayName("수분 부족은 '수부지'로 읽어 준다 — 이 말이 없으면 자기 피부인 줄 모른다")
    void dehydratedGetsItsCommonName() {
        SkinTypeDto dto = SkinTypeDto.of(SkinType.COMBINATION, List.of(SkinTrait.DEHYDRATED));

        assertThat(dto.label()).isEqualTo("복합성 · 수분 부족(수부지)");
    }

    @Test
    @DisplayName("상태가 없으면 타입만 — 가운뎃점이 붙어 끝나지 않는다")
    void primaryOnly() {
        assertThat(SkinTypeDto.of(SkinType.DRY, List.of()).label()).isEqualTo("건성");
    }

    @Test
    @DisplayName("null 목록도 견딘다 — 형제 DTO(ScoredItemDto)와 같은 계약이다")
    void nullTraitsBecomeEmpty() {
        SkinTypeDto dto = SkinTypeDto.of(SkinType.NORMAL, null);

        assertThat(dto.traits()).isEmpty();
        assertThat(dto.label()).isEqualTo("보통");
    }

    @Test
    @DisplayName("목록은 전부 내려가고 라벨만 둘까지 쓴다 — 여섯을 이어 붙이면 칩이 무너진다")
    void labelKeepsOnlyTheTwoWorstButTheListIsWhole() {
        List<SkinTrait> all = List.of(SkinTrait.BARRIER_WEAK, SkinTrait.REDNESS_PRONE,
                SkinTrait.TROUBLE_PRONE, SkinTrait.TEXTURE_CONCERN);

        SkinTypeDto dto = SkinTypeDto.of(SkinType.COMBINATION, all);

        assertThat(dto.traits()).isEqualTo(all);
        assertThat(dto.label()).isEqualTo("복합성 · 장벽 약화 · 붉은기");
    }

    @Test
    @DisplayName("라벨에서 잘려 나간 수분 부족에는 별칭을 붙이지 않는다 — 앞뒤가 안 맞는 문구가 된다")
    void aliasFollowsWhatTheLabelActuallyShows() {
        // DEHYDRATED 가 3순위라 라벨에는 없다. 그런데 별칭만 붙으면
        // "지성 · 장벽 약화 · 붉은기(수부지)" 처럼 근거 없는 괄호가 남는다.
        SkinTypeDto dto = SkinTypeDto.of(SkinType.OILY,
                List.of(SkinTrait.BARRIER_WEAK, SkinTrait.REDNESS_PRONE, SkinTrait.DEHYDRATED));

        assertThat(dto.label())
                .isEqualTo("지성 · 장벽 약화 · 붉은기")
                .doesNotContain("수부지");
    }

    @Test
    @DisplayName("피부결·색소도 라벨에 그대로 실린다")
    void appearanceTraitsAreLabelled() {
        assertThat(SkinTypeDto.of(SkinType.NORMAL,
                List.of(SkinTrait.PIGMENTATION_CONCERN, SkinTrait.TEXTURE_CONCERN)).label())
                .isEqualTo("보통 · 색소·잡티 · 피부결 거칢");
    }
}
