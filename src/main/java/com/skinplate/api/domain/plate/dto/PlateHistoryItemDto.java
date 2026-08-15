package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.MealType;

import java.time.LocalDateTime;

/**
 * @param mealType 저장 시각에서 파생한 끼니. 앱이 시각을 보고 다시 계산하지 않도록
 *                 서버가 정해서 보낸다 — 양쪽이 각자 계산하면 경계 시각에서 갈린다
 */
public record PlateHistoryItemDto(Long plateId, String foodName, int plateScore,
                                  MealType mealType, LocalDateTime recordedAt) {}
