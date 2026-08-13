package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

/** GOOD / CAUTION 행. */
public record FeedbackDto(String message, int scoreDelta, String ruleCode) {

    public static FeedbackDto from(SkinPlateFeedback feedback) {
        return new FeedbackDto(feedback.getMessage(), feedback.getScoreDelta(), feedback.getRuleCode());
    }
}
