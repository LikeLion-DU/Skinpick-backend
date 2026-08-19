package com.skinplate.api.domain.report.dto;

import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * 일일 리포트 화면 한 벌. 한 번의 호출로 화면이 완성된다.
 *
 * <p>여기 있는 숫자는 전부 <b>저장된 기록에서 다시 센 값</b>이다. 새 점수 규칙도,
 * AI 가 정한 숫자도 없다 — {@code aiComment} 하나만 AI 가 쓴 문장이고 그것도
 * 기록 저장 시점에 이미 만들어져 있던 것이다.
 *
 * <p>null 인 필드는 응답에서 키가 통째로 빠진다(default-property-inclusion: non_null).
 *
 * @param dailyScore    그날 기록들의 평균 식단 점수. <b>기록이 없으면 null</b> —
 *                      0 을 내려보내면 화면이 "0점"으로 그린다
 * @param nutrition     기록된 끼니의 영양 합계. 항목 순서는 {@link NutrientType} 선언 순서다
 * @param skinNutrients 피부 영양 포인트 3종(비타민C·오메가3·아연). 시안이 영양 밸런스와
 *                      <b>다른 카드</b>로 그리고, 측정 가능 여부도 다르다 — 표준 음식표에
 *                      매칭된 끼니에서만 값이 나오므로 {@code status} 가 null 일 수 있다.
 *                      {@link NutritionItemDto} 와 같은 모양이라 앱은 같은 위젯으로 그린다
 * @param concerns      사용자가 고른 고민에 한해서만 채운다. 안 고른 사람은 빈 배열
 * @param meals         히스토리와 같은 모양을 쓴다 — 같은 카드를 두 화면이 그린다
 * @param aiComment     저장된 "오늘의 AI 코멘트". 리포트를 열 때 새로 만들지 않는다
 * @param goodPoints    그날 기록에 가장 자주 붙은 좋은 점 문구 (최대 3)
 * @param improvePoints 그날 기록에 가장 자주 붙은 주의 문구 (최대 3)
 */
public record DailyReportResponse(
        LocalDate date,
        Integer dailyScore,
        SkinLevel grade,
        int recordCount,
        List<NutritionItemDto> nutrition,
        List<NutritionItemDto> skinNutrients,
        List<ConcernScoreDto> concerns,
        List<PlateHistoryItemDto> meals,
        String aiComment,
        List<String> goodPoints,
        List<String> improvePoints
) {
    /** 기록이 하나도 없는 날. 빈 배열과 null 로만 이루어진다. */
    public static DailyReportResponse empty(LocalDate date) {
        return new DailyReportResponse(date, null, null, 0,
                List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of());
    }
}
