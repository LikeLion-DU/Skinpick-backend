package com.skinplate.api.domain.recommendation.service;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 취약 항목 → 후보 음식 매핑. (PRD §18.9)
 *
 * 음식 선정은 규칙, 문장 생성만 AI.
 * LLM이 매번 다른 음식을 추천하면 데모마다 결과가 달라져 설명할 수 없다.
 */
public final class RecommendationCandidates {

    private RecommendationCandidates() {}

    public enum Concern { DRY, REDNESS, TROUBLE, OILY, BARRIER_WEAK }

    public record Candidates(List<String> recommend, List<String> avoid) {}

    private static final Map<Concern, Candidates> TABLE = Map.of(
            Concern.DRY,          new Candidates(List.of("연어", "아보카도", "오이", "견과류"),
                                                 List.of("커피", "술")),
            Concern.REDNESS,      new Candidates(List.of("브로콜리", "녹차", "토마토"),
                                                 List.of("매운 음식", "술")),
            Concern.TROUBLE,      new Candidates(List.of("키위", "고구마", "견과류"),
                                                 List.of("탄산음료", "초콜릿", "튀김")),
            Concern.OILY,         new Candidates(List.of("채소", "두부", "흰살생선"),
                                                 List.of("튀김", "라면", "패스트푸드")),
            Concern.BARRIER_WEAK, new Candidates(List.of("연어", "달걀", "아몬드"),
                                                 List.of("인스턴트", "가공육")));

    public static Candidates of(Concern concern) {
        return TABLE.get(concern);
    }

    /** 심각한 순으로 취약 항목 상위 N개를 뽑는다. */
    public static List<Concern> topConcerns(SkinMetrics metrics, int count) {
        record Scored(Concern concern, int severity) {}

        return List.of(
                        new Scored(Concern.DRY,          100 - metrics.getHydration()),
                        new Scored(Concern.BARRIER_WEAK, 100 - metrics.getBarrier()),
                        new Scored(Concern.OILY,         metrics.getOil()),
                        new Scored(Concern.REDNESS,      metrics.getRedness()),
                        new Scored(Concern.TROUBLE,      metrics.getTrouble()))
                .stream()
                // 동점일 때 순서가 흔들리면 같은 지표에 다른 추천이 나온다.
                // 재현성이 이 테이블의 존재 이유이므로 이름으로 한 번 더 고정한다.
                .sorted(Comparator.comparingInt(Scored::severity).reversed()
                                  .thenComparing(scored -> scored.concern().name()))
                .limit(count)
                .map(Scored::concern)
                .toList();
    }
}
