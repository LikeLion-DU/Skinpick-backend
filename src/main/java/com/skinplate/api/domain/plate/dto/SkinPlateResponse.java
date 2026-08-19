package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.SkinPlate;

import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;
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

        /*
         * 기준 시점은 오늘이 아니라 <b>기록 저장일</b> 대비다. 과거 기록을 다시 열어도
         * "그날 기준으로 오늘 피부였나"가 그대로다 — 시간이 흐른다고 답이 변하면
         * 같은 기록이 열 때마다 다른 라벨을 단다.
         */
        SkinBasis skinBasis,
        LocalDate skinMeasuredAt,

        int plateScore,
        /**
         * {@code plateScore} 의 등급. <b>앱이 점수에서 등급을 다시 내지 않게 하려고 싣는다.</b>
         * 경계표는 {@link SkinLevel} 한 곳뿐이어야 한다 — 두 벌이면 서버가 경계를 옮긴 날
         * 같은 68점이 화면마다 다른 등급으로 뜬다.
         */
        SkinLevel grade,
        int baseScore,        // 항상 RuleConstants.BASE_SCORE(70). 계산 내역 카드 첫 줄
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules,

        /*
         * "AI 맞춤 TIP". 저장 시 1회 생성된 문장이고, 실패했으면 null 이라
         * non_null 직렬화로 키가 아예 빠진다 — 앱은 키가 없으면 카드를 숨긴다.
         */
        String aiTip,
        LocalDateTime createdAt
) {
    /**
     * appliedRules는 엔티티에 JSON 문자열로 저장돼 있으므로
     * Service에서 ObjectMapper로 파싱한 결과를 넘겨받는다.
     * DTO가 ObjectMapper를 들고 있지 않게 하기 위한 선택이다.
     */
    public static SkinPlateResponse from(SkinPlate entity, List<String> appliedRules) {
        LocalDateTime skinMeasuredAt = entity.getSkinAnalysis().getCreatedAt();

        return new SkinPlateResponse(
                entity.getId(),
                entity.getSkinAnalysis().getId(),
                SkinBasis.of(skinMeasuredAt,
                        entity.getCreatedAt() == null ? null : entity.getCreatedAt().toLocalDate()),
                skinMeasuredAt == null ? null : skinMeasuredAt.toLocalDate(),
                entity.getPlateScore(),
                SkinLevel.of(entity.getPlateScore()),
                RuleConstants.BASE_SCORE,
                entity.getSummary(),
                FoodAnalysisDto.from(entity.getFoodAnalysis()),
                FeedbackGroupDto.from(entity.getFeedbacks()),
                appliedRules,
                entity.getAiTip(),
                entity.getCreatedAt());
    }
}
