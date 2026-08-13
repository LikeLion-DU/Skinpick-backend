package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;

import java.util.List;

public record PlateSimulateResponse(
        Long plateId,
        int beforeScore,
        int afterScore,
        List<String> appliedActions,
        List<String> removedRules,     // 행동으로 사라진 감점 룰
        String summary
) {
    public static PlateSimulateResponse of(Long plateId, int before, int after,
                                           List<PlateActionCode> actions,
                                           List<String> removedRules,
                                           String summary) {
        return new PlateSimulateResponse(
                plateId, before, after,
                actions.stream().map(Enum::name).toList(),
                removedRules, summary);
    }
}
