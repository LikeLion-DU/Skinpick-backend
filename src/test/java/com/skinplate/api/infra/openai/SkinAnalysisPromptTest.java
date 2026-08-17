package com.skinplate.api.infra.openai;

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

        // 루트 + metricEvidence + skinAgeAnalysis + 나이 축 8개
        assertThat(objects).isEqualTo(11);
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
    @DisplayName("스키마에 피부 타입이 없다 — AI 가 타입을 내면 판정의 출처가 다시 둘이 된다")
    @SuppressWarnings("unchecked")
    void schemaDoesNotAskForASkinType() {
        Map<String, Object> properties = (Map<String, Object>) SkinAnalysisPrompt.SCHEMA.get("properties");

        assertThat(properties).doesNotContainKey("skinType");
        assertThat(SkinAnalysisPrompt.SYSTEM).contains("피부 타입(건성·지성·복합성·보통)은 판단하지 않습니다");
    }

    @Test
    @DisplayName("프롬프트가 유분의 두 단계를 말한다 — 이게 없으면 T존 복합성과 전면 지성이 같은 값으로 온다")
    void systemSeparatesRegionalOilFromOverallOil() {
        // 백엔드는 유분 60~70 을 복합성, 70 초과를 지성으로 읽는다(SkinType.observe).
        // 프롬프트가 그 구분을 지시하지 않으면 두 사람이 같은 숫자를 받는다.
        assertThat(SkinAnalysisPrompt.SYSTEM).contains("T존에만 유분이 몰리고", "얼굴 전반이 고르게 번들거리면");
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
