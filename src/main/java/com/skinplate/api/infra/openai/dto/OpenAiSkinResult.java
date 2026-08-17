package com.skinplate.api.infra.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OpenAI Structured Outputs(json_schema) 응답을 그대로 받는 DTO.
 * 필드명이 {@code SkinAnalysisPrompt.SCHEMA} 와 1:1로 일치해야 한다.
 *
 * 확장 필드(metricEvidence · skinAgeAnalysis)는 <b>null 일 수 있다.</b>
 * 이 필드들이 생기기 전에 저장된 raw_ai_response 를 다시 읽을 때 그렇다 —
 * 그 행도 점수·지표·뱃지는 그대로 나와야 한다.
 *
 * {@code ignoreUnknown} 은 반대 방향을 막는다. 스키마에서 뺀 필드(예전의 {@code skinType})가
 * 들어 있는 옛 행을 읽을 때 여기서 터지면 {@code parseDetail} 이 통째로 null 을 돌려주고,
 * 남아 있는 근거·피부 나이까지 같이 사라진다. 전역 Jackson 설정에 기대지 않고 여기서 못 박는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiSkinResult(
        boolean faceDetected,
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier,
        MetricEvidence metricEvidence,
        SkinAgeAnalysis skinAgeAnalysis,
        String summary
) {
    /** 지표별 관찰 근거. 지표당 최대 2개 — 개수와 길이는 Backend 가 자른다. */
    public record MetricEvidence(List<String> hydration,
                                 List<String> oil,
                                 List<String> redness,
                                 List<String> trouble,
                                 List<String> barrier) {

        /** 확장 필드가 없던 기록을 읽을 때 쓴다. 조회마다 새로 만들 이유가 없다. */
        public static final MetricEvidence EMPTY = new MetricEvidence(null, null, null, null, null);
    }

    /** 나이 축 하나. evidence 는 축당 최대 1개. */
    public record Axis(int score, List<String> evidence) {}

    /**
     * 피부 나이 전용 분석. estimatedSkinAge 는 AI 가 8개 축을 종합해 직접 낸다 —
     * 8개 점수를 나이로 바꾸는 기계적 공식을 두지 않는다.
     */
    public record SkinAgeAnalysis(int estimatedSkinAge,
                                  Axis skinTexture,
                                  Axis elasticity,
                                  Axis wrinkles,
                                  Axis skinTone,
                                  Axis pores,
                                  Axis pigmentation,
                                  Axis redness,
                                  Axis blemishMarks,
                                  String ageAssessment) {}
}
