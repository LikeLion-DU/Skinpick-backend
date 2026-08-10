package com.skinplate.api.infra.openai.dto;

/**
 * OpenAI Structured Outputs(json_schema) 응답을 그대로 받는 DTO.
 * 필드명이 skin-analysis-schema.json과 1:1로 일치해야 한다.
 */
public record OpenAiSkinResult(
        boolean faceDetected,
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier,
        String summary
) {}
