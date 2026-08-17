package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 오늘의 피부 타입과 상태. <b>둘 다 지표에서 규칙으로 도출한다</b> — AI 에게 묻지 않는다.
 *
 * {@code primary} 는 {@code SkinAnalysisResponse.skinTypeGap.observed} 와 <b>항상 같은 값</b>이다.
 * 예전에는 이 필드가 AI 관찰값이라 둘이 갈릴 수 있었고, 그래서 S05 는 제목에 규칙값을,
 * 칩에 AI 값을 그리며 한 화면에서 타입 두 개를 들고 있었다. 판정을 백엔드로 모아
 * source of truth 를 하나로 만들었다.
 *
 * @param primary DRY · NORMAL · OILY · COMBINATION 만 나온다. SENSITIVE 는 자가 신고 전용이다
 * @param traits  오늘 해당하는 상태 <b>전부</b>를 심각한 순으로. 라벨은 앞의 둘만 쓰지만
 *                목록은 자르지 않는다 — 규칙 도출값이라 버릴 이유가 없다
 * @param label   화면에 그대로 쓰는 문구. 서버가 만드는 이유는 갭 카드와 같다 —
 *                앱이 primary 4개 × traits 6개를 각자 조합하기 시작하면 문구를 바꿀 때
 *                두 곳이 어긋나고, 어긋난 쪽이 화면이다
 */
public record SkinTypeDto(SkinType primary, List<SkinTrait> traits, String label) {

    /**
     * 유분은 올라와 있는데 수분이 부족한 상태를 사용자는 '수부지'라고 부른다.
     * "복합성 · 수분 부족"만 보여주면 자기 피부인 줄 모른다.
     *
     * {@code DEHYDRATED} 자체가 이미 유분 조건을 담고 있어(SkinTrait.observe) primary 를
     * 따로 보지 않는다. 다만 라벨에 실린 것만 본다 — 잘려 나간 상태를 근거로 별칭만
     * 붙으면 "복합성 · 장벽 약화 · 붉은기(수부지)" 처럼 앞뒤가 안 맞는 문구가 된다.
     */
    private static final String DEHYDRATED_ALIAS = "수부지";

    /**
     * 라벨에 싣는 상태 개수. S05 의 칩은 폭이 정해져 있지 않고 QA 에서 이미 두 번 잘렸다 —
     * 여섯 개를 다 이어 붙이면 40자를 넘긴다. 잘리는 것은 문구뿐이고 {@code traits} 는
     * 온전히 내려간다.
     */
    private static final int LABEL_TRAITS_MAX = 2;

    /// 형제 DTO(ScoredItemDto)가 null 리스트를 견디므로 여기도 같은 계약을 지킨다.
    public static SkinTypeDto of(SkinType primary, List<SkinTrait> traits) {
        List<SkinTrait> safe = traits == null ? List.of() : traits;
        return new SkinTypeDto(primary, safe, label(primary, safe));
    }

    private static String label(SkinType primary, List<SkinTrait> traits) {
        List<SkinTrait> shown = traits.stream().limit(LABEL_TRAITS_MAX).toList();

        String joined = Stream.concat(Stream.of(primary.getLabel()),
                                      shown.stream().map(SkinTrait::getLabel))
                .collect(Collectors.joining(" · "));

        return shown.contains(SkinTrait.DEHYDRATED)
                ? joined + "(" + DEHYDRATED_ALIAS + ")"
                : joined;
    }
}
