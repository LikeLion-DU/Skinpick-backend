package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlateSimulateRequest(
        /*
         * 원소에도 @NotNull 이 필요하다. {"actions":[null]} 은 크기가 1 이라
         * @NotEmpty 를 통과하고, 그 null 이 라벨을 읽는 자리에서 NPE 로 터져
         * 400 이어야 할 요청이 500(INTERNAL_ERROR)으로 나간다.
         */
        @NotEmpty(message = "실행할 행동을 하나 이상 선택해 주세요.")
        List<@NotNull(message = "알 수 없는 행동이 포함돼 있습니다.") PlateActionCode> actions
) {}
