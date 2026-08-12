package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlateSimulateRequest(
        @NotEmpty(message = "실행할 행동을 하나 이상 선택해 주세요.")
        List<PlateActionCode> actions
) {}
