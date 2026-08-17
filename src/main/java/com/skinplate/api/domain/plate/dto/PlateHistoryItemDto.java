package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.MealType;
import com.skinplate.api.domain.plate.entity.SkinPlate;

import java.time.LocalDateTime;

/**
 * 기록 카드 한 줄. 히스토리와 일일 리포트가 같은 것을 그리므로 DTO 도 하나다.
 *
 * @param mealType 저장 시각에서 파생한 끼니. 앱이 시각을 보고 다시 계산하지 않도록
 *                 서버가 정해서 보낸다 — 양쪽이 각자 계산하면 경계 시각에서 갈린다
 */
public record PlateHistoryItemDto(Long plateId, String foodName, int plateScore,
                                  MealType mealType, LocalDateTime recordedAt) {

    /** 반드시 {@code @Transactional(readOnly = true)} 안에서 부른다 — foodAnalysis 가 LAZY 다. */
    public static PlateHistoryItemDto from(SkinPlate plate) {
        return new PlateHistoryItemDto(plate.getId(),
                plate.getFoodAnalysis().getFoodName(),
                plate.getPlateScore(),
                MealType.from(plate.getCreatedAt()),
                plate.getCreatedAt());
    }
}
