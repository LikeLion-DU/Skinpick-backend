package com.skinplate.api.domain.recommendation.dto;

import com.skinplate.api.domain.recommendation.entity.Recommendation;

public record RecommendedFoodDto(String foodName, String reason) {

    public static RecommendedFoodDto from(Recommendation recommendation) {
        return new RecommendedFoodDto(recommendation.getFoodName(), recommendation.getReason());
    }
}
