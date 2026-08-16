package com.skinplate.api.infra.openai.dto;

import java.util.List;

/**
 * OpenAI Structured Outputs(json_schema) 응답을 그대로 받는 DTO.
 * 필드명이 {@code SkinAnalysisPrompt.SCHEMA} 와 1:1로 일치해야 한다.
 *
 * 확장 필드(metricEvidence · skinType · skinAgeAnalysis)는 <b>null 일 수 있다.</b>
 * 이 필드들이 생기기 전에 저장된 raw_ai_response 를 다시 읽을 때 그렇다 —
 * 그 행도 점수·지표·뱃지는 그대로 나와야 한다.
 */
public record OpenAiSkinResult(
        boolean faceDetected,
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier,
        MetricEvidence metricEvidence,
        SkinTypeResult skinType,
        SkinAgeAnalysis skinAgeAnalysis,
        String summary
) {
    /** 지표별 관찰 근거. 지표당 최대 2개 — 개수는 Backend 가 자른다. */
    public record MetricEvidence(List<String> hydration,
                                 List<String> oil,
                                 List<String> redness,
                                 List<String> trouble,
                                 List<String> barrier) {}

    /**
     * enum 이 아니라 String 으로 받는다. 스키마가 값을 강제하지만 그건 OpenAI 쪽 약속이고,
     * 모르는 값 하나에 역직렬화가 통째로 실패하면 25초짜리 유료 호출이 날아간다.
     */
    public record SkinTypeResult(String primary, List<String> traits) {}

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
