package com.skinplate.api.infra.openai.dto;

/**
 * 주간 리포트 문장 넷. PlateComments 와 같이 <b>숫자 필드가 없는 것이 의도다</b> —
 * 평균도 BEST DAY 도 서버가 이미 정했고, AI 는 그 결과를 설명하기만 한다.
 *
 * @param goodPoint    이번 주 잘한 점
 * @param improvePoint 개선할 점
 * @param habit        반복적으로 나타난 식습관
 * @param nextWeek     다음 주 추천
 */
public record WeeklyComment(String goodPoint, String improvePoint,
                            String habit, String nextWeek) {}
