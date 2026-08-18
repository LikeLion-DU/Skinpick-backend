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
            new ProteinRule(), new VitaminRule(), new ProbioticRule(),
            new HighCalorieRule()));

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

    // ---- 스키마 v2 · 음식 특성 강도 (2026-08-17) ----

    /**
     * 하위 호환의 핵심 불변식 — 특성이 없거나(UNKNOWN 포함) 전부 모르는 값이면
     * <b>강도 계수가 전부 1.0</b> 이라 R02·R07 이 특성이 생기기 전과 같은 점수를 낸다.
     *
     * <p><b>"어떤 과거 입력이든 점수가 같다"는 뜻이 아니다.</b> 같은 PR 의 R10(&gt;900kcal)과
     * R03 의 40g 초과 단계는 특성과 무관하게 걸리므로, 그 조건에 해당하는 과거 입력은
     * 특성이 없어도 점수가 달라진다(highCalorieRule · sugarVeryHighAddsExtraPenalty 가
     * 그 동작을 따로 고정한다). 이 테스트가 덮는 것은 <b>특성 축</b> 하나뿐이다 —
     * 픽스처가 520kcal · 6.2g 이라 나머지 둘은 애초에 발동하지 않는다.
     */
    @Test
    @DisplayName("특성이 전부 UNKNOWN 이면 강도 계수가 1.0 이라 특성이 없던 시절과 점수가 같다")
    void unknownTraitsBehaveExactlyAsBefore() {
        PlateEvaluation withoutTraits = engine.evaluate(new PlateContext(SKIN, demoStew()));

        FoodAnalysis unknownTraits = demoStew();
        unknownTraits.assignTraits(FoodTraits.of(FoodGroup.ETC, PortionSize.UNKNOWN,
                Spiciness.UNKNOWN, Oiliness.UNKNOWN, ProcessingLevel.UNKNOWN));
        PlateEvaluation withUnknown = engine.evaluate(new PlateContext(SKIN, unknownTraits));

        assertThat(withoutTraits.score()).isEqualTo(60);
        assertThat(withUnknown.score()).isEqualTo(60);
        assertThat(withUnknown.appliedRuleCodes())
                .containsExactlyElementsOf(withoutTraits.appliedRuleCodes());
    }

    @Test
    @DisplayName("매운맛 강도가 R02 감점을 가른다 — MILD -8 · MEDIUM -12 · HOT -16")
    void spicinessIntensityScalesR02() {
        // 홍조 64 → 심각도 1.2. R02 만 걸리는 조합(나트륨·당·단백질 전부 임계 아래).
        Nutrition mildNutrition = nutrition(500, "10.0", 1000, "5.0");

        assertThat(scoreOfSpicy(mildNutrition, Spiciness.MILD)).isEqualTo(70 - 8);     // -10×1.2×0.7
        assertThat(scoreOfSpicy(mildNutrition, Spiciness.MEDIUM)).isEqualTo(70 - 12);  // -10×1.2×1.0
        assertThat(scoreOfSpicy(mildNutrition, Spiciness.HOT)).isEqualTo(70 - 16);     // -10×1.2×1.3
        // UNKNOWN 은 MEDIUM 과 같다 — 구 데이터가 손해도 이득도 보지 않는다.
        assertThat(scoreOfSpicy(mildNutrition, Spiciness.UNKNOWN)).isEqualTo(70 - 12);
    }

    @Test
    @DisplayName("홍조가 임계 아래면 HOT 이어도 R02 는 안 걸린다 — 강도는 발동 조건이 아니다")
    void spicinessDoesNotTriggerWithoutRedness() {
        FoodAnalysis hotFood = withTraits(
                food("불닭", CookingMethod.BOILED, true, nutrition(500, "10.0", 1000, "5.0"), List.of()),
                Spiciness.HOT, Oiliness.UNKNOWN);

        SkinMetrics calmSkin = SkinMetrics.of(50, 50, 50, 50, 50);

        assertThat(engine.evaluate(new PlateContext(calmSkin, hotFood)).score()).isEqualTo(70);
    }

    @Test
    @DisplayName("튀김이 아니어도 기름기 HIGH 면 R07 이 약하게 걸린다 — 삼겹살 구이가 사각지대였다")
    void oilinessHighTriggersR07WithoutFrying() {
        SkinMetrics oilySkin = SkinMetrics.of(50, 75, 50, 50, 50);   // 유분 75 → 심각도 1.2
        Nutrition plain = nutrition(500, "10.0", 1000, "5.0");

        FoodAnalysis grilledOily = withTraits(
                food("삼겹살 구이", CookingMethod.GRILLED, false, plain, List.of()),
                Spiciness.UNKNOWN, Oiliness.HIGH);
        FoodAnalysis fried = food("치킨", CookingMethod.FRIED, false, plain, List.of());
        FoodAnalysis grilledUnknown = food("닭가슴살 구이", CookingMethod.GRILLED, false, plain, List.of());

        // 기름진 비튀김은 튀김보다 약하다: -10×1.2×0.7 = -8 vs 튀김 -10×1.2 = -12
        assertThat(engine.evaluate(new PlateContext(oilySkin, grilledOily)).score()).isEqualTo(70 - 8);
        assertThat(engine.evaluate(new PlateContext(oilySkin, fried)).score()).isEqualTo(70 - 12);
        // UNKNOWN 은 발동하지 않는다 — 특성이 없던 시절과 동일.
        assertThat(engine.evaluate(new PlateContext(oilySkin, grilledUnknown)).score()).isEqualTo(70);

        // 튀김옷이 없으니 REMOVE_BATTER 행동 카드가 붙지 않는다.
        PlateEvaluation evaluation = engine.evaluate(new PlateContext(oilySkin, grilledOily));
        assertThat(evaluation.results()).noneMatch(RuleResult::hasAction);
    }

    // ---- 음식 축 · 피부 게이트 제거 (2026-08-18) ----

    /**
     * 게이트가 있던 시절 이 두 음식은 <b>똑같이 70점</b>이었다. 그게 이 개편의 출발점이다.
     */
    @Test
    @DisplayName("정상 피부에서도 튀김과 당류는 감점된다 — 감자튀김과 샐러드가 같은 점수일 수 없다")
    void foodBurdenAppliesOnCalmSkin() {
        SkinMetrics calm = SkinMetrics.of(60, 50, 40, 40, 60);   // 어떤 게이트도 통과하지 못하는 지표

        FoodAnalysis fried = food("감자튀김", CookingMethod.FRIED, false,
                nutrition(400, "6.0", 300, "1.0"), List.of());
        FoodAnalysis sugary = food("탄산음료 세트", CookingMethod.ETC, false,
                nutrition(400, "6.0", 300, "20.0"), List.of());
        FoodAnalysis plain = food("닭가슴살 샐러드", CookingMethod.RAW, false,
                nutrition(150, "12.0", 300, "3.0"), List.of());

        // 심각도 0.6 — 발동하지 않는 것이 아니라 약하게 걸린다.
        assertThat(engine.evaluate(new PlateContext(calm, fried)).score()).isEqualTo(70 - 6);
        assertThat(engine.evaluate(new PlateContext(calm, sugary)).score()).isEqualTo(70 - 7);
        assertThat(engine.evaluate(new PlateContext(calm, plain)).score()).isEqualTo(70);
    }

    @Test
    @DisplayName("같은 튀김이 피부 상태에 따라 -6 / -12 / -15 로 벌어진다")
    void friedPenaltyScalesWithOil() {
        FoodAnalysis fried = food("감자튀김", CookingMethod.FRIED, false,
                nutrition(400, "6.0", 300, "1.0"), List.of());

        assertThat(engine.evaluate(new PlateContext(SkinMetrics.of(60, 50, 40, 40, 60), fried)).score())
                .isEqualTo(70 - 6);
        assertThat(engine.evaluate(new PlateContext(SkinMetrics.of(60, 75, 40, 40, 60), fried)).score())
                .isEqualTo(70 - 12);
        assertThat(engine.evaluate(new PlateContext(SkinMetrics.of(60, 85, 40, 40, 60), fried)).score())
                .isEqualTo(70 - 15);
    }

    /**
     * 게이트를 떼면 문장이 거짓이 될 수 있다 — 유분·트러블이 정상인 사람에게
     * "지금 유분이 높은 상태에서" 라고 말하는 순간 화면이 사실이 아닌 것을 말한다.
     */
    @Test
    @DisplayName("피부가 정상이면 판정 이유가 피부 상태를 단정하지 않는다")
    void reasonDoesNotClaimSkinStateWhenCalm() {
        SkinMetrics calm = SkinMetrics.of(60, 50, 40, 40, 60);

        FoodAnalysis fried = food("감자튀김", CookingMethod.FRIED, false,
                nutrition(400, "6.0", 300, "20.0"), List.of());

        PlateEvaluation evaluation = engine.evaluate(new PlateContext(calm, fried));

        assertThat(reasonOf(evaluation, "R07")).doesNotContain("지금 유분이").contains("튀김 조리라");
        assertThat(reasonOf(evaluation, "R03")).doesNotContain("트러블 지표").contains("당류가 많은 편이라");

        // 피부가 나쁘면 예전처럼 지표를 잇는 문장이 나온다.
        PlateEvaluation personalized = engine.evaluate(
                new PlateContext(SkinMetrics.of(60, 85, 40, 85, 60), fried));
        assertThat(reasonOf(personalized, "R07")).contains("지금 유분이 많이 높은 상태");
        assertThat(reasonOf(personalized, "R03")).contains("트러블 지표가 많이 올라와 있는");
    }

    @Test
    @DisplayName("당류 임계는 15g 이다 — 국물떡볶이(33g)가 비켜 가던 25g 을 내렸다")
    void sugarThresholdIsFifteenGrams() {
        SkinMetrics calm = SkinMetrics.of(60, 50, 40, 40, 60);

        FoodAnalysis atBoundary = food("국물떡볶이", CookingMethod.BOILED, false,
                nutrition(400, "6.0", 300, "15.0"), List.of());
        FoodAnalysis overBoundary = food("국물떡볶이", CookingMethod.BOILED, false,
                nutrition(400, "6.0", 300, "15.1"), List.of());

        assertThat(engine.evaluate(new PlateContext(calm, atBoundary)).score()).isEqualTo(70);
        assertThat(engine.evaluate(new PlateContext(calm, overBoundary)).score()).isEqualTo(70 - 7);
    }

    @Test
    @DisplayName("당류 40g 초과는 감점을 더한다 — 15~40g 구간은 기본 델타 그대로다")
    void sugarVeryHighAddsExtraPenalty() {
        SkinMetrics troubledSkin = SkinMetrics.of(50, 50, 50, 65, 50);   // 트러블 65 → 심각도 1.2

        FoodAnalysis high = food("케이크", CookingMethod.ETC, false,
                nutrition(500, "5.0", 300, "30.0"), List.of());          // -12×1.2 = -14.4 → -14
        FoodAnalysis veryHigh = food("허니콤보", CookingMethod.ETC, false,
                nutrition(500, "5.0", 300, "45.0"), List.of());          // -16×1.2 = -19.2 → -19

        assertThat(engine.evaluate(new PlateContext(troubledSkin, high)).score()).isEqualTo(70 - 14);
        assertThat(engine.evaluate(new PlateContext(troubledSkin, veryHigh)).score()).isEqualTo(70 - 19);
    }

    @Test
    @DisplayName("R10 — 900kcal 초과만 고정 -5 로 걸리고 밥 줄이기 행동이 붙는다")
    void highCalorieRule() {
        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);

        FoodAnalysis heavy = food("곱빼기", CookingMethod.ETC, false,
                nutrition(950, "10.0", 1000, "5.0"), List.of());
        FoodAnalysis boundary = food("보통", CookingMethod.ETC, false,
                nutrition(900, "10.0", 1000, "5.0"), List.of());

        PlateEvaluation evaluation = engine.evaluate(new PlateContext(neutral, heavy));
        assertThat(evaluation.score()).isEqualTo(70 - 5);
        assertThat(evaluation.appliedRuleCodes()).containsExactly("R10");
        // 광고한 회복치가 시뮬레이션의 실제 효과와 같아야 한다 — R10 은 고정 감점이라
        // 근사가 필요 없고, 어긋나면 카드가 확인 가능한 거짓을 말한다.
        assertThat(evaluation.results().get(0).expectedGain()).isEqualTo(5);

        assertThat(engine.evaluate(new PlateContext(neutral, boundary)).score()).isEqualTo(70);
    }

    /**
     * reason 은 결정론 템플릿이다 — 같은 입력이면 같은 문장. 현재 피부 지표와 음식
     * 특성을 잇되 인과를 단정하지 않는다("부담이 될 수 있어요"까지).
     */
    @Test
    @DisplayName("판정 이유가 현재 피부 상태와 음식 특성을 잇는 문장으로 붙는다")
    void reasonsConnectSkinStateAndFoodTraits() {
        FoodAnalysis hotStew = withTraits(demoStew(), Spiciness.HOT, Oiliness.UNKNOWN);

        PlateEvaluation evaluation = engine.evaluate(new PlateContext(SKIN, hotStew));

        assertThat(reasonOf(evaluation, "R02"))
                .contains("붉은기가 높은 상태").contains("강한 매운맛").contains("될 수 있어요");
        assertThat(reasonOf(evaluation, "R04")).contains("나트륨이 높은 편");
        // 피드백 엔티티까지 실려 내려간다 — DTO 배선은 FeedbackDto.from 이 잇는다.
        assertThat(evaluation.toFeedbacks())
                .filteredOn(feedback -> "R02".equals(feedback.getRuleCode())
                        && feedback.getType() == com.skinplate.api.domain.plate.entity.FeedbackType.CAUTION)
                .singleElement()
                .satisfies(feedback -> assertThat(feedback.getReason()).contains("붉은기"));
    }

    private String reasonOf(PlateEvaluation evaluation, String ruleCode) {
        return evaluation.results().stream()
                .filter(result -> result.ruleCode().equals(ruleCode))
                .findFirst().orElseThrow()
                .reason();
    }

    private int scoreOfSpicy(Nutrition nutrition, Spiciness spiciness) {
        FoodAnalysis spicyFood = withTraits(
                food("라면", CookingMethod.BOILED, true, nutrition, List.of()),
                spiciness, Oiliness.UNKNOWN);
        return engine.evaluate(new PlateContext(SKIN, spicyFood)).score();
    }

    /** 예시 A 의 김치찌개 — 특성 없는 원형. */
    private static FoodAnalysis demoStew() {
        return food("돼지고기 김치찌개", CookingMethod.BOILED, true,
                nutrition(520, "28.5", 1850, "6.2"),
                List.of(FoodIngredient.of("김치", IngredientTag.PROBIOTIC),
                        FoodIngredient.of("고춧가루", IngredientTag.CAPSAICIN)));
    }

    private static FoodAnalysis withTraits(FoodAnalysis food, Spiciness spiciness, Oiliness oiliness) {
        food.assignTraits(FoodTraits.of(FoodGroup.ETC, PortionSize.UNKNOWN,
                spiciness, oiliness, ProcessingLevel.UNKNOWN));
        return food;
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
