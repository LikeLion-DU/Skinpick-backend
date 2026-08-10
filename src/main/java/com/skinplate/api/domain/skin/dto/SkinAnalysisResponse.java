package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;

import java.time.LocalDateTime;
import java.util.List;

public record SkinAnalysisResponse(
        Long skinAnalysisId,
        int skinScore,
        SkinMetricsDto metrics,
        String summary,
        List<HighlightDto> highlights,
        SkinTypeGapDto skinTypeGap,       // 선언 타입이 없으면 null → 키 생략
        LocalDateTime analyzedAt
) {
    public static SkinAnalysisResponse from(SkinAnalysis entity,
                                            List<HighlightDto> highlights,
                                            SkinTypeGapDto skinTypeGap) {
        return new SkinAnalysisResponse(
                entity.getId(),
                entity.getSkinScore(),
                SkinMetricsDto.from(entity.getMetrics()),
                entity.getSummary(),
                highlights,
                skinTypeGap,
                entity.getCreatedAt());
    }
}
