package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

public record SkinMetricsDto(
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier
) {
    public static SkinMetricsDto from(SkinMetrics metrics) {
        return new SkinMetricsDto(
                metrics.getHydration(),
                metrics.getOil(),
                metrics.getRedness(),
                metrics.getTrouble(),
                metrics.getBarrier());
    }
}
