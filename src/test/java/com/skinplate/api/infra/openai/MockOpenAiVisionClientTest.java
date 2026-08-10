package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock 은 네트워크가 끊긴 무대에서 쓰는 백업 플랜이다.
 * 그래서 "값이 나온다"로는 부족하다 — 그 값이 발표에서 말할 60점을 만들어야 한다.
 */
class MockOpenAiVisionClientTest {

    private final MockOpenAiVisionClient client = new MockOpenAiVisionClient();

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule()));

    @Test
    @DisplayName("피부 응답은 문서의 시연 지표를 그대로 돌려준다")
    void skinMatchesDemoMetrics() {
        OpenAiSkinResult result = client.analyzeSkin("무시된다", "image/jpeg");

        assertThat(result.faceDetected()).isTrue();
        assertThat(List.of(result.hydration(), result.oil(), result.redness(),
                           result.trouble(), result.barrier()))
                .containsExactly(38, 52, 64, 25, 78);
    }

    @Test
    @DisplayName("Mock 응답을 룰 엔진에 넣으면 발표에서 말할 60점이 나온다")
    void mockFoodReproducesDemoScore() {
        OpenAiSkinResult skin = client.analyzeSkin("무시된다", "image/jpeg");
        OpenAiFoodResult food = client.analyzeFood("무시된다", "image/jpeg");

        SkinMetrics metrics = SkinMetrics.of(skin.hydration(), skin.oil(),
                skin.redness(), skin.trouble(), skin.barrier());

        FoodAnalysis analysis = FoodAnalysis.create(null, food.foodName(),
                food.foodCategory(),
                Nutrition.of(food.nutrition().caloriesKcal(), food.nutrition().proteinG(),
                        food.nutrition().fatG(), food.nutrition().carbG(),
                        food.nutrition().sodiumMg(), food.nutrition().sugarG()),
                CookingMethod.valueOf(food.cookingMethod()), food.spicy(), "{}");
        food.ingredients().forEach(ingredient -> analysis.addIngredient(
                FoodIngredient.of(ingredient.name(), IngredientTag.valueOf(ingredient.tag()))));

        assertThat(engine.evaluate(new PlateContext(metrics, analysis)).score()).isEqualTo(60);
    }

    @Test
    @DisplayName("같은 입력에 항상 같은 값 — 무대에서 두 번 찍어도 같아야 한다")
    void isDeterministic() {
        assertThat(client.analyzeSkin("a", "image/jpeg"))
                .isEqualTo(client.analyzeSkin("b", "image/png"));
        assertThat(client.analyzeFood("a", "image/jpeg"))
                .isEqualTo(client.analyzeFood("b", "image/png"));
    }
}
