package com.skinplate.api.domain.report.dto;

import java.util.List;

/**
 * 반복된 감점 하나.
 *
 * label 은 새로 만든 이름이 아니라 저장된 피드백의 message 다 — 9개 룰이 모두
 * 짧은 라벨을 리터럴로 돌려주고 그 값이 그대로 기록돼 있다.
 *
 * @param totalDelta 음수. 합산 감점
 * @param topFoods   그 룰이 적용된 Plate 의 음식명, 빈도 상위 3
 */
public record PenaltyDto(String ruleCode, String label, int count, int totalDelta,
                         List<String> topFoods) {}
