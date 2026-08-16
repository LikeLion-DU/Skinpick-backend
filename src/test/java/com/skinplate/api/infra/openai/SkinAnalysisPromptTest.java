package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.prompt.SkinAnalysisPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스키마가 잘못되면 400 이 오고, 그 400 은 실기기에서 처음 보인다 — 유료 호출이
 * 나가기도 전에 실패하므로 로그에도 "분석에 실패했습니다" 한 줄만 남는다.
 * strict 모드가 요구하는 두 가지(전 필드 required · additionalProperties:false)를
 * 여기서 구조적으로 확인한다.
 */
class SkinAnalysisPromptTest {

    @Test
    @DisplayName("모든 object 가 전 필드 required 이고 additionalProperties:false 다 — strict 의 조건")
    @SuppressWarnings("unchecked")
    void everyObjectSatisfiesStrictMode() {
        Deque<Map<String, Object>> queue = new ArrayDeque<>();
        queue.add(SkinAnalysisPrompt.SCHEMA);

        int objects = 0;
        while (!queue.isEmpty()) {
            Map<String, Object> node = queue.poll();

            if ("object".equals(node.get("type"))) {
                objects++;
                Map<String, Object> properties = (Map<String, Object>) node.get("properties");

                assertThat(node.get("additionalProperties")).as("additionalProperties").isEqualTo(false);
                assertThat((List<String>) node.get("required"))
                        .as("required 는 properties 전부를 담아야 한다")
                        .containsExactlyInAnyOrderElementsOf(properties.keySet());

                properties.values().forEach(child -> queue.add((Map<String, Object>) child));
            }
            if (node.get("items") instanceof Map<?, ?> items) {
                queue.add((Map<String, Object>) items);
            }
        }

        // 루트 + metricEvidence + skinType + skinAgeAnalysis + 나이 축 8개
        assertThat(objects).isEqualTo(12);
    }

    @Test
    @DisplayName("스키마 필드와 DTO 컴포넌트가 1:1 이다 — 하나만 어긋나도 그 필드가 통째로 null 이 된다")
    @SuppressWarnings("unchecked")
    void schemaMatchesResultRecord() {
        Map<String, Object> properties = (Map<String, Object>) SkinAnalysisPrompt.SCHEMA.get("properties");

        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(componentsOf(OpenAiSkinResult.class));

        Map<String, Object> age = (Map<String, Object>) properties.get("skinAgeAnalysis");
        assertThat(((Map<String, Object>) age.get("properties")).keySet())
                .containsExactlyInAnyOrderElementsOf(componentsOf(OpenAiSkinResult.SkinAgeAnalysis.class));

        Map<String, Object> evidence = (Map<String, Object>) properties.get("metricEvidence");
        assertThat(((Map<String, Object>) evidence.get("properties")).keySet())
                .containsExactlyInAnyOrderElementsOf(componentsOf(OpenAiSkinResult.MetricEvidence.class));
    }

    @Test
    @DisplayName("traits enum 은 SkinTrait 와 같은 목록이다 — 어긋나면 그 값만 조용히 버려진다")
    @SuppressWarnings("unchecked")
    void traitEnumMatchesDomain() {
        Map<String, Object> properties = (Map<String, Object>) SkinAnalysisPrompt.SCHEMA.get("properties");
        Map<String, Object> skinType = (Map<String, Object>) properties.get("skinType");
        Map<String, Object> traits = (Map<String, Object>) ((Map<String, Object>) skinType.get("properties")).get("traits");
        Map<String, Object> items = (Map<String, Object>) traits.get("items");

        assertThat((List<String>) items.get("enum"))
                .containsExactlyInAnyOrder(Arrays.stream(SkinTrait.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    @DisplayName("프롬프트가 나이 8축과 등급 없음을 말한다 — 축 이름이 빠지면 그 축만 조용히 비어 온다")
    void systemDescribesEveryAgeAxis() {
        assertThat(SkinAnalysisPrompt.SYSTEM).contains(
                "skinTexture", "elasticity", "wrinkles", "skinTone",
                "pores", "pigmentation", "redness", "blemishMarks",
                "estimatedSkinAge", "ageAssessment", "metricEvidence");

        // 등급은 Backend 가 만든다. 프롬프트가 level 을 요구하면 스키마와 어긋난다.
        assertThat(SkinAnalysisPrompt.SYSTEM).doesNotContain("level");
    }

    private static List<String> componentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
