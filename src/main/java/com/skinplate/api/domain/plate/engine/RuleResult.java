package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.plate.entity.FeedbackType;

/**
 * 룰 1개의 평가 결과.
 *
 * delta        : 점수에 더해지는 값 (+ 가점 / − 감점)
 * reason       : "왜 이 판정인가" — 현재 피부 지표와 음식 특성을 잇는 결정론 템플릿 문장.
 *                message(짧은 라벨)와 분리한 이유: 리포트가 message 문자열 빈도로 집계하는데,
 *                지표에 따라 달라지는 문장을 라벨에 넣으면 그 집계가 전부 1회짜리로 흩어진다.
 *                AI 문장이 아니다 — 같은 입력이면 같은 문장이다. (없으면 null)
 * actionMessage: 감점 룰이 제시하는 개선 행동 (없으면 null)
 * expectedGain : 그 행동을 했을 때 회복되는 점수. delta의 절댓값과 다를 수 있다.
 */
public record RuleResult(
        String ruleCode,
        int delta,
        FeedbackType type,
        String message,
        String reason,
        String actionMessage,
        int expectedGain
) {
    public static RuleResult good(String ruleCode, int delta, String message) {
        return good(ruleCode, delta, message, null);
    }

    public static RuleResult good(String ruleCode, int delta, String message, String reason) {
        return new RuleResult(ruleCode, delta, FeedbackType.GOOD, message, reason, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message) {
        return caution(ruleCode, delta, message, (String) null);
    }

    /** 행동 카드가 없는 주의 — R07 이 기름진 비튀김 음식에서 쓴다(튀김옷 제거가 성립 안 한다). */
    public static RuleResult caution(String ruleCode, int delta, String message, String reason) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION, message, reason, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message,
                                     String actionMessage, int expectedGain) {
        return caution(ruleCode, delta, message, null, actionMessage, expectedGain);
    }

    public static RuleResult caution(String ruleCode, int delta, String message, String reason,
                                     String actionMessage, int expectedGain) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION,
                              message, reason, actionMessage, expectedGain);
    }

    public boolean hasAction() {
        return actionMessage != null && !actionMessage.isBlank();
    }
}
