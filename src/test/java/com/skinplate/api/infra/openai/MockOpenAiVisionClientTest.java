package com.skinplate.api.infra.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.food.service.StandardFoodTable;
import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.FacePhotoType;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock 은 네트워크가 끊긴 무대에서 쓰는 백업 플랜이다.
 * 그래서 "값이 나온다"로는 부족하다 — 그 값이 발표에서 말할 58점을 만들어야 한다.
 */
class MockOpenAiVisionClientTest {

    private static final List<FacePhoto> PHOTOS = List.of(
            new FacePhoto(FacePhotoType.FRONT, "무시된다", "image/jpeg"),
            new FacePhoto(FacePhotoType.LEFT, "무시된다", "image/jpeg"),
            new FacePhoto(FacePhotoType.RIGHT, "무시된다", "image/jpeg"));

    private final MockOpenAiVisionClient client = new MockOpenAiVisionClient();

    private final FoodAnalysisService foodAnalysisService =
            new FoodAnalysisService(client, new ObjectMapper());

    // **프로덕션과 같은 목록이어야 한다.** 하나라도 빠지면 이 테스트가 무대에 없는
    // 숫자를 "발표에서 말할 점수"로 고정한다 — 실제 컨텍스트는 @Component 를 전부 모은다.
    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule(),
            new HighCalorieRule(), new SaturatedFatRule(), new RefinedCarbRule(),
            new Omega3FoodRule(), new FiberRule()));

    @Test
    @DisplayName("피부 응답은 문서의 시연 지표를 그대로 돌려준다")
    void skinMatchesDemoMetrics() {
        OpenAiSkinResult result = client.analyzeSkin(PHOTOS);

        assertThat(result.faceDetected()).isTrue();
        assertThat(List.of(result.hydration(), result.oil(), result.redness(),
                           result.trouble(), result.barrier()))
                .containsExactly(38, 52, 64, 25, 78);
    }

    @Test
    @DisplayName("확장 필드가 하나도 비지 않는다 — 비면 그 칸만 무대에서 빈 채로 그려진다")
    void skinFillsEveryExtendedField() {
        OpenAiSkinResult result = client.analyzeSkin(PHOTOS);

        assertThat(result.metricEvidence()).isNotNull();
        assertThat(List.of(result.metricEvidence().hydration(), result.metricEvidence().oil(),
                           result.metricEvidence().redness(), result.metricEvidence().trouble(),
                           result.metricEvidence().barrier()))
                .allSatisfy(evidence -> assertThat(evidence).isNotEmpty());

        OpenAiSkinResult.SkinAgeAnalysis age = result.skinAgeAnalysis();
        assertThat(age.estimatedSkinAge()).isBetween(18, 80);
        assertThat(age.ageAssessment()).isNotBlank();
        assertThat(List.of(age.skinTexture(), age.elasticity(), age.wrinkles(), age.skinTone(),
                           age.pores(), age.pigmentation(), age.redness(), age.blemishMarks()))
                .allSatisfy(axis -> assertThat(axis.evidence()).isNotEmpty());
    }

    @Test
    @DisplayName("Mock 지표가 무대에서 말할 타입·상태를 만든다 — 건성 + 붉은기")
    void skinMetricsProduceTheDemoTypeAndConditions() {
        OpenAiSkinResult result = client.analyzeSkin(PHOTOS);

        SkinMetrics metrics = SkinMetrics.of(result.hydration(), result.oil(),
                result.redness(), result.trouble(), result.barrier());

        // Mock 은 타입을 말하지 않는다. 서버가 이 지표에서 낸다 — 그 값이 무대의 대사다.
        assertThat(SkinType.observe(metrics)).isEqualTo(SkinType.DRY);
        assertThat(SkinTrait.observe(metrics,
                        result.skinAgeAnalysis().skinTexture().score(),
                        result.skinAgeAnalysis().pigmentation().score()))
                .containsExactly(SkinTrait.REDNESS_PRONE);
    }

    @Test
    @DisplayName("Mock 응답을 룰 엔진에 넣으면 발표에서 말할 58점이 나온다")
    void mockFoodReproducesDemoScore() {
        OpenAiSkinResult skin = client.analyzeSkin(PHOTOS);
        OpenAiFoodResult food = client.analyzeFood("무시된다", "image/jpeg");

        SkinMetrics metrics = SkinMetrics.of(skin.hydration(), skin.oil(),
                skin.redness(), skin.trouble(), skin.barrier());

        // 시연 음식이 표준 테이블에 실제로 있어야 한다. Mock 의 AI 추정값과 표준값이
        // 일부러 같은 숫자라, 이 확인이 없으면 테이블이 통째로 안 실려도 58 이 나온다.
        assertThat(StandardFoodTable.find(food.foodName())).isPresent();

        // 서비스를 통해 만든다. 손으로 Nutrition 을 조립하면 표준 음식 테이블을 건너뛰어,
        // 표준값이 시연 음식을 다른 값으로 덮어써도 이 테스트가 모른 채 통과한다.
        // 실제 무대는 이 경로로 흐른다.
        FoodAnalysis analysis = foodAnalysisService.toEntity(null, food);

        assertThat(engine.evaluate(new PlateContext(metrics, analysis)).score()).isEqualTo(58);
    }

    @Test
    @DisplayName("Mock 음식의 특성이 채워져 있고 spiciness 는 MEDIUM 이다 — 화면·AI 문장이 이 값을 읽는다")
    void mockFoodFillsTraits() {
        OpenAiFoodResult food = client.analyzeFood("무시된다", "image/jpeg");

        assertThat(food.foodGroup()).isEqualTo("SOUP_STEW");
        assertThat(food.portionSize()).isEqualTo("MEDIUM");
        assertThat(food.spiciness()).isEqualTo("MEDIUM");   // 표준 매칭 뒤엔 점수에 안 쓰인다
        assertThat(food.oiliness()).isEqualTo("MEDIUM");
        assertThat(food.processingLevel()).isEqualTo("MINIMALLY_PROCESSED");
    }

    @Test
    @DisplayName("같은 입력에 항상 같은 값 — 무대에서 두 번 찍어도 같아야 한다")
    void isDeterministic() {
        assertThat(client.analyzeSkin(PHOTOS))
                .isEqualTo(client.analyzeSkin(PHOTOS));
        assertThat(client.analyzeFood("a", "image/jpeg"))
                .isEqualTo(client.analyzeFood("b", "image/png"));
    }

    @Test
    @DisplayName("인사이트는 13종 전부에 문장을 갖는다 — 하나라도 비면 그 주제 조합에서만 무대에서 죽는다")
    void insightCoversEveryCategory() {
        SkinInsightSentences sentences = client.generateSkinInsight("무시된다");

        assertThat(sentences.summary()).isNotBlank();
        assertThat(sentences.topics()).extracting(SkinInsightSentences.Topic::category)
                .containsExactly(Stream.of(InsightCategory.values()).map(Enum::name).toArray(String[]::new));
        assertThat(sentences.topics()).allSatisfy(topic ->
                assertThat(topic.description()).as("%s 의 문장", topic.category()).isNotBlank());
    }
}
