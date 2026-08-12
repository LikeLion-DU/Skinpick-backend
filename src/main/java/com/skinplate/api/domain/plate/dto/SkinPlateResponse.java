package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.SkinPlate;

import java.time.LocalDateTime;
import java.util.List;

public record SkinPlateResponse(
        Long plateId,

        /*
         * 설계서 §1.18 에는 없던 필드다. 앱은 S07 결과에서 S08 추천으로 넘어가는데
         * (PRD §6 화면 전환), 추천 조회가 skinAnalysisId 를 요구한다. 이 값이 응답에
         * 없으면 앱은 "최신 피부 분석"을 대신 쓰는 수밖에 없고, 그러면 과거 Plate 를
         * 다시 열었을 때 엉뚱한 날짜의 추천이 뜬다.
         * 설계서 Part 3 계약 대조표도 함께 고쳤다.
         */
        Long skinAnalysisId,

        int plateScore,
        int baseScore,        // 항상 RuleConstants.BASE_SCORE(70). 계산 내역 카드 첫 줄
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules,
        LocalDateTime createdAt
) {
    /**
     * appliedRules는 엔티티에 JSON 문자열로 저장돼 있으므로
     * Service에서 ObjectMapper로 파싱한 결과를 넘겨받는다.
     * DTO가 ObjectMapper를 들고 있지 않게 하기 위한 선택이다.
     */
    public static SkinPlateResponse from(SkinPlate entity, List<String> appliedRules) {
        return new SkinPlateResponse(
                entity.getId(),
                entity.getSkinAnalysis().getId(),
                entity.getPlateScore(),
                RuleConstants.BASE_SCORE,
                entity.getSummary(),
                FoodAnalysisDto.from(entity.getFoodAnalysis()),
                FeedbackGroupDto.from(entity.getFeedbacks()),
                appliedRules,
                entity.getCreatedAt());
    }
}
