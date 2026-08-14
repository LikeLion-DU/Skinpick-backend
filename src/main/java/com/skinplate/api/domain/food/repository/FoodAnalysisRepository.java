package com.skinplate.api.domain.food.repository;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FoodAnalysisRepository extends JpaRepository<FoodAnalysis, Long> {

    Optional<FoodAnalysis> findByIdAndUserId(Long id, Long userId);

    /**
     * 기록 저장의 멱등키. 새 컬럼·테이블 없이 raw_ai_response(jsonb) 안 _meta.jti 로
     * 찾는다 — jsonb 연산자는 JPQL 이 지원하지 않아 네이티브 쿼리다.
     * idx_food_analysis_user_created 가 사용자 범위를 좁혀, 사용자당 수십~수백
     * 행을 순차 스캔하는 정도로 충분하다(별도 인덱스 없음).
     */
    @Query(value = "select f.id from food_analysis f "
                 + "where f.user_id = :userId and f.raw_ai_response->'_meta'->>'jti' = :jti "
                 + "limit 1", nativeQuery = true)
    Optional<Long> findIdByUserIdAndJti(@Param("userId") Long userId, @Param("jti") String jti);
}
