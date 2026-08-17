package com.skinplate.api.domain.report.dto;

import com.skinplate.api.infra.openai.dto.WeeklyComment;

/**
 * 주간 리포트의 AI 문장 넷. 숫자 필드가 없는 것이 의도다 — 평균도 BEST DAY 도
 * 서버가 이미 정했고, AI 는 그 결과를 설명하기만 한다.
 *
 * <p>OpenAI 응답 레코드({@link WeeklyComment})를 그대로 내보내지 않고 한 번 옮겨 담는다.
 * {@code SkinInsightResponse} 가 {@code SkinInsightSentences} 를 옮겨 담는 것과 같은 이유다 —
 * 그대로 노출하면 외부 API 스키마에 맞춘 필드명이 곧 앱 계약이 되고, 프롬프트를 손보다
 * 필드 하나를 바꾸는 순간 앱이 조용히 깨진다.
 */
public record WeeklyCommentDto(String goodPoint, String improvePoint,
                               String habit, String nextWeek) {

    /** 생성 실패(null)는 그대로 null 로 흘린다 — 응답에서 키가 통째로 빠진다. */
    public static WeeklyCommentDto from(WeeklyComment comment) {
        if (comment == null) return null;

        return new WeeklyCommentDto(comment.goodPoint(), comment.improvePoint(),
                comment.habit(), comment.nextWeek());
    }
}
