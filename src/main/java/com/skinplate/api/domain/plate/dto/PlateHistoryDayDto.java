package com.skinplate.api.domain.plate.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param skinScore 그날 최신 분석. 없으면 그날 첫 기록이 채점 기준으로 쓴 분석의 점수.
 *                  skin_plate.skin_analysis_id 가 NOT NULL 이라 기록이 있으면 반드시 있다
 */
public record PlateHistoryDayDto(LocalDate date, Integer skinScore,
                                 List<PlateHistoryItemDto> plates) {}
