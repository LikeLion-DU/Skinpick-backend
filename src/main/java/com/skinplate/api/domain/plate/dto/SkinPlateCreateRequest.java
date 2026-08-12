package com.skinplate.api.domain.plate.dto;

/**
 * multipart 요청의 image 외 파트.
 * skinAnalysisId가 null이면 Service가 해당 사용자의 최신 피부 분석을 사용한다.
 */
public record SkinPlateCreateRequest(Long skinAnalysisId) {}
