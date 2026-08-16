package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param metrics       5개 지표의 원값. <b>기존 계약 그대로 둔다</b> — S05 의 지표 바가 이걸 읽는다
 * @param metricDetails 같은 5개에 등급과 관찰 근거를 붙인 것. 확장 필드라
 *                      이 기능 이전에 저장된 분석이면 근거가 빈 배열이다
 * @param skinType      AI 가 읽은 피부 타입. skinTypeGap.observed(규칙 도출)와는 다른 값이다
 * @param skinAge       AI 추정 피부 나이. 예전 분석이면 null → 키 생략
 */
public record SkinAnalysisResponse(
        Long skinAnalysisId,
        int skinScore,
        SkinMetricsDto metrics,
        List<ScoredItemDto> metricDetails,
        SkinTypeDto skinType,             // AI 가 못 냈으면 null → 키 생략
        SkinAgeDto skinAge,               // 예전 분석이면 null → 키 생략
        String summary,
        List<HighlightDto> highlights,
        SkinTypeGapDto skinTypeGap,       // 선언 타입이 없으면 null → 키 생략
        LocalDateTime analyzedAt
) {
    public static SkinAnalysisResponse from(SkinAnalysis entity,
                                            List<ScoredItemDto> metricDetails,
                                            SkinTypeDto skinType,
                                            SkinAgeDto skinAge,
                                            List<HighlightDto> highlights,
                                            SkinTypeGapDto skinTypeGap) {
        return new SkinAnalysisResponse(
                entity.getId(),
                entity.getSkinScore(),
                SkinMetricsDto.from(entity.getMetrics()),
                metricDetails,
                skinType,
                skinAge,
                entity.getSummary(),
                highlights,
                skinTypeGap,
                entity.getCreatedAt());
    }
}
