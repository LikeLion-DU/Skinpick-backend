package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.service.FoodHighlightTags;
import com.skinplate.api.domain.plate.entity.MealType;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.plate.entity.SkinPlate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기록 카드 한 줄. 히스토리와 일일 리포트가 같은 것을 그리므로 DTO 도 하나다.
 *
 * @param mealType      저장 시각에서 파생한 끼니. 앱이 시각을 보고 다시 계산하지 않도록
 *                      서버가 정해서 보낸다 — 양쪽이 각자 계산하면 경계 시각에서 갈린다
 * @param highlightTags 이 끼니에서 눈에 띄는 항목 두세 개("나트륨" · "단백질").
 *                      {@link com.skinplate.api.domain.food.service.FoodHighlightTags} 가
 *                      <b>기존 임계값만 되읽어</b> 고른다 — 앱이 고르려면 목록에 영양값
 *                      전체를 실어야 하고 그러면 앱 안에 기준이 또 한 벌 생긴다.
 *                      걸리는 항목이 없으면 빈 배열이고 앱은 칩 줄을 안 그린다
 */
public record PlateHistoryItemDto(Long plateId, String foodName, int plateScore,
                                  SkinLevel grade,
                                  MealType mealType, LocalDateTime recordedAt,
                                  List<String> highlightTags) {

    /** 반드시 {@code @Transactional(readOnly = true)} 안에서 부른다 — foodAnalysis 가 LAZY 다. */
    public static PlateHistoryItemDto from(SkinPlate plate) {
        return new PlateHistoryItemDto(plate.getId(),
                plate.getFoodAnalysis().getFoodName(),
                plate.getPlateScore(),
                // 등급을 함께 싣는다 — 앱이 점수에서 다시 내면 경계표가 두 벌이 된다.
                SkinLevel.of(plate.getPlateScore()),
                MealType.from(plate.getCreatedAt()),
                plate.getCreatedAt(),
                FoodHighlightTags.of(plate.getFoodAnalysis()));
    }
}
