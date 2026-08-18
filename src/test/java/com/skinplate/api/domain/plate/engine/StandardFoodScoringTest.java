package com.skinplate.api.domain.plate.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.service.StandardFood;
import com.skinplate.api.domain.food.service.StandardFoodTable;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표준 음식 전체를 실제 엔진에 통과시켜 <b>순위가 상식과 맞는지</b> 고정한다.
 *
 * <p>단위 테스트는 룰 하나가 제 값을 내는지만 본다. 이 앱이 실패하는 방식은 그게 아니라
 * "룰은 다 맞는데 감자튀김과 닭가슴살 샐러드가 같은 70점" 이었다 — 개별 룰은 전부
 * 통과하면서 합이 틀린 상태다. 그래서 여기서는 델타가 아니라 <b>음식들 사이의 순서</b>를
 * 단정한다. 임계값을 다시 튜닝하다 순서가 뒤집히면 이 테스트가 먼저 깨진다.
 */
class StandardFoodScoringTest {

    /** 게이트가 하나도 통과되지 않는 지표. 여기서 갈리는 점수는 순수하게 음식 축이다. */
    private static final SkinMetrics CALM   = SkinMetrics.of(60, 50, 40, 40, 60);
    private static final SkinMetrics MIDDLE = SkinMetrics.of(45, 65, 65, 65, 45);
    private static final SkinMetrics SEVERE = SkinMetrics.of(35, 85, 85, 85, 35);

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule(),
            new HighCalorieRule(), new SaturatedFatRule(), new RefinedCarbRule(),
            new FiberRule(), new Omega3FoodRule()));

    /** 시연에서 "좋은 음식"으로 보여줄 것들. */
    private static final List<String> HEALTHY =
            List.of("된장국_시금치", "참치구이", "닭가슴살 샐러드", "콩나물무침", "고등어구이");

    /** 시연에서 "부담이 큰 음식"으로 보여줄 것들. */
    private static final List<String> HEAVY =
            List.of("떡볶이", "치즈볼", "돈가스_치즈", "스테이크", "감자튀김", "탄탄면");

    @Test
    @DisplayName("건강식이 전부 부담식보다 높다 — 이 순서가 시연의 전부다")
    void healthyFoodsOutrankHeavyFoods() {
        int lowestHealthy = HEALTHY.stream().mapToInt(name -> score(name, CALM)).min().orElseThrow();
        int highestHeavy  = HEAVY.stream().mapToInt(name -> score(name, CALM)).max().orElseThrow();

        assertThat(lowestHealthy).isGreaterThan(highestHeavy);
    }

    @Test
    @DisplayName("정상 피부에서도 음식마다 점수가 갈린다 — 70점에 몰리지 않는다")
    void scoresSpreadOnCalmSkin() {
        List<Integer> scores = allScores(CALM);

        long exactlySeventy = scores.stream().filter(score -> score == 70).count();
        double share = exactlySeventy * 100.0 / scores.size();

        // 게이트가 있던 시절 이 값이 50% 였다. 룰이 다시 게이트 뒤로 숨으면 여기서 걸린다.
        assertThat(share).isLessThan(30.0);
        // 정상 피부에서 81점 이상에 닿는 음식이 하나도 없던 것이 개편 전 상태다.
        assertThat(scores.stream().filter(score -> score > 80).count()).isPositive();
        assertThat(standardDeviation(scores)).isGreaterThan(7.0);
    }

    @Test
    @DisplayName("피부가 나쁠수록 같은 음식표가 더 넓게 벌어진다 — 개인화가 살아 있다")
    void personalizationWidensTheSpread() {
        double calm   = standardDeviation(allScores(CALM));
        double middle = standardDeviation(allScores(MIDDLE));
        double severe = standardDeviation(allScores(SEVERE));

        assertThat(middle).isGreaterThan(calm);
        assertThat(severe).isGreaterThan(middle);
    }

    @Test
    @DisplayName("대표 음식 점수와 발동 룰 — 시연 대본에 그대로 쓰는 표")
    void demoScoreboard() {
        List<String> names = new ArrayList<>(HEALTHY);
        names.addAll(List.of("샐러드", "초밥", "김밥"));
        names.addAll(HEAVY);

        Map<String, Integer> board = new LinkedHashMap<>();
        for (String name : names) {
            PlateEvaluation evaluation = evaluate(name, CALM);
            board.put(name, evaluation.score());
            System.out.printf("%-14s %3d  %s%n", name, evaluation.score(),
                    evaluation.appliedRuleCodes());
        }

        // 표가 비면 이름이 바뀐 것이다 — 조용히 통과하지 않게 막는다.
        assertThat(board).hasSize(names.size()).allSatisfy((name, score) ->
                assertThat(score).as(name).isBetween(1, 100));
    }

    // ---- 표준 테이블 → 엔진 입력 ----

    private int score(String name, SkinMetrics skin) {
        return evaluate(name, skin).score();
    }

    private PlateEvaluation evaluate(String name, SkinMetrics skin) {
        StandardFood standard = StandardFoodTable.find(name)
                .orElseThrow(() -> new AssertionError("표준 음식 테이블에 없다: " + name));
        return engine.evaluate(new PlateContext(skin, toFood(standard)));
    }

    private List<Integer> allScores(SkinMetrics skin) {
        List<Integer> scores = new ArrayList<>();
        for (StandardFood food : allStandardFoods()) {
            scores.add(engine.evaluate(new PlateContext(skin, toFood(food))).score());
        }
        return scores;
    }

    /**
     * 표준 음식 이름 전체. {@link StandardFoodTable} 은 이름 조회만 열어 두므로
     * 리소스를 직접 읽는다 — 조회 API 를 이 테스트를 위해 넓히지 않는다.
     */
    private List<StandardFood> allStandardFoods() {
        List<StandardFood> foods = new ArrayList<>();
        try (InputStream stream = new ClassPathResource("food/standard-food.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(stream);
            for (String group : List.of("exact", "base")) {
                for (JsonNode node : root.path(group)) {
                    StandardFoodTable.find(node.path("name").asText()).ifPresent(foods::add);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("표준 음식 테이블을 읽지 못했다", e);
        }
        assertThat(foods).hasSizeGreaterThan(1_000);
        return foods;
    }

    /** FoodAnalysisService.toEntity 가 표준 음식에 대해 만드는 것과 같은 모양. */
    private static FoodAnalysis toFood(StandardFood standard) {
        Nutrition empty = Nutrition.of(0, BigDecimal.ZERO, BigDecimal.ZERO,
                                       BigDecimal.ZERO, 0, BigDecimal.ZERO);
        FoodAnalysis food = FoodAnalysis.create(null, standard.name(), "표준",
                standard.toNutrition(empty),
                standard.cookingMethod() == null ? CookingMethod.ETC : standard.cookingMethod(),
                standard.spicy(), "{}");

        for (IngredientTag tag : standard.tags()) {
            if (tag != IngredientTag.ETC) {
                food.addIngredient(FoodIngredient.of(standard.name(), tag));
            }
        }
        return food;
    }

    private static double standardDeviation(List<Integer> scores) {
        double mean = scores.stream().mapToInt(Integer::intValue).average().orElseThrow();
        double variance = scores.stream()
                .mapToDouble(score -> (score - mean) * (score - mean))
                .average().orElseThrow();
        return Math.sqrt(variance);
    }
}
