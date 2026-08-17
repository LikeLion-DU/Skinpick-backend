package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodGroup;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Oiliness;
import com.skinplate.api.domain.food.entity.PortionSize;
import com.skinplate.api.domain.food.entity.ProcessingLevel;
import com.skinplate.api.domain.food.entity.Spiciness;
import com.skinplate.api.infra.openai.prompt.FoodAnalysisPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스키마의 enum 과 백엔드 enum 이 어긋나면 어긋난 값 전부가 UNKNOWN/ETC 로
 * 흘러 룰이 조용히 안 걸린다 — 아무것도 실패하지 않고 점수만 틀린다.
 * 스키마 파일이 따로 없고 SCHEMA_JSON 이 원본이므로 여기서 값 단위로 잠근다.
 */
class FoodAnalysisPromptTest {

    @Test
    @DisplayName("스키마의 enum 값이 백엔드 enum 과 정확히 일치한다")
    void schemaEnumsMatchBackendEnums() {
        assertThat(enumValuesOf("cookingMethod")).containsExactlyElementsOf(names(CookingMethod.values()));
        assertThat(enumValuesOf("foodGroup")).containsExactlyElementsOf(names(FoodGroup.values()));
        assertThat(enumValuesOf("portionSize")).containsExactlyElementsOf(names(PortionSize.values()));
        assertThat(enumValuesOf("spiciness")).containsExactlyElementsOf(names(Spiciness.values()));
        assertThat(enumValuesOf("oiliness")).containsExactlyElementsOf(names(Oiliness.values()));
        assertThat(enumValuesOf("processingLevel")).containsExactlyElementsOf(names(ProcessingLevel.values()));
        assertThat(ingredientTagEnum()).containsExactlyElementsOf(names(IngredientTag.values()));
    }

    @Test
    @DisplayName("strict Structured Outputs 라 모든 필드가 required 다 — 빠지면 그 필드가 조용히 사라진다")
    void everyPropertyIsRequired() {
        Map<String, Object> properties = section(FoodAnalysisPrompt.SCHEMA, "properties");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) FoodAnalysisPrompt.SCHEMA.get("required");

        assertThat(required).containsExactlyInAnyOrderElementsOf(properties.keySet());
    }

    @Test
    @DisplayName("영양은 1인분 기준으로 못 박는다 — portionSize 가 영양 추정을 흔들면 안 된다")
    void nutritionStaysPerServing() {
        assertThat(FoodAnalysisPrompt.SYSTEM).contains("1인분 기준");
        assertThat(FoodAnalysisPrompt.SYSTEM).contains("확실하지 않으면 UNKNOWN");
    }

    // ---- helpers ----

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> node, String key) {
        return (Map<String, Object>) node.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumValuesOf(String property) {
        Map<String, Object> properties = section(FoodAnalysisPrompt.SCHEMA, "properties");
        return (List<String>) section(properties, property).get("enum");
    }

    @SuppressWarnings("unchecked")
    private static List<String> ingredientTagEnum() {
        Map<String, Object> properties = section(FoodAnalysisPrompt.SCHEMA, "properties");
        Map<String, Object> items = section(section(properties, "ingredients"), "items");
        return (List<String>) section(section(items, "properties"), "tag").get("enum");
    }
}
