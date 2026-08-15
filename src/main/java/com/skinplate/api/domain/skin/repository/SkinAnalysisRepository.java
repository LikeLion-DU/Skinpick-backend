package com.skinplate.api.domain.skin.repository;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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
     * 인사이트의 "직전 분석 대비 변화량"용. createdAt 이 아니라 id 로 줄을 세운다 —
     * 같은 초에 두 건이 들어오면 createdAt 정렬은 순서가 흔들리고, 그러면 같은 분석의
     * 변화량이 조회할 때마다 달라진다. id 는 단조 증가라 그럴 일이 없다.
     */
    Optional<SkinAnalysis> findTopByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id);

    /** 리포트 추이용. 이름에 UserId 가 들어가 조건을 빠뜨릴 수 없다. */
    List<SkinAnalysis> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long userId, LocalDateTime from, LocalDateTime toExclusive);

    /**
     * 추천 lazy 생성이 겹치지 않도록 이 분석 행에 줄을 세운다.
     * (RecommendationService.getOrCreate — 소유 확인은 이미 끝난 뒤에 부른다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SkinAnalysis a where a.id = :id")
    Optional<SkinAnalysis> findForUpdate(@Param("id") Long id);
}
