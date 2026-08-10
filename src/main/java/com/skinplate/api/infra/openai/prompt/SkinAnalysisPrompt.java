package com.skinplate.api.infra.openai.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 피부 분석 프롬프트와 스키마. (PRD §17.3)
 *
 * tag/enum 을 바꾸면 도메인 enum 도 같이 바꿔야 한다 —
 * AI 가 자유롭게 값을 만들면 Rule Engine 이 아무것도 매칭하지 못한다.
 */
public final class SkinAnalysisPrompt {

    private SkinAnalysisPrompt() {}

    public static final String SYSTEM = """
            당신은 피부 이미지 분석 어시스턴트입니다.
            얼굴 사진을 보고 아래 5개 지표를 0~100 정수로 평가하세요.
            
            - hydration : 피부 수분감. 높을수록 촉촉함
            - oil       : 유분기. 높을수록 번들거림
            - redness   : 홍조. 높을수록 붉고 자극된 상태
            - trouble   : 여드름/뾰루지/염증. 높을수록 심함
            - barrier   : 피부 장벽 건강. 높을수록 매끄럽고 안정적
            
            규칙
            1. 반드시 주어진 JSON 스키마로만 응답한다.
            2. 의학적 진단이나 질환명을 언급하지 않는다.
            3. 얼굴이 인식되지 않으면 faceDetected를 false로 한다.
            4. summary는 한국어 1문장, 40자 이내로 작성한다.
            5. 판단 근거가 부족한 지표는 50에 가깝게 평가한다.""";

    public static final String USER = "이 얼굴 사진의 피부 상태를 평가해 주세요.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "faceDetected": { "type": "boolean" },
                "hydration": { "type": "integer", "minimum": 0, "maximum": 100 },
                "oil":       { "type": "integer", "minimum": 0, "maximum": 100 },
                "redness":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "trouble":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "barrier":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "summary":   { "type": "string" }
              },
              "required": ["faceDetected","hydration","oil","redness","trouble","barrier","summary"],
              "additionalProperties": false
            }""";

    /** Structured Outputs 의 json_schema 에 그대로 실린다. */
    public static final Map<String, Object> SCHEMA = load();

    private static Map<String, Object> load() {
        try {
            return new ObjectMapper().readValue(SCHEMA_JSON, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("SkinAnalysisPrompt 의 JSON Schema 가 올바르지 않다", e);
        }
    }
}
