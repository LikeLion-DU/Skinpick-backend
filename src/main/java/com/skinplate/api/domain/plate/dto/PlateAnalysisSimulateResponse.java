package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;

import java.util.List;

/**
 * POST /plates/simulate 의 응답. 기존 PlateSimulateResponse 에서 plateId 만 뺀 형태다 —
 * PlateAnalysisResponse 와 같은 이유로, 저장 전인데 id 가 내려가면 앱이 저장된 것으로
 * 오해한다.
 */
public record PlateAnalysisSimulateResponse(
        int beforeScore,
        int afterScore,
        List<String> appliedActions,
        List<String> removedRules,     // 행동으로 사라진 감점 룰
        String summary
) {
    public static PlateAnalysisSimulateResponse of(int before, int after,
                                                    List<PlateActionCode> actions,
                                                    List<String> removedRules,
                                                    String summary) {
        return new PlateAnalysisSimulateResponse(
                before, after,
                actions.stream().map(Enum::name).toList(),
                removedRules, summary);
    }
}
