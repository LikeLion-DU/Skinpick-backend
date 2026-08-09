package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.plate.entity.FeedbackType;

/**
 * 룰 1개의 평가 결과.
 *
 * delta        : 점수에 더해지는 값 (+ 가점 / − 감점)
 * actionMessage: 감점 룰이 제시하는 개선 행동 (없으면 null)
 * expectedGain : 그 행동을 했을 때 회복되는 점수. delta의 절댓값과 다를 수 있다.
 */
public record RuleResult(
        String ruleCode,
        int delta,
        FeedbackType type,
        String message,
        String actionMessage,
        int expectedGain
) {
    public static RuleResult good(String ruleCode, int delta, String message) {
        return new RuleResult(ruleCode, delta, FeedbackType.GOOD, message, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION, message, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message,
                                     String actionMessage, int expectedGain) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION,
                              message, actionMessage, expectedGain);
    }

    public boolean hasAction() {
        return actionMessage != null && !actionMessage.isBlank();
    }
}
