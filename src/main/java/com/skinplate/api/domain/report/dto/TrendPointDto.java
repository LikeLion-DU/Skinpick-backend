package com.skinplate.api.domain.report.dto;

import java.time.LocalDate;

/** 추이 그래프의 점 하나. 기록이 없는 날은 아예 배열에 넣지 않는다. */
public record TrendPointDto(LocalDate date, int score) {}
