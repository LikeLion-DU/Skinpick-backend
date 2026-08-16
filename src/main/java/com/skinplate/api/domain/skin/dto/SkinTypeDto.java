package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 가 사진에서 읽은 피부 타입.
 *
 * {@code SkinAnalysisResponse.skinTypeGap.observed} 와는 <b>다른 값</b>이다.
 * 그쪽은 5개 지표에서 규칙으로 도출한다(PRD §14.3) — 같은 지표면 항상 같은 타입이
 * 나와야 갭 코멘트가 재현 가능하기 때문이다. 이 필드는 AI 관찰을 그대로 보여준다.
 *
 * 둘이 갈리는 것은 오류가 아니라 정보다. 갭 카드는 규칙값을 계속 쓴다.
 *
 * @param primary DRY · NORMAL · OILY · COMBINATION 만 온다. SENSITIVE 는 traits 쪽이다
 * @param label   화면에 그대로 쓰는 문구. 서버가 만드는 이유는 갭 카드와 같다 —
 *                앱이 primary 4개 × traits 4개를 각자 조합하기 시작하면 문구를 바꿀 때
 *                두 곳이 어긋나고, 어긋난 쪽이 화면이다
 */
public record SkinTypeDto(SkinType primary, List<SkinTrait> traits, String label) {

    /**
     * 복합성이면서 수분이 부족한 상태를 사용자는 '수부지'라고 부른다.
     * "복합성 · 수분 부족 경향"만 보여주면 자기 피부인 줄 모른다.
     */
    private static final String DEHYDRATED_COMBINATION_ALIAS = "수부지";

    public static SkinTypeDto of(SkinType primary, List<SkinTrait> traits) {
        return new SkinTypeDto(primary, traits, label(primary, traits));
    }

    private static String label(SkinType primary, List<SkinTrait> traits) {
        String joined = Stream.concat(Stream.of(primary.getLabel()),
                                      traits.stream().map(SkinTrait::getLabel))
                .collect(Collectors.joining(" · "));

        return primary == SkinType.COMBINATION && traits.contains(SkinTrait.DEHYDRATED)
                ? joined + "(" + DEHYDRATED_COMBINATION_ALIAS + ")"
                : joined;
    }
}
