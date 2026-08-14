package com.skinplate.api.domain.plate.dto;

import java.util.List;

/** Plate 가 하나도 없는 날은 days 에 넣지 않는다. 히스토리는 식단 기록이다. */
public record PlateHistoryResponse(List<PlateHistoryDayDto> days) {}
