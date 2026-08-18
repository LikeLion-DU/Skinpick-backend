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
 * PRD §18.7 예시 계산을 그대로 재현한다.
 * 룰 임계값을 튜닝할 때 이 테스트가 깨지면 문서도 함께 고쳐야 한다는 신호다.
 *
 * <p>영양값은 표준 음식 테이블의 실제 값을 쓴다(포화지방 포함). 사용자가 사진을 찍었을 때
 * 나오는 숫자와 이 테스트가 같은 것을 봐야 "재현" 이라 부를 수 있다.
 */
class PlateRuleEngineTest {

    /** 건조 38 · 유분 52 · 홍조 64 · 트러블 25 · 장벽 78 */
    private static final SkinMetrics SKIN = SkinMetrics.of(38, 52, 64, 25, 78);

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule(),
            new HighCalorieRule(), new SaturatedFatRule(), new RefinedCarbRule(),
            new FiberRule(), new Omega3FoodRule()));

    @Test
    @DisplayName("예시 A · 돼지고기 김치찌개 → 58점")
    void kimchiStew() {
        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, demoStew()));

        // 70 + R05(+6) + R09(+4) + R04(-8) + R11(-2) + R02(-12) = 58
        assertThat(result.score()).isEqualTo(58);
        assertThat(result.appliedRuleCodes())
                .containsExactlyInAnyOrder("R02", "R04", "R05", "R09", "R11");
    }

    @Test
    @DisplayName("예시 B · 연어구이 정식 → 93점")
    void grilledSalmon() {
        FoodAnalysis food = standardFood("연어구이 정식", CookingMethod.GRILLED, false,
                nutrition(610, "32.0", 1600, "4.0", "28.0", "45.0", "4.4"),
                IngredientTag.OMEGA3, IngredientTag.ANTIOXIDANT, IngredientTag.PROBIOTIC);

        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, food));

        // 70 + R01(+10) + R05(+6) + R06(+5) + R09(+4) + R14(+4) + R04(-4) + R11(-2) = 93
        // 나트륨 1,600mg 은 새 1단계(>1150)라 -4 다. 옛 임계 1,500mg 에서는 -8 이었다.
        // R14 는 R01 과 독립이다 — 건조한 사용자의 연어가 두 룰을 다 받는 것은
        // 이중계상이 아니라 "이 사람에게 가장 필요한 음식" 이라는 뜻이다.
        assertThat(result.score()).isEqualTo(93);
        assertThat(result.appliedRuleCodes()).contains("R01", "R14");
    }

    @Test
    @DisplayName("나트륨은 3단계로 깎인다 — p75 -4 · p90 -8 · p95 -12")
    void sodiumIsTiered() {
        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);   // 다른 룰이 안 걸리는 지표

        assertThat(scoreOfSodium(neutral, 1150)).isEqualTo(70);        // 경계는 초과부터
        assertThat(scoreOfSodium(neutral, 1151)).isEqualTo(70 - 4);
        assertThat(scoreOfSodium(neutral, 1701)).isEqualTo(70 - 8);
        assertThat(scoreOfSodium(neutral, 2301)).isEqualTo(70 - 12);
        // 초과량 비례에서 계단으로 바꿨으므로 아무리 짜도 -12 를 넘지 않는다.
        assertThat(scoreOfSodium(neutral, 9000)).isEqualTo(70 - 12);
    }

    /**
     * 옛 비례식은 "국물 절반"의 효과를 미리 계산할 수 없어 고정 +8 을 광고했다.
     * 계단형은 절반으로 줄인 뒤의 단계를 알 수 있으므로, 카드가 말하는 회복치와
     * 시뮬레이션이 실제로 올리는 점수가 같아야 한다.
     */
    @Test
    @DisplayName("국물 절반 회복치가 실제 단계 변화와 같다")
    void sodiumGainMatchesTheActualTierChange() {
        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);

        // 2400 → 절반 1200 : -12 에서 -4 로 → 회복 8
        assertThat(gainOfSodium(neutral, 2400)).isEqualTo(8);
        // 1200 → 절반 600 : -4 에서 0 으로 → 회복 4. 옛 고정값이라면 "+8" 이라 거짓말했다.
        assertThat(gainOfSodium(neutral, 1200)).isEqualTo(4);
    }

    private int scoreOfSodium(SkinMetrics skin, int sodiumMg) {
        FoodAnalysis soup = food("국", CookingMethod.BOILED, false,
                nutrition(300, "5.0", sodiumMg, "2.0"), List.of());
        return engine.evaluate(new PlateContext(skin, soup)).score();
    }

    private int gainOfSodium(SkinMetrics skin, int sodiumMg) {
        FoodAnalysis soup = food("국", CookingMethod.BOILED, false,
                nutrition(300, "5.0", sodiumMg, "2.0"), List.of());
        return engine.evaluate(new PlateContext(skin, soup)).results().stream()
                .filter(result -> result.ruleCode().equals("R04"))
                .findFirst().orElseThrow()
                .expectedGain();
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

        assertThat(withoutTraits.score()).isEqualTo(58);
        assertThat(withUnknown.score()).isEqualTo(58);
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

    /**
     * 임계를 25g 에서 15g 으로 내리면서 "단 음료를 물로 바꿔도 같은 단계에 남는" 구간이
     * 생겼다(37.5~40g · 0.4 배 후에도 15g 위). 옛 고정값 +7 은 그 구간에서 회복이 0 인데도
     * +7 이라 말했고, 표준 음식표에 실제로 그런 음식이 3종 있다(고구마맛탕 등).
     */
    @Test
    @DisplayName("단 음료 회복치가 실제 단계 변화와 같고, 회복이 0 이면 카드를 주지 않는다")
    void sugarGainMatchesTheActualTierChange() {
        SkinMetrics troubled = SkinMetrics.of(50, 50, 50, 65, 50);   // 트러블 65 → 심각도 1.2

        // 30g → 12g : 1단계에서 0 으로 → 회복 14
        assertThat(sugarGain(troubled, "30.0")).isEqualTo(14);
        // 45g → 18g : 2단계에서 1단계로 → 회복 5 (-19 에서 -14)
        assertThat(sugarGain(troubled, "45.0")).isEqualTo(5);
        // 38.7g → 15.48g : 여전히 1단계라 회복이 0 이다 — 카드를 아예 주지 않는다.
        assertThat(sugarGain(troubled, "38.7")).isZero();
        assertThat(engine.evaluate(new PlateContext(troubled, sugaryFood("38.7"))).results())
                .filteredOn(result -> "R03".equals(result.ruleCode()))
                .singleElement()
                .satisfies(result -> assertThat(result.hasAction()).isFalse());
    }

    private FoodAnalysis sugaryFood(String sugar) {
        return food("단 음식", CookingMethod.ETC, false,
                nutrition(400, "5.0", 300, sugar), List.of());
    }

    /** R03 이 광고하는 회복치. 카드가 없으면 0 이다. */
    private int sugarGain(SkinMetrics skin, String sugar) {
        return engine.evaluate(new PlateContext(skin, sugaryFood(sugar))).results().stream()
                .filter(result -> "R03".equals(result.ruleCode()))
                .findFirst().orElseThrow()
                .expectedGain();
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
    @DisplayName("R10 — 열량은 2단계로 걸리고 밥 줄이기 회복치가 실제 단계 변화와 같다")
    void highCalorieRule() {
        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);

        FoodAnalysis boundary = food("보통", CookingMethod.ETC, false,
                nutrition(660, "10.0", 1000, "5.0"), List.of());
        FoodAnalysis heavy = food("곱빼기", CookingMethod.ETC, false,
                nutrition(700, "10.0", 1000, "5.0"), List.of());
        FoodAnalysis veryHeavy = food("특곱빼기", CookingMethod.ETC, false,
                nutrition(1000, "10.0", 1000, "5.0"), List.of());
        FoodAnalysis beyondHelp = food("초대형", CookingMethod.ETC, false,
                nutrition(1200, "10.0", 1000, "5.0"), List.of());

        assertThat(engine.evaluate(new PlateContext(neutral, boundary)).score()).isEqualTo(70);

        PlateEvaluation evaluation = engine.evaluate(new PlateContext(neutral, heavy));
        assertThat(evaluation.score()).isEqualTo(70 - 4);
        assertThat(evaluation.appliedRuleCodes()).containsExactly("R10");
        // 700 × 0.75 = 525 → 단계 0. 광고한 회복치가 시뮬레이션의 실제 효과와 같다.
        assertThat(evaluation.results().get(0).expectedGain()).isEqualTo(4);

        // 1000 × 0.75 = 750 → 2단계에서 1단계로만 내려간다. 회복은 7-4 = 3 이다.
        PlateEvaluation veryHeavyResult = engine.evaluate(new PlateContext(neutral, veryHeavy));
        assertThat(veryHeavyResult.score()).isEqualTo(70 - 7);
        assertThat(veryHeavyResult.results().get(0).expectedGain()).isEqualTo(3);

        // 1200 × 0.75 = 900 → 여전히 2단계라 실제 회복이 0 이다. 옛 고정값은 이 자리에서도
        // "+5" 라 말했고, 사용자가 버튼을 누르면 점수가 그대로였다 — 이제 카드를 안 준다.
        PlateEvaluation beyondHelpResult = engine.evaluate(new PlateContext(neutral, beyondHelp));
        assertThat(beyondHelpResult.results().get(0).expectedGain()).isZero();
        assertThat(beyondHelpResult.results()).noneMatch(RuleResult::hasAction);
        // 주의 문장은 사라지지 않는다 — 회복이 없다고 부담까지 없는 것은 아니다.
        assertThat(beyondHelpResult.appliedRuleCodes()).contains("R10");
    }

    // ---- R11 포화지방 · R12 정제 탄수 (2026-08-18) ----

    private static final SkinMetrics CALM = SkinMetrics.of(60, 50, 40, 40, 60);

    /**
     * <b>이 룰이 존재하는 이유가 이 테스트다.</b> 총지방으로 재던 시절 연어(28g)와
     * 스테이크(39g)는 둘 다 최상위 구간이라 같은 -10 을 먹었다. 등푸른생선의 지방은
     * 불포화가 주라 같은 벌을 줄 근거가 없고, 그 한계를 "OMEGA3 태그면 완화" 라는
     * 대리 지표로 메우고 있었다. 실측 포화지방이 그 우회로를 지운다.
     */
    @Test
    @DisplayName("총지방이 비슷해도 포화지방이 다르면 갈린다 — 생선은 비켜 가고 붉은 고기·튀김은 걸린다")
    void saturatedFatSeparatesFishFromRedMeat() {
        // 표준 음식 테이블의 실제 값이다.
        FoodAnalysis salmon = food("연어구이", CookingMethod.GRILLED, false,
                nutrition(610, "32.0", 300, "4.0", "28.0", "45.0", "4.4"), List.of());
        FoodAnalysis mackerel = food("고등어구이", CookingMethod.GRILLED, false,
                nutrition(237, "16.3", 329, "0.0", "16.4", "4.6", "3.8"), List.of());
        FoodAnalysis tuna = food("참치구이", CookingMethod.GRILLED, false,
                nutrition(347, "47.8", 826, "0.0", "16.2", "2.5", "5.0"), List.of());
        FoodAnalysis steak = food("스테이크", CookingMethod.GRILLED, false,
                nutrition(766, "52.7", 1061, "18.1", "39.1", "35.6", "12.7"), List.of());
        FoodAnalysis porkCutlet = food("치즈돈가스", CookingMethod.FRIED, false,
                nutrition(755, "42.0", 870, "3.6", "46.7", "36.4", "13.6"), List.of());

        // 고등어 3.8g 은 1단계 경계(3.9) 아래라 아예 안 걸린다.
        assertThat(rulesOf(salmon)).contains("R11");
        assertThat(rulesOf(mackerel)).doesNotContain("R11");
        assertThat(deltaOf(salmon, "R11")).isEqualTo(-2);
        assertThat(deltaOf(tuna, "R11")).isEqualTo(-2);
        assertThat(deltaOf(steak, "R11")).isEqualTo(-10);
        assertThat(deltaOf(porkCutlet, "R11")).isEqualTo(-10);

        // 상식적인 순서가 점수로 나온다 — 생선이 위, 붉은 고기·튀김이 아래.
        assertThat(scoreOf(mackerel)).isGreaterThan(scoreOf(steak));
        assertThat(scoreOf(tuna)).isGreaterThan(scoreOf(porkCutlet));
    }

    /**
     * 게이트가 있던 시절 오메가3 가점은 R01(건조)·R08(장벽) 뒤에만 있어서, 피부가 정상인
     * 사용자에게 고등어구이가 초밥·김밥과 같은 70점이었다. 등푸른생선이 좋은 이유가
     * 피부가 건조할 때만 생기지는 않는다.
     */
    @Test
    @DisplayName("오메가3는 정상 피부에서도 가점이고, 건조하면 그 위에 개인화가 더 붙는다")
    void omega3ScoresOnCalmSkinAndStacksWithPersonalization() {
        FoodAnalysis mackerel = standardFood("고등어구이", CookingMethod.GRILLED, false,
                nutrition(237, "16.3", 329, "0.0", "16.4", "4.6", "3.8"),
                IngredientTag.OMEGA3);

        assertThat(deltaOf(mackerel, "R14")).isEqualTo(4);

        // 건조(수분 35)·장벽 약화(35)면 R01·R08 이 그 위에 더 붙는다 — 독립이다.
        PlateEvaluation dry = engine.evaluate(
                new PlateContext(SkinMetrics.of(35, 50, 40, 40, 35), mackerel));
        assertThat(dry.appliedRuleCodes()).contains("R01", "R08", "R14");
        assertThat(dry.score()).isGreaterThan(scoreOf(mackerel));
    }

    @Test
    @DisplayName("포화지방 3단계 경계 — 초과부터 걸린다")
    void saturatedFatTiers() {
        assertThat(rulesOf(withSaturatedFat("3.9"))).doesNotContain("R11");
        assertThat(deltaOf(withSaturatedFat("4.0"), "R11")).isEqualTo(-2);
        assertThat(deltaOf(withSaturatedFat("8.3"), "R11")).isEqualTo(-7);
        assertThat(deltaOf(withSaturatedFat("12.4"), "R11")).isEqualTo(-10);
    }

    /**
     * 표준 테이블에서 못 찾은 음식은 포화지방이 0 이다 — AI 스키마에 그 값이 없다.
     * 모르는 값을 추정해 깎지 않는다는 뜻이고, 이 동작이 바뀌면 AI 추정치가 점수를
     * 흔들기 시작한다.
     */
    @Test
    @DisplayName("포화지방을 모르는 음식은 R11 이 걸리지 않는다")
    void unknownSaturatedFatDoesNotTrigger() {
        FoodAnalysis unknown = food("정체불명", CookingMethod.ETC, false,
                nutrition(500, "10.0", 300, "2.0"), List.of());   // 확장 영양이 전부 0

        assertThat(rulesOf(unknown)).doesNotContain("R11");
    }

    /**
     * HIGH_GI 태그만 보면 감자 된장국(70kcal · 탄수 10.6g)이 깎인다 — 태깅이 재료
     * 이름을 보기 때문이다. "재료가 있다"와 "많이 먹는다"는 다른 말이라 둘 다 물어본다.
     */
    @Test
    @DisplayName("R12 는 HIGH_GI 태그와 탄수 30g 이 둘 다 설 때만 걸린다")
    void refinedCarbNeedsBothSignals() {
        FoodAnalysis potatoSoup = standardFood("감자 된장국", CookingMethod.BOILED, false,
                nutrition(70, "4.4", 500, "3.1", "2.4", "10.6", "0.5"), IngredientTag.HIGH_GI);
        FoodAnalysis tteokbokki = standardFood("떡볶이", CookingMethod.GRILLED, false,
                nutrition(259, "6.3", 704, "7.9", "5.3", "46.7", "0.5"), IngredientTag.HIGH_GI);
        FoodAnalysis plainRice = food("현미밥", CookingMethod.ETC, false,
                nutrition(300, "6.0", 10, "0.0", "1.0", "65.0", "0.2"), List.of());

        assertThat(rulesOf(potatoSoup)).doesNotContain("R12");     // 태그는 있지만 탄수가 적다
        assertThat(deltaOf(tteokbokki, "R12")).isEqualTo(-4);
        assertThat(rulesOf(plainRice)).doesNotContain("R12");      // 탄수는 많지만 태그가 없다
    }

    // ---- R15 식이섬유 · R06 실측 비타민 (2026-08-18) ----

    /**
     * <b>밀도로 재는 이유가 이 테스트다.</b> 절대량으로 보면 감자튀김(5.8g)이
     * 샐러드(3.8g)를 이긴다 — 감자튀김이 468kcal 이고 샐러드가 293kcal 이기 때문이다.
     * 그러면 점수가 음식의 질이 아니라 크기를 재게 된다.
     */
    @Test
    @DisplayName("식이섬유는 밀도로 잰다 — 절대량이 더 많은 감자튀김이 나물을 이기지 않는다")
    void fiberIsMeasuredByDensityNotAmount() {
        // 표준 음식 테이블의 실제 값이다. 감자튀김이 식이섬유 절대량은 더 많다.
        FoodAnalysis friesFixture = micronutrientFood("감자튀김", CookingMethod.FRIED,
                468, "6.8", "5.8", 0, "0.0");
        FoodAnalysis beanSprouts = micronutrientFood("콩나물무침", CookingMethod.RAW,
                25, "2.1", "1.2", 0, "0.0");

        assertThat(deltaOf(beanSprouts, "R15")).isEqualTo(4);       // 밀도 4.8 → p75 통과
        assertThat(rulesOf(friesFixture)).doesNotContain("R15");    // 밀도 1.24 → 미달
    }

    @Test
    @DisplayName("식이섬유 2단계 — 100kcal 당 3.0g 이상 +4 · 5.0g 이상 +6")
    void fiberTiers() {
        assertThat(rulesOf(micronutrientFood("경계아래", CookingMethod.ETC, 100, "2.0", "2.9", 0, "0.0")))
                .doesNotContain("R15");
        assertThat(deltaOf(micronutrientFood("1단계", CookingMethod.ETC, 100, "2.0", "3.0", 0, "0.0"), "R15"))
                .isEqualTo(4);
        assertThat(deltaOf(micronutrientFood("2단계", CookingMethod.ETC, 100, "2.0", "5.0", 0, "0.0"), "R15"))
                .isEqualTo(6);
    }

    /**
     * 태그 판정을 지우지 않는 이유 — 표준 테이블에 없는 음식에서는 AI 가 사진에서 본
     * 재료가 유일한 단서다. 실측을 더하는 이유 — 태그만으로는 1,443종 중 42종밖에 못 잡았다.
     */
    @Test
    @DisplayName("R06 은 재료 태그와 실측 비타민 중 하나만 서도 걸리고, 둘 다 서도 한 번만 준다")
    void vitaminAcceptsEitherSignalButScoresOnce() {
        FoodAnalysis tagOnly = standardFood("브로콜리 무침", CookingMethod.RAW, false,
                nutrition(100, "2.0", 100, "0.0"), IngredientTag.ANTIOXIDANT);
        FoodAnalysis measuredOnly = micronutrientFood("시금치 된장국", CookingMethod.BOILED,
                36, "4.1", "0.0", 72, "0.0");                       // 비타민A 밀도 200
        FoodAnalysis both = micronutrientFood("당근 나물", CookingMethod.RAW,
                100, "2.0", "0.0", 200, "0.0");

        both.assignStandardFoodName("당근 나물");
        both.addIngredient(FoodIngredient.fromStandardTable("당근", IngredientTag.VITAMIN_A));

        assertThat(deltaOf(tagOnly, "R06")).isEqualTo(5);
        assertThat(deltaOf(measuredOnly, "R06")).isEqualTo(5);
        // 신호가 둘이라고 두 배 좋아지지 않는다 — R06 은 한 번만 실린다.
        assertThat(rulesOf(both)).filteredOn("R06"::equals).hasSize(1);
        assertThat(deltaOf(both, "R06")).isEqualTo(5);
    }

    @Test
    @DisplayName("확장 영양을 모르는 음식은 R15·R06 실측 경로가 걸리지 않는다")
    void unknownMicronutrientsDoNotTrigger() {
        FoodAnalysis unknown = food("정체불명", CookingMethod.ETC, false,
                nutrition(500, "10.0", 300, "2.0"), List.of());

        assertThat(rulesOf(unknown)).doesNotContain("R15", "R06");
    }

    private static FoodAnalysis micronutrientFood(String name, CookingMethod method, int kcal,
                                                  String protein, String fiber,
                                                  int vitaminA, String saturatedFat) {
        Nutrition nutrition = Nutrition.of(kcal, new BigDecimal(protein), BigDecimal.ZERO,
                        BigDecimal.ZERO, 100, BigDecimal.ZERO)
                .withMicronutrients(new BigDecimal(saturatedFat), new BigDecimal(fiber),
                        vitaminA, BigDecimal.ZERO, BigDecimal.ZERO);
        return food(name, method, false, nutrition, List.of());
    }

    private FoodAnalysis withSaturatedFat(String grams) {
        return food("시험용", CookingMethod.ETC, false,
                nutrition(300, "5.0", 300, "1.0", "20.0", "5.0", grams), List.of());
    }

    private int scoreOf(FoodAnalysis food) {
        return engine.evaluate(new PlateContext(CALM, food)).score();
    }

    private List<String> rulesOf(FoodAnalysis food) {
        return engine.evaluate(new PlateContext(CALM, food)).appliedRuleCodes();
    }

    private int deltaOf(FoodAnalysis food, String ruleCode) {
        return engine.evaluate(new PlateContext(CALM, food)).results().stream()
                .filter(result -> result.ruleCode().equals(ruleCode))
                .findFirst().orElseThrow()
                .delta();
    }

    /**
     * reason 은 결정론 템플릿이다 — 같은 입력이면 같은 문장. 현재 피부 지표와 음식
     * 특성을 잇되 인과를 단정하지 않는다("부담이 될 수 있어요"까지).
     */
    @Test
    @DisplayName("판정 이유가 현재 피부 상태와 음식 특성을 잇는 문장으로 붙는다")
    void reasonsConnectSkinStateAndFoodTraits() {
        // 표준 음식표가 확정한 한 끼는 AI 관찰 강도를 점수에도 문장에도 쓰지 않는다 —
        // 같은 사진이 HOT 과 MEDIUM 을 오가며 -16 과 -12 를 오가던 통로를 막은 결과다.
        FoodAnalysis hotStew = withTraits(demoStew(), Spiciness.HOT, Oiliness.UNKNOWN);

        PlateEvaluation evaluation = engine.evaluate(new PlateContext(SKIN, hotStew));

        assertThat(reasonOf(evaluation, "R02"))
                .contains("붉은기가 높은 상태").contains("매운 재료가 들어 있어").contains("될 수 있어요");

        // 표준 DB 에서 못 찾은 음식은 다른 단서가 없으므로 관찰 강도를 그대로 쓴다.
        FoodAnalysis unmatchedHot = withTraits(
                food("정체불명의 매운 볶음", CookingMethod.GRILLED, true,
                        nutrition(500, "10.0", 1000, "5.0"), List.of()),
                Spiciness.HOT, Oiliness.UNKNOWN);
        assertThat(reasonOf(engine.evaluate(new PlateContext(SKIN, unmatchedHot)), "R02"))
                .contains("강한 매운맛");
        // 1,850mg 은 새 2단계(>1700)라 수식어가 "매우 높은"으로 올라간다.
        assertThat(reasonOf(evaluation, "R04")).contains("나트륨이 매우 높은 편");
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

    /**
     * 예시 A 의 김치찌개 — 특성 없는 원형.
     * 영양값은 표준 음식 테이블의 실제 값이다(포화지방 6.0g 포함) — 사용자가 사진을
     * 찍었을 때 나오는 숫자와 이 테스트가 같은 것을 봐야 §18.7 재현이라 부를 수 있다.
     */
    private static FoodAnalysis demoStew() {
        return standardFood("돼지고기 김치찌개", CookingMethod.BOILED, true,
                nutrition(520, "28.5", 1850, "6.2", "24.0", "32.0", "6.0"),
                IngredientTag.PROBIOTIC, IngredientTag.CAPSAICIN);
    }

    private static FoodAnalysis withTraits(FoodAnalysis food, Spiciness spiciness, Oiliness oiliness) {
        food.assignTraits(FoodTraits.of(FoodGroup.ETC, PortionSize.UNKNOWN,
                spiciness, oiliness, ProcessingLevel.UNKNOWN));
        return food;
    }

    // ---- helpers ----

    /** 표준 음식표에서 못 찾은 한 끼. 재료는 AI 유래라 점수용 태그가 서지 않는다. */
    private static FoodAnalysis food(String name, CookingMethod method, boolean spicy,
                                     Nutrition nutrition, List<FoodIngredient> ingredients) {
        FoodAnalysis food = FoodAnalysis.create(
                null, name, "한식", nutrition, method, spicy, "{}");
        food.addIngredients(ingredients);
        return food;
    }

    /**
     * 표준 음식표가 확정한 한 끼. <b>점수용 태그는 표준 유래만 선다</b>(2026-08-18) —
     * 태그가 점수를 움직이는 테스트는 전부 이쪽을 쓴다. AI 유래 재료로 만들면 R06·R09·R14 가
     * 서지 않는 것이 이제 정상 동작이다.
     */
    private static FoodAnalysis standardFood(String name, CookingMethod method, boolean spicy,
                                             Nutrition nutrition, IngredientTag... tags) {
        FoodAnalysis food = FoodAnalysis.create(
                null, name, "한식", nutrition, method, spicy, "{}");
        food.assignStandardFoodName(name);
        for (IngredientTag tag : tags) {
            food.addIngredient(FoodIngredient.fromStandardTable(name, tag));
        }
        return food;
    }

    private static Nutrition nutrition(int kcal, String protein, int sodium, String sugar) {
        return Nutrition.of(kcal, new BigDecimal(protein), BigDecimal.ZERO,
                            BigDecimal.ZERO, sodium, new BigDecimal(sugar));
    }

    /** 지방·탄수와 포화지방까지 실은 픽스처. 표준 테이블에서 찾은 음식이 이 모양이다. */
    private static Nutrition nutrition(int kcal, String protein, int sodium, String sugar,
                                       String fat, String carb, String saturatedFat) {
        return Nutrition.of(kcal, new BigDecimal(protein), new BigDecimal(fat),
                            new BigDecimal(carb), sodium, new BigDecimal(sugar))
                .withMicronutrients(new BigDecimal(saturatedFat), BigDecimal.ZERO,
                                    0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
