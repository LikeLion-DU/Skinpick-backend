package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;

import java.util.List;

/**
 * POST /plates/analyze 의 응답. 저장 전이라 plateId·createdAt 이 없다 —
 * SkinPlateResponse 를 재사용하면 null 로 채워 넣게 되는데, 그러면 앱이
 * "저장된 것"으로 오해한다. analysisToken 을 들고 있다가 저장을 확정할 때
 * POST /plates/records 로 되돌려 보낸다.
 */
public record PlateAnalysisResponse(
        String analysisToken,
        Long skinAnalysisId,
        int plateScore,
        int baseScore,
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules
) {}
