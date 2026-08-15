package com.skinplate.api.domain.insight.repository;

import com.skinplate.api.domain.insight.entity.SkinInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkinInsightRepository extends JpaRepository<SkinInsight, Long> {

    /**
     * 조회는 반드시 userId 를 함께 건다 — 타인의 분석 id 면 404 로 떨어진다.
     *
     * 존재 확인(exists)을 따로 두지 않는다. 락을 잡은 뒤의 재확인은 "있으면 <b>그것을</b>
     * 돌려줘야" 하므로 엔티티가 필요하고, 그러면 exists 는 같은 조건을 두 번 세는 셈이 된다.
     */
    Optional<SkinInsight> findBySkinAnalysisIdAndUserId(Long skinAnalysisId, Long userId);
}
