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
    @DisplayName("정상 피부에서도 음식마다 점수가 갈린다 — 기준점수에 몰리지 않는다")
    void scoresSpreadOnCalmSkin() {
        List<Integer> scores = allScores(CALM);

        // 리터럴 70 을 쓰면 BASE_SCORE 를 옮기는 순간(2026-08-20 70→75) 이 가드가 조용히
        // 무의미해진다 — "아무 룰도 안 걸린 음식"이 더는 70 이 아니라서 세는 대상이 사라지고,
        // 표가 아무리 몰려도 통과한다. 세야 하는 것은 숫자가 아니라 기준점수 그 자체다.
        long atBaseScore = scores.stream()
                .filter(score -> score == RuleConstants.BASE_SCORE).count();
        double share = atBaseScore * 100.0 / scores.size();

        // 게이트가 있던 시절 이 값이 50% 였다. 룰이 다시 게이트 뒤로 숨으면 여기서 걸린다.
        assertThat(share).isLessThan(30.0);
        // 기준점수보다 확실히 위로 올라가는 음식이 있어야 한다. 여기도 리터럴을 쓰지 않는다 —
        // BASE 가 오르면 "80 초과"는 가점 하나로 넘겨져 아무것도 검증하지 않는다.
        assertThat(scores.stream()
                .filter(score -> score > RuleConstants.BASE_SCORE + 10).count()).isPositive();
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
        //
        // `isBetween(1, MAX_SCORE)` 로는 아무것도 못 잡는다 — 엔진이 이미 MAX_SCORE 로
        // clamp 하므로 위쪽 경계가 깨질 입력이 없다(리터럴 100 이던 시절에도 같았다).
        //
        // 대신 **상한에 붙지 않는다**를 본다. 상한에 닿은 점수는 원점수를 잘라낸 값이라
        // 서로 다른 음식이 같은 숫자로 뭉개지고, 그 구간에서는 행동 카드가 광고한
        // 회복치도 0 이 된다. 시연 대본에 쓰는 표만은 그 평탄면 밖에 있어야 한다.
        // (동점 자체는 정상이다 — 다른 음식이 같은 점수를 받을 수 있다.)
        assertThat(board).hasSize(names.size()).allSatisfy((name, score) -> {
            assertThat(score).as(name).isPositive();
            assertThat(score).as(name + " — 상한에 붙었다").isLessThan(RuleConstants.MAX_SCORE);
        });
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

        // 표준 유래 태그로 넣는다 — 점수용 태그는 이것만 선다(FoodAnalysis.hasTag).
        food.assignStandardFoodName(standard.name());
        for (IngredientTag tag : standard.tags()) {
            if (tag != IngredientTag.ETC) {
                food.addIngredient(FoodIngredient.fromStandardTable(standard.name(), tag));
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
