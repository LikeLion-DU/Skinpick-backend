package com.skinplate.api.infra.openai.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 음식 분석 프롬프트와 스키마. (PRD §17.3)
 *
 * ingredients[].tag enum 이 Rule Engine 의 인터페이스다.
 * IngredientTag / CookingMethod 를 바꾸면 이 스키마도 같이 바꿔야 한다.
 */
public final class FoodAnalysisPrompt {

    private FoodAnalysisPrompt() {}

    public static final String SYSTEM = """
            당신은 음식 이미지 분석 어시스턴트입니다.
            음식 사진을 보고 음식 종류·주요 재료·영양 정보를 판단하세요.
            
            규칙
            1. 반드시 주어진 JSON 스키마로만 응답한다.
            2. 재료의 tag 는 스키마에 정의된 값 중에서만 고른다. 애매하면 ETC 를 쓴다.
            3. 음식이 인식되지 않으면 foodDetected 를 false 로 한다.
            4. 영양 정보는 1인분 기준으로 추정한다.""";

    public static final String USER = "이 음식 사진을 분석해 주세요.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "foodDetected":  { "type": "boolean" },
                "foodName":      { "type": "string" },
                "foodCategory":  { "type": "string" },
                "cookingMethod": { "type": "string",
                                   "enum": ["FRIED","BOILED","GRILLED","RAW","STEAMED","ETC"] },
                "spicy":         { "type": "boolean" },
                "ingredients": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "tag":  { "type": "string",
                                "enum": ["VITAMIN_C","VITAMIN_A","OMEGA3","ANTIOXIDANT",
                                         "PROBIOTIC","DAIRY","GLUTEN","CAPSAICIN",
                                         "CAFFEINE","ALCOHOL","HIGH_GI","ETC"] }
                    },
                    "required": ["name","tag"],
                    "additionalProperties": false
                  }
                },
                "nutrition": {
                  "type": "object",
                  "properties": {
                    "caloriesKcal": { "type": "integer" },
                    "proteinG":     { "type": "number" },
                    "fatG":         { "type": "number" },
                    "carbG":        { "type": "number" },
                    "sodiumMg":     { "type": "integer" },
                    "sugarG":       { "type": "number" }
                  },
                  "required": ["caloriesKcal","proteinG","fatG","carbG","sodiumMg","sugarG"],
                  "additionalProperties": false
                }
              },
              "required": ["foodDetected","foodName","foodCategory","cookingMethod",
                           "spicy","ingredients","nutrition"],
              "additionalProperties": false
            }""";

    /** Structured Outputs 의 json_schema 에 그대로 실린다. */
    public static final Map<String, Object> SCHEMA = load();

    private static Map<String, Object> load() {
        try {
            return new ObjectMapper().readValue(SCHEMA_JSON, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("FoodAnalysisPrompt 의 JSON Schema 가 올바르지 않다", e);
        }
    }
}
