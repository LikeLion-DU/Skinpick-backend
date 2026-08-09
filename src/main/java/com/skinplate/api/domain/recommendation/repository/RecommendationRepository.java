package com.skinplate.api.domain.recommendation.repository;

import com.skinplate.api.domain.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findBySkinAnalysisIdAndUserIdOrderByDisplayOrderAsc(
            Long skinAnalysisId, Long userId);

    boolean existsBySkinAnalysisId(Long skinAnalysisId);
}
