package com.skinplate.api.domain.food.repository;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodAnalysisRepository extends JpaRepository<FoodAnalysis, Long> {

    Optional<FoodAnalysis> findByIdAndUserId(Long id, Long userId);
}
