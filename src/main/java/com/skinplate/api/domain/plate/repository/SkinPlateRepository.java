package com.skinplate.api.domain.plate.repository;

import com.skinplate.api.domain.plate.entity.SkinPlate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SkinPlateRepository extends JpaRepository<SkinPlate, Long> {

    Optional<SkinPlate> findByIdAndUserId(Long id, Long userId);

    /**
     * 리포트·히스토리용 기간 조회. userId 조건이 빠지면 남의 기록이 섞이는데,
     * 단일 사용자 개발 DB 에서는 그대로 통과한다. 조건을 쿼리에 박아 둔다.
     *
     * feedbacks 는 감점 집계에 바로 쓰이므로 같이 읽는다. batch_fetch_size 가
     * 이미 N+1 을 한 번의 추가 쿼리로 접지만, 그 왕복 하나를 더 아낀다.
     */
    @EntityGraph(attributePaths = {"feedbacks", "foodAnalysis", "skinAnalysis"})
    @Query("select p from SkinPlate p "
         + "where p.user.id = :userId and p.createdAt >= :from and p.createdAt < :toExclusive "
         + "order by p.createdAt desc")
    List<SkinPlate> findInRange(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from,
                                @Param("toExclusive") LocalDateTime toExclusive);
}
