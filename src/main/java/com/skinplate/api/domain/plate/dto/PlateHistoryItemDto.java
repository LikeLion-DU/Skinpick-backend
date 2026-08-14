package com.skinplate.api.domain.plate.dto;

import java.time.LocalDateTime;

public record PlateHistoryItemDto(Long plateId, String foodName, int plateScore,
                                  LocalDateTime recordedAt) {}
