package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.dto.SkinTypeDto;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨을 서버가 만드는 이유가 여기 있다 — 앱이 primary 4개 × traits 4개를 각자 조합하면
 * 문구를 바꿀 때 두 곳이 어긋나고, 어긋난 쪽이 화면이다. 조합 규칙을 여기 고정한다.
 */
class SkinTypeDtoTest {

    @Test
    @DisplayName("복합성 + 수분 부족은 '수부지'로 읽어 준다 — 이 말이 없으면 자기 피부인 줄 모른다")
    void dehydratedCombinationGetsItsCommonName() {
        SkinTypeDto dto = SkinTypeDto.of(SkinType.COMBINATION, List.of(SkinTrait.DEHYDRATED));

        assertThat(dto.label()).isEqualTo("복합성 · 수분 부족 경향(수부지)");
    }

    @Test
    @DisplayName("복합성 + 민감 경향")
    void combinationSensitive() {
        assertThat(SkinTypeDto.of(SkinType.COMBINATION, List.of(SkinTrait.SENSITIVE_TENDENCY)).label())
                .isEqualTo("복합성 · 민감 경향");
    }

    @Test
    @DisplayName("경향이 없으면 타입만 — 가운뎃점이 붙어 끝나지 않는다")
    void primaryOnly() {
        assertThat(SkinTypeDto.of(SkinType.DRY, List.of()).label()).isEqualTo("건성");
    }

    @Test
    @DisplayName("경향이 여럿이면 순서대로 이어 붙인다")
    void multipleTraits() {
        assertThat(SkinTypeDto.of(SkinType.OILY,
                List.of(SkinTrait.OILY_T_ZONE, SkinTrait.TROUBLE_TENDENCY)).label())
                .isEqualTo("지성 · T존 유분 경향 · 트러블 경향");
    }

    @Test
    @DisplayName("수부지 별칭은 복합성일 때만 붙는다 — 건성 + 수분 부족은 그냥 건성이다")
    void aliasOnlyAppliesToCombination() {
        assertThat(SkinTypeDto.of(SkinType.DRY, List.of(SkinTrait.DEHYDRATED)).label())
                .isEqualTo("건성 · 수분 부족 경향")
                .doesNotContain("수부지");
    }
}
