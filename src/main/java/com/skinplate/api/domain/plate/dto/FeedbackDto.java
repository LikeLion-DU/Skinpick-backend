package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

/**
 * @param reason "왜 이 판정인가" — 피부 지표와 음식 특성을 잇는 문장. V8 이전 행은
 *               null 이라 non_null 직렬화로 키가 빠지고, 앱은 이유 줄을 그리지 않는다
 */
public record FeedbackDto(String message, String reason, int scoreDelta, String ruleCode) {

    public static FeedbackDto from(SkinPlateFeedback feedback) {
        return new FeedbackDto(feedback.getMessage(), feedback.getReason(),
                feedback.getScoreDelta(), feedback.getRuleCode());
    }
}
