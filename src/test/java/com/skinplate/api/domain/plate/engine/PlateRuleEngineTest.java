package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.food.entity.*;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD v1.4 §18.7 예시 계산을 그대로 재현한다.
 * 룰 임계값을 튜닝할 때 이 테스트가 깨지면 문서도 함께 고쳐야 한다는 신호다.
 */
class PlateRuleEngineTest {

    /** 건조 38 · 유분 52 · 홍조 64 · 트러블 25 · 장벽 78 */
    private static final SkinMetrics SKIN = SkinMetrics.of(38, 52, 64, 25, 78);

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule()));

    @Test
    @DisplayName("예시 A · 돼지고기 김치찌개 → 60점")
    void kimchiStew() {
        FoodAnalysis food = food("돼지고기 김치찌개", CookingMethod.BOILED, true,
                nutrition(520, "28.5", 1850, "6.2"),
                List.of(FoodIngredient.of("김치", IngredientTag.PROBIOTIC),
                        FoodIngredient.of("고춧가루", IngredientTag.CAPSAICIN)));

        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, food));

        // 70 + R05(+6) + R09(+4) + R04(-8) + R02(-12) = 60
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.appliedRuleCodes()).containsExactlyInAnyOrder("R02", "R04", "R05", "R09");
    }

    @Test
    @DisplayName("예시 B · 연어구이 정식 → 87점")
    void grilledSalmon() {
        FoodAnalysis food = food("연어구이 정식", CookingMethod.GRILLED, false,
                nutrition(610, "32.0", 1600, "4.0"),
                List.of(FoodIngredient.of("연어", IngredientTag.OMEGA3),
                        FoodIngredient.of("브로콜리", IngredientTag.ANTIOXIDANT),
                        FoodIngredient.of("된장", IngredientTag.PROBIOTIC)));

        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, food));

        // 70 + R01(+10) + R05(+6) + R06(+5) + R09(+4) + R04(-8) = 87
        assertThat(result.score()).isEqualTo(87);
    }

    @Test
    @DisplayName("나트륨 초과량에 비례해 감점이 커지고 -15에서 잘린다")
    void sodiumScalesWithExcess() {
        // 두 예시(1850·1600)는 초과량이 500 미만이라 비례 분기가 한 번도 돌지 않는다.
        // Day 8 에 만질 로직이므로 여기서 덮어둔다.
        FoodAnalysis mild = food("간장국", CookingMethod.BOILED, false,
                nutrition(300, "5.0", 2100, "2.0"), List.of());     // excess 600 → 8 + 1 = 9
        FoodAnalysis extreme = food("소금덩어리", CookingMethod.BOILED, false,
                nutrition(300, "5.0", 9000, "2.0"), List.of());     // excess 7500 → 8 + 15 → 15로 잘림

        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);   // 다른 룰이 안 걸리는 지표

        assertThat(engine.evaluate(new PlateContext(neutral, mild)).score())
                .isEqualTo(70 - 9);
        assertThat(engine.evaluate(new PlateContext(neutral, extreme)).score())
                .isEqualTo(70 - 15);
    }

    @Test
    @DisplayName("같은 음식이라도 홍조가 심하면 더 크게 감점된다")
    void severityMatters() {
        FoodAnalysis spicy = food("라면", CookingMethod.BOILED, true,
                nutrition(500, "10.0", 1800, "5.0"), List.of());

        int mild   = engine.evaluate(new PlateContext(SkinMetrics.of(50, 50, 62, 30, 60), spicy)).score();
        int severe = engine.evaluate(new PlateContext(SkinMetrics.of(50, 50, 88, 30, 60), spicy)).score();

        assertThat(severe).isLessThan(mild);
    }

    // ---- helpers ----

    private static FoodAnalysis food(String name, CookingMethod method, boolean spicy,
                                     Nutrition nutrition, List<FoodIngredient> ingredients) {
        FoodAnalysis food = FoodAnalysis.create(
                null, name, "한식", nutrition, method, spicy, "{}");
        food.addIngredients(ingredients);
        return food;
    }

    private static Nutrition nutrition(int kcal, String protein, int sodium, String sugar) {
        return Nutrition.of(kcal, new BigDecimal(protein), BigDecimal.ZERO,
                            BigDecimal.ZERO, sodium, new BigDecimal(sugar));
    }
}
