package com.skinplate.api.domain.plate.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /plates/records 의 요청. 필드가 토큰 하나뿐인 것이 요구사항이다 —
 * food·nutrition·plateScore 등 클라이언트가 계산한 값을 받는 필드를 두지 않는다.
 * 받지 않으므로 조작할 대상도 없다. 점수는 서버가 토큰의 food 로 다시 계산한다.
 */
public record PlateRecordRequest(
        @NotBlank(message = "분석 토큰이 필요합니다.") String analysisToken
) {}
