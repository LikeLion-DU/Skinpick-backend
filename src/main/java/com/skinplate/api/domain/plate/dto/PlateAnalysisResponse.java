package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;

import com.skinplate.api.domain.skin.entity.SkinLevel;

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

        /**
         * {@code plateScore} 의 등급. <b>앱이 점수에서 등급을 다시 내지 않게 하려고 싣는다.</b>
         *
         * <p>경계표(0~20 SEVERE … 81~100 EXCELLENT)는 {@link SkinLevel} 한 곳뿐이어야 한다.
         * 이 필드가 없던 시절 앱은 같은 표를 Dart 로 한 벌 더 들고 있었고, 서버가 경계를
         * 옮기면 두 벌이 조용히 갈렸다 — 같은 68점이 화면마다 다른 등급으로 뜬다.
         */
        SkinLevel grade,
        int baseScore,
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules
) {}
