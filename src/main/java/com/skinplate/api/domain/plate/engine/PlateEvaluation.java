package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

import java.util.ArrayList;
import java.util.List;

/** 엔진 실행 결과. Service는 이 값을 SkinPlate 엔티티로 옮기기만 하면 된다. */
public record PlateEvaluation(
        int score,
        List<RuleResult> results,
        String summary
) {
    public List<String> appliedRuleCodes() {
        return results.stream().map(RuleResult::ruleCode).toList();
    }

    /**
     * RuleResult 목록을 화면 표시 순서대로 SkinPlateFeedback 엔티티로 변환한다.
     * 좋은 점 → 주의사항 → 추천 행동 순서가 그대로 displayOrder가 된다.
     */
    public List<SkinPlateFeedback> toFeedbacks() {
        List<SkinPlateFeedback> feedbacks = new ArrayList<>();
        int order = 0;

        for (RuleResult result : results) {
            if (result.type() == FeedbackType.GOOD) {
                feedbacks.add(SkinPlateFeedback.good(
                        result.ruleCode(), result.message(), result.delta(), order++));
            }
        }
        for (RuleResult result : results) {
            if (result.type() == FeedbackType.CAUTION) {
                feedbacks.add(SkinPlateFeedback.caution(
                        result.ruleCode(), result.message(), result.delta(), order++));
            }
        }
        for (RuleResult result : results) {
            if (result.hasAction()) {
                feedbacks.add(SkinPlateFeedback.action(
                        result.ruleCode(), result.actionMessage(), result.expectedGain(), order++));
            }
        }
        return feedbacks;
    }
}
