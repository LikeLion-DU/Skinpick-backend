package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param metrics       5개 지표의 원값. <b>기존 계약 그대로 둔다</b> — S05 의 지표 바가 이걸 읽는다
 * @param metricDetails 같은 5개에 등급과 관찰 근거를 붙인 것. 확장 필드라
 *                      이 기능 이전에 저장된 분석이면 근거가 빈 배열이다
 * @param skinType      규칙으로 도출한 오늘의 타입과 상태. primary 는 skinTypeGap.observed 와
 *                      항상 같다 — 지표에서 다시 만들어지므로 예전 분석에도 나온다
 * @param skinAge       AI 추정 피부 나이. 예전 분석이면 null → 키 생략
 * @param careFocus     "지금 피부가 필요로 하는 관리" 축. 지표에서 규칙으로 도출하므로
 *                      <b>예전 분석에도 나온다</b> — highlights 와 같은 성격이다.
 *                      최소 하나는 온다(해당 축이 없으면 "지금 균형 유지")
 * @param careMessage   위 축들의 권고를 이어 붙인 문단. AI 문장이 아니다 —
 *                      {@code summary}(AI 가 관찰한 것)와 다른 것을 말한다
 */
public record SkinAnalysisResponse(
        Long skinAnalysisId,
        int skinScore,

        /**
         * {@code skinScore} 의 등급. 리포트의 {@code grade} 와 같은 표를 지난다 —
         * 앱이 점수에서 다시 내면 경계표가 두 벌이 된다.
         */
        SkinLevel grade,
        SkinMetricsDto metrics,
        List<ScoredItemDto> metricDetails,
        SkinTypeDto skinType,             // 지표에서 규칙으로 도출 — 항상 채워진다
        SkinAgeDto skinAge,               // 예전 분석이면 null → 키 생략
        String summary,
        List<HighlightDto> highlights,
        List<CareFocusDto> careFocus,     // 지표에서 규칙 도출 — 항상 채워진다
        String careMessage,
        SkinTypeGapDto skinTypeGap,       // 선언 타입이 없으면 null → 키 생략
        LocalDateTime analyzedAt
) {
    public static SkinAnalysisResponse from(SkinAnalysis entity,
                                            List<ScoredItemDto> metricDetails,
                                            SkinTypeDto skinType,
                                            SkinAgeDto skinAge,
                                            List<HighlightDto> highlights,
                                            List<CareFocusDto> careFocus,
                                            String careMessage,
                                            SkinTypeGapDto skinTypeGap) {
        return new SkinAnalysisResponse(
                entity.getId(),
                entity.getSkinScore(),
                SkinLevel.of(entity.getSkinScore()),
                SkinMetricsDto.from(entity.getMetrics()),
                metricDetails,
                skinType,
                skinAge,
                entity.getSummary(),
                highlights,
                careFocus,
                careMessage,
                skinTypeGap,
                entity.getCreatedAt());
    }
}
