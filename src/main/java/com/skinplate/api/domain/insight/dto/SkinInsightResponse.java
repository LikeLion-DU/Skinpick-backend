package com.skinplate.api.domain.insight.dto;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.entity.SkinInsight;
import com.skinplate.api.domain.insight.entity.SkinInsightItem;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * S10 개인화 인사이트 화면.
 *
 * changes 는 <b>저장하지 않고</b> 조회할 때마다 직전 분석에서 다시 계산한다.
 * 직전 분석은 불변이므로 결과도 불변이고, 저장할 이유가 없다.
 */
public record SkinInsightResponse(
        Long skinAnalysisId,
        String summary,

        /* null 이면 non_null 직렬화로 키 자체가 생략된다 — 첫 분석이라 비교 대상이 없는 경우다.
           0 으로 채우면 "변화 없음"과 구분이 사라진다. */
        Changes changes,

        List<Insight> insights,
        List<TodayAction> todayActions,
        LocalDateTime generatedAt
) {
    /** 직전 분석 대비 증감. 부호 그대로다 — 방향 해석은 화면이 지표별로 한다. */
    public record Changes(int hydration, int oil, int redness, int trouble, int barrier, int skinScore) {}

    public record Insight(InsightCategory category, String priority, String title, String description) {}

    public record TodayAction(InsightCategory category, String title) {}

    /**
     * LAZY 컬렉션(items)을 초기화하므로 트랜잭션 안에서만 부른다. (open-in-view: false)
     *
     * @param previous 직전 피부 분석. 없으면 changes 가 null 이다
     */
    public static SkinInsightResponse from(SkinAnalysis analysis,
                                           SkinInsight insight,
                                           SkinAnalysis previous) {
        List<SkinInsightItem> items = insight.getItems();

        return new SkinInsightResponse(
                analysis.getId(),
                insight.getSummary(),
                changesOf(analysis, previous),
                items.stream()
                        .map(item -> new Insight(item.getCategory(), priorityOf(item.getDisplayOrder()),
                                item.getTitle(), item.getDescription()))
                        .toList(),
                items.stream()
                        .map(item -> new TodayAction(item.getCategory(), item.getActionTitle()))
                        .toList(),
                insight.getCreatedAt());
    }

    private static Changes changesOf(SkinAnalysis analysis, SkinAnalysis previous) {
        if (previous == null) return null;

        SkinMetrics current = analysis.getMetrics();
        SkinMetrics before = previous.getMetrics();

        return new Changes(
                current.getHydration() - before.getHydration(),
                current.getOil() - before.getOil(),
                current.getRedness() - before.getRedness(),
                current.getTrouble() - before.getTrouble(),
                current.getBarrier() - before.getBarrier(),
                analysis.getSkinScore() - previous.getSkinScore());
    }

    /**
     * displayOrder 가 곧 우선순위다. 등급을 따로 저장하면 "0번인데 LOW" 같은
     * 모순 상태가 표현 가능해진다 — 상한이 3이라 이 표 하나로 끝난다.
     */
    private static String priorityOf(int displayOrder) {
        return switch (displayOrder) {
            case 0 -> "HIGH";
            case 1 -> "MEDIUM";
            default -> "LOW";
        };
    }
}
