package com.skinplate.api.domain.skin.repository;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkinAnalysisRepository extends JpaRepository<SkinAnalysis, Long> {

    /**
     * ★ findById 를 쓰지 않는다.
     * id만으로 조회하면 /skin/analyses/1 부터 순서대로 호출해
     * 남의 피부 분석 결과를 전부 읽을 수 있다. 조건 하나로 막힌다.
     */
    Optional<SkinAnalysis> findByIdAndUserId(Long id, Long userId);

    Optional<SkinAnalysis> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
