package com.skinplate.api.domain.plate.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * <b>같은 음식 · 같은 피부면 언제나 같은 점수가 나온다.</b>
 *
 * <p>실사진 E2E 에서 같은 떡볶이 사진이 50 · 54 · 55 · 58 점을 냈다. 영양값은 표준 음식표가
 * 이미 고정하고 있었는데, <b>재료 태그와 매운맛 강도가 AI 유래</b>라 회차마다 달랐다 —
 * ANTIOXIDANT 가 오면 R06 이 +5, PROBIOTIC 이면 R09 가 +4, 강도가 HOT↔MEDIUM 이면
 * R02 가 ±4 움직였다. 그 네 응답을 그대로 재현해 점수가 하나로 모이는지 본다.
 *
 * <p>{@code FoodAnalysisService.toEntity} 를 실제로 태운다 — 엔티티만 조립해 보면
 * "표준표를 태우는 경로"가 빠져서, 배선이 끊겨도 테스트는 통과한다.
 */
class DeterministicScoringTest {

    /** 시연 지표. 홍조 64 라 R02 가 걸리고, 강도 계수가 살아 있으면 여기서 드러난다. */
    private static final SkinMetrics DEMO = SkinMetrics.of(38, 52, 64, 25, 78);
    private static final SkinMetrics CALM = SkinMetrics.of(60, 50, 40, 40, 60);
    private static final SkinMetrics SEVERE = SkinMetrics.of(35, 85, 85, 85, 35);

    private final FoodAnalysisService foodAnalysisService =
            new FoodAnalysisService(mock(VisionClient.class), new ObjectMapper());

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule(),
            new HighCalorieRule(), new SaturatedFatRule(), new RefinedCarbRule(),
            new FiberRule(), new Omega3FoodRule()));

    /**
     * 실사진 E2E 에서 실제로 관측된 네 응답이다. 같은 떡볶이 사진 한 장에서 나왔고,
     * 그때 점수가 50 / 54 / 55 / 58 로 갈렸다.
     */
    private static final List<OpenAiFoodResult> OBSERVED_TTEOKBOKKI = List.of(
            tteokbokki("HOT",    ingredient("쌀떡", "HIGH_GI"), ingredient("어묵", "GLUTEN")),
            tteokbokki("HOT",    ingredient("쌀떡", "HIGH_GI"), ingredient("고명", "ANTIOXIDANT")),
            tteokbokki("MEDIUM", ingredient("쌀떡", "HIGH_GI"), ingredient("어묵", "PROBIOTIC")),
            tteokbokki("MEDIUM", ingredient("쌀떡", "HIGH_GI"), ingredient("어묵", "GLUTEN")));

    @Test
    @DisplayName("떡볶이 사진 네 번 — AI 응답이 달라도 점수가 하나로 모인다 (E2E 의 50~58 변동이 사라진다)")
    void sameStandardFood_sameScore_regardlessOfAiTags() {
        Set<Integer> scores = OBSERVED_TTEOKBOKKI.stream()
                .map(aiResult -> score(aiResult, DEMO))
                .collect(Collectors.toSet());

        assertThat(scores).hasSize(1);
    }

    @Test
    @DisplayName("AI 태그가 바뀌어도 적용 룰 목록 자체가 같다 — R06·R09 가 켜졌다 꺼지지 않는다")
    void aiTags_doNotChangeWhichRulesFire() {
        Set<List<String>> appliedRules = OBSERVED_TTEOKBOKKI.stream()
                .map(aiResult -> evaluate(aiResult, DEMO).appliedRuleCodes())
                .collect(Collectors.toSet());

        assertThat(appliedRules).hasSize(1);
        // 표준표의 떡볶이 태그는 CAPSAICIN·HIGH_GI 뿐이다. AI 가 준 ANTIOXIDANT·PROBIOTIC 은
        // 화면에만 남고 점수에는 서지 않는다.
        assertThat(appliedRules.iterator().next()).doesNotContain("R06", "R09");
    }

    @Test
    @DisplayName("AI 재료는 화면에 그대로 남는다 — 점수에서만 뺐지 데이터를 버리지 않았다")
    void aiIngredients_surviveForDisplay() {
        FoodAnalysis food = foodAnalysisService.toEntity(null, OBSERVED_TTEOKBOKKI.get(2));

        assertThat(food.getIngredients()).extracting(ingredient -> ingredient.getName())
                .contains("쌀떡", "어묵");
        // 그런데 그 AI 태그(PROBIOTIC)는 점수용 판정에서 서지 않는다.
        assertThat(food.hasTag(com.skinplate.api.domain.food.entity.IngredientTag.PROBIOTIC))
                .isFalse();
        assertThat(food.isStandardMatched()).isTrue();
    }

    @Test
    @DisplayName("같은 음식·같은 피부를 열 번 계산해도 같은 값이다 — 세 피부 프로필 전부")
    void repeatedEvaluation_isStable() {
        for (SkinMetrics skin : List.of(CALM, DEMO, SEVERE)) {
            Set<Integer> scores = IntStream.range(0, 10)
                    .mapToObj(index -> score(OBSERVED_TTEOKBOKKI.get(index % 4), skin))
                    .collect(Collectors.toSet());

            assertThat(scores).as("피부 %s".formatted(skin.getRedness())).hasSize(1);
        }
    }

    @Test
    @DisplayName("개인화는 그대로다 — 같은 떡볶이가 피부에 따라 다른 점수를 낸다")
    void personalizationSurvivesDeterminism() {
        int calm = score(OBSERVED_TTEOKBOKKI.get(0), CALM);
        int demo = score(OBSERVED_TTEOKBOKKI.get(0), DEMO);
        int severe = score(OBSERVED_TTEOKBOKKI.get(0), SEVERE);

        // 결정론은 "누구에게나 같은 점수"가 아니라 "같은 사람에게 언제나 같은 점수"다.
        assertThat(calm).isGreaterThan(demo);
        assertThat(demo).isGreaterThan(severe);
    }

    /**
     * 표준표에서 못 찾은 음식은 태그 룰이 전부 꺼진다 — AI 태그를 점수에 섞지 않는다는
     * 규칙의 다른 얼굴이다. 영양값은 AI 추정치라 여전히 흔들릴 수 있고, 그건 표준표를
     * 넓히는 것 말고 해결 방법이 없다.
     */
    @Test
    @DisplayName("표준표에 없는 음식은 AI 태그로 가점받지 않는다")
    void unmatchedFood_getsNoTagBasedScore() {
        OpenAiFoodResult unmatched = new OpenAiFoodResult(true,
                "정체불명의 새 요리", "한식", "GRILLED", false,
                List.of(ingredient("연어", "OMEGA3"), ingredient("브로콜리", "ANTIOXIDANT")),
                new OpenAiFoodResult.Nutrition(400, new BigDecimal("10.0"), new BigDecimal("5.0"),
                        new BigDecimal("20.0"), 300, new BigDecimal("2.0")),
                "ETC", "MEDIUM", "NONE", "LOW", "MINIMALLY_PROCESSED");

        FoodAnalysis food = foodAnalysisService.toEntity(null, unmatched);

        assertThat(food.isStandardMatched()).isFalse();
        assertThat(evaluate(unmatched, CALM).appliedRuleCodes()).doesNotContain("R06", "R14");
        // 재료는 화면용으로 남아 있다.
        assertThat(food.getIngredients()).hasSize(2);
    }

    // ---- helpers ----

    private int score(OpenAiFoodResult aiResult, SkinMetrics skin) {
        return evaluate(aiResult, skin).score();
    }

    private PlateEvaluation evaluate(OpenAiFoodResult aiResult, SkinMetrics skin) {
        return engine.evaluate(new PlateContext(skin, foodAnalysisService.toEntity(null, aiResult)));
    }

    private static OpenAiFoodResult tteokbokki(String spiciness,
                                               OpenAiFoodResult.Ingredient... ingredients) {
        // 영양값은 표준표와 겹치지 않는 숫자로 둔다 — 표준표가 이겼는지 보이게.
        return new OpenAiFoodResult(true, "떡볶이", "분식", "GRILLED", true,
                List.of(ingredients),
                new OpenAiFoodResult.Nutrition(999, new BigDecimal("99.0"), new BigDecimal("99.0"),
                        new BigDecimal("99.0"), 999, new BigDecimal("99.0")),
                "SNACK", "MEDIUM", spiciness, "MEDIUM", "PROCESSED");
    }

    private static OpenAiFoodResult.Ingredient ingredient(String name, String tag) {
        return new OpenAiFoodResult.Ingredient(name, tag);
    }
}
