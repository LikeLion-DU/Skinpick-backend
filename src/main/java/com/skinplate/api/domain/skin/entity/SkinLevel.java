package com.skinplate.api.domain.skin.entity;

/**
 * 지표 하나의 상태 등급. AI 는 점수만 내고 등급은 여기서 정한다.
 *
 * AI 에게 등급까지 물으면 같은 점수에 다른 등급이 붙는 날이 오고, 그때 화면은
 * "38점인데 EXCELLENT"를 그대로 그린다. 등급을 Backend 가 만들면 UI 기준을 바꿔도
 * 과거 응답을 다시 받을 필요가 없다 — 저장된 점수에서 언제든 다시 계산된다.
 *
 * 경계는 새로 만들지 않았다. 이미 판정에 쓰이는 값들이다.
 *   40 : {@link SkinMetrics#DRY_THRESHOLD} · {@link SkinMetrics#BARRIER_WEAK_THRESHOLD}
 *   60 : SkinHighlightBuilder 의 GOOD 경계
 *   80 : SeverityCalculator 의 SEVERE 경계(severity 80)
 *   20 : 그 SEVERE 경계를 방향 정렬 점수로 뒤집은 값 (aligned = 100 − severity)
 *
 * 뱃지(SkinHighlightBuilder)는 "60 이상 GOOD", 등급은 "61 이상 GOOD" 이라
 * 정확히 40·60 인 한 점에서만 한 칸 어긋난다. 등급 구간은 명세가 준 값을 그대로 쓴다.
 */
public enum SkinLevel {

    SEVERE,      // 0~20
    CAUTION,     // 21~40
    NORMAL,      // 41~60
    GOOD,        // 61~80
    EXCELLENT;   // 81~100

    /**
     * @param alignedScore 반드시 <b>"높을수록 좋음"으로 방향을 맞춘</b> 점수다.
     *                     oil·redness·trouble·wrinkles 처럼 높을수록 나쁜 지표를
     *                     그대로 넣으면 유분 90 이 EXCELLENT 가 된다.
     */
    public static SkinLevel of(int alignedScore) {
        if (alignedScore <= 20) return SEVERE;
        if (alignedScore <= 40) return CAUTION;
        if (alignedScore <= 60) return NORMAL;
        if (alignedScore <= 80) return GOOD;
        return EXCELLENT;
    }
}
