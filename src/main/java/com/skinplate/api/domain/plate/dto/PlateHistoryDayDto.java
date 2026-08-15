package com.skinplate.api.domain.plate.dto;

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
 */
public record PlateHistoryDayDto(LocalDate date, Integer skinScore,
                                 int plateScore, int targetScore,
                                 List<PlateHistoryItemDto> plates) {}
