package com.skinplate.api.domain.recommendation.dto;

import com.skinplate.api.domain.recommendation.entity.Recommendation;
import com.skinplate.api.domain.recommendation.entity.RecommendationType;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(
        Long skinAnalysisId,
        List<RecommendedFoodDto> recommend,
        List<RecommendedFoodDto> avoid,
        LocalDateTime generatedAt
) {
    public static RecommendationResponse from(Long skinAnalysisId, List<Recommendation> all) {
        return new RecommendationResponse(
                skinAnalysisId,
                filter(all, RecommendationType.RECOMMEND),
                filter(all, RecommendationType.AVOID),
                all.isEmpty() ? null : all.get(0).getCreatedAt());
    }

    private static List<RecommendedFoodDto> filter(List<Recommendation> all, RecommendationType type) {
        return all.stream()
                .filter(recommendation -> recommendation.getType() == type)
                .map(RecommendedFoodDto::from)
                .toList();
    }
}
