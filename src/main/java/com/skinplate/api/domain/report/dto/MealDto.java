package com.skinplate.api.domain.report.dto;

import java.time.LocalDateTime;

/** 오늘 먹은 것 한 줄. TODAY 에서만 채운다. */
public record MealDto(Long plateId, String foodName, int plateScore, LocalDateTime recordedAt) {}
