package com.skinplate.api.domain.skin.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 오늘 관찰된 피부 상태. {@link com.skinplate.api.domain.user.entity.SkinType} 과 짝을 이룬다.
 *
 *   타입 : 수분·유분이 만드는 피부의 성질 — 건성 · 지성 · 복합성 · 보통
 *   상태 : 그 위에 오늘 얹힌 것 — 붉은기 · 트러블 · 장벽 · 피부결 · 색소
 *
 * 나누는 이유는 하나다. 붉은기가 올라온 날 "당신은 민감성입니다"로 타입이 바뀌면
 * 갭 카드가 말하려던 "평소 알던 것 vs 오늘"이 성립하지 않는다. 타입은 잘 안 변하고
 * 상태는 매일 변하는 값이라, 같은 목록에 섞으면 둘 다 못 읽는다.
 *
 * <b>전부 규칙 도출이다 — AI 에게 묻지 않는다.</b> 임계값도 새로 만들지 않았다.
 * 앞의 넷은 {@link SkinMetrics} 의 판정자를 그대로 쓰고, 뒤의 둘은 이미 받고 있는
 * 피부 나이 축(skinTexture · pigmentation)을 되읽는다 — Vision 호출도 스키마 항목도
 * 늘리지 않는다.
 *
 * 방향만 맞추면 경계가 하나다. 높을수록 좋은 축은 40 미만, 높을수록 나쁜 축은 60 초과.
 */
public enum SkinTrait {

    DEHYDRATED           ("수분 부족"),
    REDNESS_PRONE        ("붉은기"),
    TROUBLE_PRONE        ("트러블"),
    BARRIER_WEAK         ("장벽 약화"),
    TEXTURE_CONCERN      ("피부결 거칢"),
    PIGMENTATION_CONCERN ("색소·잡티");

    /** 나이 축 skinTexture 는 높을수록 좋다. 미만이면 표면이 거칠다 (프롬프트 기준 "다소 거칢" 이하). */
    public static final int TEXTURE_CONCERN_THRESHOLD = 40;

    /** 나이 축 pigmentation 은 높을수록 나쁘다. 초과면 색조 불균일이 넓다. */
    public static final int PIGMENTATION_CONCERN_THRESHOLD = 60;

    private final String label;

    SkinTrait(String label) { this.label = label; }

    public String getLabel() { return label; }

    /**
     * 오늘 해당하는 상태를 <b>심각한 순으로</b> 전부 돌려준다. 없으면 빈 목록이다.
     *
     * 정렬이 필요한 이유는 {@code SkinTypeDto} 가 라벨에 앞의 둘만 싣기 때문이다.
     * 선언 순서로 자르면 장벽이 무너진 사람의 라벨에 장벽이 안 뜨는 날이 온다.
     * 동점에서 순서가 흔들리면 같은 사진에 다른 문구가 나오므로 이름으로 한 번 더
     * 고정한다 — {@code RecommendationCandidates.topConcerns} 와 같은 규칙이다.
     *
     * @param textureScore      나이 축 skinTexture. 예전 기록이면 원본에 없어 null 이다
     * @param pigmentationScore 나이 축 pigmentation. 같은 이유로 null 일 수 있다
     */
    public static List<SkinTrait> observe(SkinMetrics metrics,
                                          Integer textureScore,
                                          Integer pigmentationScore) {
        record Candidate(SkinTrait trait, boolean present, int severity) {}

        // 수분이 부족한데 유분은 올라와 있는 상태 = 흔히 말하는 수부지.
        // 유분 조건을 빼면 DRY 타입과 뜻이 겹쳐 "건성 · 수분 부족"이 된다.
        boolean dehydrated = metrics.isDry() && metrics.isOilElevated();

        List<Candidate> candidates = new ArrayList<>(List.of(
                new Candidate(DEHYDRATED,    dehydrated,              100 - metrics.getHydration()),
                new Candidate(REDNESS_PRONE, metrics.hasRedness(),    metrics.getRedness()),
                new Candidate(TROUBLE_PRONE, metrics.hasTrouble(),    metrics.getTrouble()),
                new Candidate(BARRIER_WEAK,  metrics.isBarrierWeak(), 100 - metrics.getBarrier())));

        if (textureScore != null) {
            candidates.add(new Candidate(TEXTURE_CONCERN,
                    textureScore < TEXTURE_CONCERN_THRESHOLD, 100 - textureScore));
        }
        if (pigmentationScore != null) {
            candidates.add(new Candidate(PIGMENTATION_CONCERN,
                    pigmentationScore > PIGMENTATION_CONCERN_THRESHOLD, pigmentationScore));
        }

        return candidates.stream()
                .filter(Candidate::present)
                .sorted(Comparator.comparingInt(Candidate::severity).reversed()
                                  .thenComparing(candidate -> candidate.trait().name()))
                .map(Candidate::trait)
                .toList();
    }
}
