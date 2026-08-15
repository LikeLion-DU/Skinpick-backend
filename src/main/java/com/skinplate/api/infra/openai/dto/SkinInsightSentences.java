package com.skinplate.api.infra.openai.dto;

import java.util.List;

/**
 * 인사이트 문장 생성 호출의 응답. 숫자·우선순위 필드가 없는 것이 의도다 —
 * 주제도 순서도 이미 정해져 있고, AI 는 category 마다 문장 하나씩만 채운다.
 */
public record SkinInsightSentences(String summary, List<Topic> topics) {

    public record Topic(String category, String description) {}
}
