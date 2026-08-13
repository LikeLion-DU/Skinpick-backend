package com.skinplate.api.domain.skin.repository;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SkinAnalysisRepository extends JpaRepository<SkinAnalysis, Long> {

    /**
     * ★ findById 를 쓰지 않는다.
     * id만으로 조회하면 /skin/analyses/1 부터 순서대로 호출해
     * 남의 피부 분석 결과를 전부 읽을 수 있다. 조건 하나로 막힌다.
     */
    Optional<SkinAnalysis> findByIdAndUserId(Long id, Long userId);

    Optional<SkinAnalysis> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 추천 lazy 생성이 겹치지 않도록 이 분석 행에 줄을 세운다.
     * (RecommendationService.getOrCreate — 소유 확인은 이미 끝난 뒤에 부른다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SkinAnalysis a where a.id = :id")
    Optional<SkinAnalysis> findForUpdate(@Param("id") Long id);
}
