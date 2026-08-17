package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /plates/analyze 의 응답. 저장 전이라 plateId·createdAt 이 없다 —
 * SkinPlateResponse 를 재사용하면 null 로 채워 넣게 되는데, 그러면 앱이
 * "저장된 것"으로 오해한다. analysisToken 을 들고 있다가 저장을 확정할 때
 * POST /plates/records 로 되돌려 보낸다.
 *
 * @param skinBasis      채점 기준 피부가 오늘(KST) 측정인지({@link SkinBasis})
 * @param skinMeasuredAt 그 피부 분석의 측정일. RECENT 일 때 앱이 "8/3 측정 기준"을 그린다
 */
public record PlateAnalysisResponse(
        String analysisToken,
        Long skinAnalysisId,
        SkinBasis skinBasis,
        LocalDate skinMeasuredAt,
        int plateScore,
        int baseScore,
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules
) {}
