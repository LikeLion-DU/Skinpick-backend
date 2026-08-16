package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.global.common.Texts;

import java.util.List;

/**
 * 점수 하나 + 등급 + 관찰 근거.
 *
 * 피부 상태 5지표와 피부 나이 7축이 화면에서 같은 모양(바 + 뱃지 + 한 줄)이라
 * DTO 를 하나만 둔다. 둘로 나누면 등급 계산이 두 벌이 되고, 한쪽만 고쳐지는 날이 온다.
 *
 * @param score    AI 가 낸 원래 점수. 화면 바는 이 값으로 그린다 — 방향을 뒤집지 않는다
 * @param level    Backend 가 방향을 맞춰 계산한 등급 (AI 는 등급을 반환하지 않는다)
 * @param evidence 관찰 문장. 개수 상한을 여기서 자른다
 */
public record ScoredItemDto(String key, int score, SkinLevel level, List<String> evidence) {

    /**
     * evidence 개수는 <b>스키마가 아니라 여기서</b> 막는다. OpenAI Structured Outputs 의
     * strict 모드는 {@code maxItems} 를 지원하지 않아 배열 길이를 스키마로 강제할 수 없다.
     * 프롬프트로 지시하고, 지켜지지 않으면 잘라낸다 — 안 자르면 토큰 예산과 화면 레이아웃이
     * 같이 무너진다.
     *
     * @param higherIsWorse oil·redness·trouble·wrinkles·pores·pigmentation·blemishMarks 면 true
     * @param maxEvidence   상태 지표 2개 · 나이 축 1개
     */
    public static ScoredItemDto of(String key, int score, boolean higherIsWorse,
                                   List<String> evidence, int maxEvidence) {

        int aligned = higherIsWorse ? 100 - score : score;

        return new ScoredItemDto(key, score, SkinLevel.of(aligned), trim(evidence, maxEvidence));
    }

    /**
     * 0~100 밖이면 못 믿는 값이라는 뜻이다. clamp 로 살리지 않는다 —
     * {@code score} 키가 빠진 응답은 Jackson 이 0 으로 채우는데, 그걸 깎아 두면
     * "피부결 0점 · SEVERE" 라는 없는 등급이 화면에 그려진다.
     * {@code SkinAnalysisService.skinAge} 가 estimatedSkinAge 를 clamp 하지 않는 것과 같은 규칙이다.
     */
    public static boolean isUsableScore(int score) {
        return score >= 0 && score <= 100;
    }

    /**
     * 개수와 <b>길이</b>를 같이 자른다. 개수만 막고 길이는 프롬프트를 믿으면, 400자짜리
     * 근거 한 줄이 S05 의 행 높이를 무너뜨린다 — DB 컬럼에 닿지 않아 500 도 안 나고
     * 화면만 조용히 깨진다. 프롬프트 지시는 30자이고 60은 그 두 배의 여유다.
     *
     * 이 절삭이 토큰을 아끼지는 않는다. 응답을 다 받은 뒤라 이미 생성·과금된 것을
     * 버리는 것뿐이다. 토큰까지 아끼려면 스키마의 maxItems 로 막아야 하는데,
     * strict 모드가 그 키워드를 받는지는 확인하지 않았다 — 같은 스키마가 쓰는
     * minimum/maximum 이 통과하므로 될 가능성이 높다. 확인은 마감 뒤로 미룬다.
     */
    private static final int EVIDENCE_MAX_LENGTH = 60;

    private static List<String> trim(List<String> evidence, int max) {
        if (evidence == null) return List.of();

        return evidence.stream()
                .filter(sentence -> sentence != null && !sentence.isBlank())
                .limit(max)
                .map(sentence -> Texts.ellipsize(sentence, EVIDENCE_MAX_LENGTH))
                .toList();
    }
}
