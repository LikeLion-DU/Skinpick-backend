package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

import java.util.List;

/**
 * 좋은 점 / 주의사항 / 추천 행동을 미리 3개 배열로 나눠서 내려준다.
 * Flutter가 타입별로 필터링하지 않고 바로 3개 섹션에 렌더링할 수 있다.
 */
public record FeedbackGroupDto(
        List<FeedbackDto> good,
        List<FeedbackDto> caution,
        List<ActionDto> action
) {
    public static FeedbackGroupDto from(List<SkinPlateFeedback> all) {
        return new FeedbackGroupDto(
                all.stream().filter(feedback -> feedback.getType() == FeedbackType.GOOD)
                   .map(FeedbackDto::from).toList(),
                all.stream().filter(feedback -> feedback.getType() == FeedbackType.CAUTION)
                   .map(FeedbackDto::from).toList(),
                all.stream().filter(feedback -> feedback.getType() == FeedbackType.ACTION)
                   .map(ActionDto::from).toList());
    }
}
