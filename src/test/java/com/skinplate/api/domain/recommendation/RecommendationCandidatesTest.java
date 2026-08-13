package com.skinplate.api.domain.recommendation;

import com.skinplate.api.domain.recommendation.service.RecommendationCandidates;
import com.skinplate.api.domain.recommendation.service.RecommendationCandidates.Concern;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 취약 항목 선정은 "심각한 순 상위 2개"가 아니라 <b>"실제로 취약한 것 중</b> 상위 2개"다.
 * 그 구분이 없으면 피부가 멀쩡한 사람에게도 추천이 나가는데, 심사위원이 본인 얼굴로
 * 찍어 보는 순간이 정확히 그 경우다.
 *
 * 임계값은 SkinMetrics 가 갖고 있다 — DRY<40 · OILY>70 · REDNESS>60 · TROUBLE>60 · BARRIER<40.
 */
class RecommendationCandidatesTest {

    private static final int TOP = 2;

    @Test
    @DisplayName("A. 피부가 멀쩡하면 취약 항목이 없다 — 없는 걱정을 만들지 않는다")
    void healthySkin_hasNoConcern() {
        SkinMetrics healthy = SkinMetrics.of(95, 5, 5, 5, 95);

        assertThat(RecommendationCandidates.topConcerns(healthy, TOP)).isEmpty();
    }

    @Test
    @DisplayName("A-b. 임계값 경계에서는 취약으로 보지 않는다 — 초과·미만이 조건이다")
    void exactlyOnThreshold_isNotAConcern() {
        // hydration 40 = DRY_THRESHOLD (미만이어야 건조), redness 60 = 임계값 (초과여야 홍조)
        SkinMetrics onEdge = SkinMetrics.of(40, 70, 60, 60, 40);

        assertThat(RecommendationCandidates.topConcerns(onEdge, TOP)).isEmpty();
    }

    @Test
    @DisplayName("B. 홍조만 넘으면 홍조 하나다 — 자리를 채우려고 건조를 끼워 넣지 않는다")
    void onlyRedness_returnsRednessAlone() {
        SkinMetrics redOnly = SkinMetrics.of(80, 30, 75, 10, 80);

        assertThat(RecommendationCandidates.topConcerns(redOnly, TOP))
                .containsExactly(Concern.REDNESS);
    }

    @Test
    @DisplayName("C. 시연 지표 38/52/64/25/78 → 홍조(64) · 건조(62) 순서 그대로")
    void demoMetrics_returnRednessThenDry() {
        SkinMetrics demo = SkinMetrics.of(38, 52, 64, 25, 78);

        assertThat(RecommendationCandidates.topConcerns(demo, TOP))
                .containsExactly(Concern.REDNESS, Concern.DRY);
    }

    @Test
    @DisplayName("D. 셋 이상 넘어도 심각한 순 두 개까지만 나온다")
    void moreThanTwo_keepsTopTwoBySeverity() {
        // DRY 100-10=90 · TROUBLE 85 · REDNESS 80 · OILY 75 · BARRIER 100-30=70 → 다섯 개 전부 초과
        SkinMetrics severe = SkinMetrics.of(10, 75, 80, 85, 30);

        assertThat(RecommendationCandidates.topConcerns(severe, TOP))
                .containsExactly(Concern.DRY, Concern.TROUBLE);
    }

    @Test
    @DisplayName("F. 트러블이 임계값을 넘으면 키위 경로가 실제로 열린다")
    void troubleAboveThreshold_reachesKiwi() {
        SkinMetrics troubled = SkinMetrics.of(80, 30, 20, 70, 80);

        List<Concern> concerns = RecommendationCandidates.topConcerns(troubled, TOP);

        assertThat(concerns).containsExactly(Concern.TROUBLE);
        assertThat(RecommendationCandidates.of(Concern.TROUBLE).recommend()).contains("키위");
    }

    @Test
    @DisplayName("같은 음식이 추천과 주의 양쪽에 있으면 안 된다 — 화면에 권하면서 말리게 된다")
    void noFoodIsBothRecommendedAndAvoided() {
        Set<String> recommended = Stream.of(Concern.values())
                .flatMap(concern -> RecommendationCandidates.of(concern).recommend().stream())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> avoided = Stream.of(Concern.values())
                .flatMap(concern -> RecommendationCandidates.of(concern).avoid().stream())
                .collect(java.util.stream.Collectors.toSet());

        // 중복 제거는 타입별로 돈다(DB 의 UNIQUE 도 타입을 포함한다). 그래서 표에 겹치는
        // 이름을 넣는 순간 한 사용자에게 "드세요"와 "피하세요"가 동시에 뜬다.
        // 런타임에 한쪽을 조용히 버리는 대신 여기서 빌드를 깨뜨린다.
        assertThat(recommended).doesNotContainAnyElementsOf(avoided);
    }

    @Test
    @DisplayName("E. 후보 표의 모든 음식에 문구가 있고, 서로 다르다")
    void everyCandidateHasItsOwnReason() {
        List<String> foods = Stream.of(Concern.values())
                .map(RecommendationCandidates::of)
                .flatMap(candidates -> Stream.concat(
                        candidates.recommend().stream(), candidates.avoid().stream()))
                .distinct()
                .toList();

        // 표에 이름을 추가하고 문구를 빠뜨리면 화면에 이름만 뜬다.
        assertThat(foods).allSatisfy(food ->
                assertThat(RecommendationCandidates.reasonOf(food))
                        .as("%s 의 추천 문구", food).isNotBlank());

        // 문구가 항목별이면 같은 취약 항목의 음식들이 글자까지 같은 문장을 단다.
        Set<String> distinct = foods.stream()
                .map(RecommendationCandidates::reasonOf)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(distinct).hasSameSizeAs(foods);
    }
}
