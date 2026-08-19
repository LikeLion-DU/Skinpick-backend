package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * @param skinScore       그날 최신 분석. 없으면 그날 첫 기록이 채점 기준으로 쓴 분석의 점수.
 *                        skin_plate.skin_analysis_id 가 NOT NULL 이라 기록이 있으면 반드시 있다
 * @param plateScore      그날 기록들의 평균 식단 점수. 시안의 홈이 "오늘의 피부 식단 점수"로
 *                        크게 보여주는 값이다. 앱에서 평균을 내지 않는 이유는 하나다 —
 *                        반올림 방식이 서버와 조금만 달라도 두 화면에 다른 숫자가 뜬다
 * @param targetScore     시안의 "목표 80점". 지금은 모두에게 같은 값이라 서버가 상수로 보낸다.
 *                        앱에 하드코딩하면 나중에 사용자별 목표를 주려 할 때 앱 배포가 필요해진다
 * @param aiComment       "오늘의 AI 코멘트". 그날 최신 기록이 쥔 문장이다(V4 주석 참조).
 *                        null 이면 non_null 직렬화로 키가 빠지고 앱은 카드를 숨긴다
 */
public record PlateHistoryDayDto(LocalDate date, Integer skinScore,
                                 int plateScore, SkinLevel grade, int targetScore,
                                 String aiComment,
                                 List<PlateHistoryItemDto> plates) {

    /**
     * {@code plateScore} 의 등급을 함께 싣는다 — 앱이 점수에서 등급을 다시 내면
     * 경계표가 두 벌이 되고, 서버가 경계를 옮긴 날 한쪽만 따라간다.
     */
    public static PlateHistoryDayDto of(LocalDate date, Integer skinScore, int plateScore,
                                        int targetScore, String aiComment,
                                        List<PlateHistoryItemDto> plates) {
        return new PlateHistoryDayDto(date, skinScore, plateScore, SkinLevel.of(plateScore),
                targetScore, aiComment, plates);
    }
}
