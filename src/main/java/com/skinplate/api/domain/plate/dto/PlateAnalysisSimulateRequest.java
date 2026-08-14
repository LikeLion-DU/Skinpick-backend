package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * POST /plates/simulate 의 요청. 저장 전 시뮬레이션이라 plateId 대신 analysisToken 이
 * 대상을 지목한다 — /plates/records 와 나란한 형태다.
 */
public record PlateAnalysisSimulateRequest(
        @NotBlank(message = "분석 토큰이 필요합니다.") String analysisToken,

        /*
         * 원소에도 @NotNull 이 필요하다. {"actions":[null]} 은 크기가 1 이라
         * @NotEmpty 를 통과하고, 그 null 이 라벨을 읽는 자리에서 NPE 로 터져
         * 400 이어야 할 요청이 500(INTERNAL_ERROR)으로 나간다.
         */
        @NotEmpty(message = "실행할 행동을 하나 이상 선택해 주세요.")
        List<@NotNull(message = "알 수 없는 행동이 포함돼 있습니다.") PlateActionCode> actions
) {}
