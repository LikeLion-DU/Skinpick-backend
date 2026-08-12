package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

/** ACTION 행. expectedGain은 scoreDelta의 절댓값이 아니라 별도 값이다. */
public record ActionDto(String message, int expectedGain, String ruleCode) {

    public static ActionDto from(SkinPlateFeedback feedback) {
        return new ActionDto(feedback.getMessage(), feedback.getExpectedGain(), feedback.getRuleCode());
    }
}
