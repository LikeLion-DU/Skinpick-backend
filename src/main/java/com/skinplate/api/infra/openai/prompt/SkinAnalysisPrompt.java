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

    /**
     * 이 문구 뒤에 {@link com.skinplate.api.infra.openai.dto.FacePhotoType} 의 라벨과
     * 사진 세 장이 이어 붙는다. 라벨 문구는 그 enum 이 소유한다 — 여기 다시 적으면
     * 두 곳이 어긋난 채로 컴파일된다.
     *
     * SYSTEM 의 지표 정의와 판정 규칙은 손대지 않았다 — 입력이 세 장으로 늘었을 뿐
     * 무엇을 어떻게 평가하는지는 그대로다. 여기서 기준을 흔들면 과거 분석과
     * 오늘 분석이 다른 잣대로 매겨진다.
     */
    public static final String USER = """
            같은 사람의 얼굴을 세 각도에서 찍은 사진 3장이 이어서 제공됩니다.
            각 사진 바로 앞에 촬영 방향 라벨을 적어 두었습니다.

            세 장을 함께 보고 이 사람의 피부 상태를 하나의 결과로 평가해 주세요.
            사진마다 따로 점수를 매겨 평균 내지 말고, 세 각도에서 관찰된 것을 종합하세요.
            각도와 조명 차이로 생긴 차이는 같은 사람의 촬영 조건 차이로 봅니다.
            세 장 중 얼굴이 보이지 않는 사진이 하나라도 있으면 faceDetected 를 false 로 합니다.""";

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
