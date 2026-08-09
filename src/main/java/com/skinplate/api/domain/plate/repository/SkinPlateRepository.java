package com.skinplate.api.domain.plate.repository;

import com.skinplate.api.domain.plate.entity.SkinPlate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkinPlateRepository extends JpaRepository<SkinPlate, Long> {

    Optional<SkinPlate> findByIdAndUserId(Long id, Long userId);

    List<SkinPlate> findByUserIdOrderByCreatedAtDesc(Long userId);
}
