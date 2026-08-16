package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.dto.PlateComments;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 네트워크 없이 고정 응답을 돌려주는 시연 백업. (PRD §17.4)
 *
 * 스위치는 프로퍼티 하나다 — @Profile("mock") 과 섞으면 발표장에서
 * AI_MOCK=true 를 넣고 재기동해도 아무 일이 일어나지 않는다.
 *
 * 값은 문서의 시연 예시 그대로다. 이 값이 룰 엔진을 통과하면 60점이 나오고,
 * 그게 무대에서 말할 숫자다. 바꾸려면 테스트가 먼저 깨진다.
 */
@Primary
@Component
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "true")
public class MockOpenAiVisionClient implements VisionClient {

    /**
     * 13종 <b>전부</b>의 문장을 들고 있는다. 어떤 주제 조합이 뽑히든 서비스가 필요한 것만
     * 골라 쓰므로, 여기에 하나라도 빠지면 그 조합에서만 인사이트가 통째로 실패한다 —
     * 하필 무대에서 처음 밟는 조합이 그것일 수 있다.
     *
     * 시연 지표(수분 38 · 붉어짐 64)에서는 REDNESS · DRY 가 뽑힌다. 그 두 문장은
     * 화면에 그대로 읽히므로 무대에서 소리 내어 읽어도 어색하지 않아야 한다.
     */
    private static final Map<InsightCategory, String> INSIGHT_DESCRIPTIONS = Map.ofEntries(
            Map.entry(InsightCategory.DRY,
                    "수분 지표가 낮게 나왔어요. 오늘은 물기를 붙잡아 주는 케어를 조금 더 챙겨 보면 좋겠어요."),
            Map.entry(InsightCategory.OILY,
                    "유분이 평소보다 많이 올라와 있어요. 기름진 간식을 줄이는 것도 도움이 될 수 있어요."),
            Map.entry(InsightCategory.REDNESS,
                    "붉어짐이 눈에 띄게 기록됐어요. 자극이 적은 진정 케어와 매운 음식 줄이기를 함께 해보면 좋겠어요."),
            Map.entry(InsightCategory.TROUBLE,
                    "트러블 지표가 올라와 있어요. 당분이 많은 간식과 잦은 손길이 함께 영향을 줄 수 있어요."),
            Map.entry(InsightCategory.BARRIER_WEAK,
                    "장벽 지표가 낮은 편이에요. 세안 뒤 보습을 서두르는 습관이 도움이 될 수 있어요."),
            Map.entry(InsightCategory.DARK_CIRCLE,
                    "다크서클이 신경 쓰인다고 하셨어요. 늦은 시간 화면 보는 습관도 함께 영향을 줄 수 있어요."),
            Map.entry(InsightCategory.PIGMENTATION,
                    "톤이 고르지 않다고 느끼고 계시네요. 외출 전 자외선 차단이 가장 꾸준히 쌓이는 관리예요."),
            Map.entry(InsightCategory.ELASTICITY,
                    "탄력이 신경 쓰인다고 하셨어요. 단백질이 들어간 끼니를 꾸준히 챙기는 것이 도움이 될 수 있어요."),
            Map.entry(InsightCategory.PUFFINESS,
                    "붓기가 고민이라고 하셨어요. 짠 음식과 늦은 밤 국물이 함께 기록되는 날이 많아요."),
            Map.entry(InsightCategory.SLEEP,
                    "수면이 부족하다고 기록되고 있어요. 잠이 짧은 날엔 피부 컨디션도 함께 흔들리기 쉬워요."),
            Map.entry(InsightCategory.STRESS,
                    "스트레스가 높다고 기록되고 있어요. 짧은 산책이나 호흡만으로도 하루의 결이 달라질 수 있어요."),
            Map.entry(InsightCategory.EXERCISE,
                    "운동을 거의 안 한다고 기록되고 있어요. 가벼운 움직임도 순환에 도움이 될 수 있어요."),
            Map.entry(InsightCategory.WATER,
                    "수분 섭취가 부족하다고 기록되고 있어요. 물 한두 잔을 더 챙기는 것부터 시작해 보세요."));

    /**
     * 사진이 몇 장이든 같은 값을 돌려준다. 시연에서 필요한 건 재현 가능한 60점이다.
     *
     * 피부 타입은 <b>DRY</b> 다. 설계 문서의 예시는 COMBINATION 이지만, 시연 지표에서
     * {@code SkinType.observe} 가 도출하는 값은 DRY 이고 갭 카드도 "지성이라고
     * 생각하셨지만… 건조" 로 뜬다. Mock 만 COMBINATION 이면 한 화면에서 타입이 두 개로
     * 갈라져 보인다 — 무대에서 설명할 수 없는 종류의 어긋남이다.
     *
     * traits 에 SENSITIVE_TENDENCY 를 두는 근거는 붉어짐 64 다(임계 60 초과).
     * 빈 배열로 두면 traits 칩이 시연에서 한 번도 안 그려진다.
     */
    @Override
    public OpenAiSkinResult analyzeSkin(List<FacePhoto> photos) {
        return new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                new OpenAiSkinResult.MetricEvidence(
                        List.of("볼과 입가에 부분적인 각질이 보임"),
                        List.of("T존에 중간 정도의 광택이 보임"),
                        List.of("코와 볼 주변에 붉은기가 뚜렷함"),
                        List.of("작은 융기가 소수만 보임"),
                        List.of("전반적인 피부결이 균일한 편임")),
                new OpenAiSkinResult.SkinTypeResult("DRY", List.of("SENSITIVE_TENDENCY")),
                new OpenAiSkinResult.SkinAgeAnalysis(29,
                        axis(72, "볼과 이마의 피부결이 균일한 편임"),
                        axis(76, "턱선의 처짐이 뚜렷하지 않음"),
                        axis(28, "이마에 얕은 선이 일부 보임"),
                        axis(58, "볼 주변 톤이 다소 고르지 않음"),
                        axis(42, "코 주변 모공이 일부 보임"),
                        axis(35, "볼에 작은 색소가 일부 보임"),
                        axis(60, "코 주변에 붉은기가 뚜렷함"),
                        axis(30, "작은 트러블 흔적이 일부 보임"),
                        "피부결과 탄력이 좋은 편이고 눈에 띄는 주름도 많지 않아 비교적 젊은 피부 외관으로 보여요. "
                                + "다만 볼 주변의 붉은기와 톤 불균일이 피부 나이를 조금 높이는 요인으로 보여요."),
                "피부 장벽은 양호하지만 건조하고 홍조가 관찰됩니다.");
    }

    private static OpenAiSkinResult.Axis axis(int score, String evidence) {
        return new OpenAiSkinResult.Axis(score, List.of(evidence));
    }

    @Override
    public OpenAiFoodResult analyzeFood(String base64Image, String mediaType) {
        return new OpenAiFoodResult(
                true,
                "돼지고기 김치찌개",
                "한식/찌개",
                "BOILED",
                true,
                List.of(new OpenAiFoodResult.Ingredient("돼지고기", "ETC"),
                        new OpenAiFoodResult.Ingredient("김치", "PROBIOTIC"),
                        new OpenAiFoodResult.Ingredient("두부", "ETC"),
                        new OpenAiFoodResult.Ingredient("고춧가루", "CAPSAICIN")),
                new OpenAiFoodResult.Nutrition(520, new BigDecimal("28.5"), new BigDecimal("24.0"),
                        new BigDecimal("32.0"), 1850, new BigDecimal("6.2")));
    }

    /** 60점 김치찌개 시나리오에 맞는 고정 문장. 무대에서 읽어도 어색하지 않아야 한다. */
    @Override
    public PlateComments generateComments(String userContext) {
        return new PlateComments(
                "나트륨이 조금 높았어요. 다음 식사에는 국물을 줄이고 채소를 곁들여 보세요!",
                "발효식품과 단백질을 잘 챙긴 하루였어요. 내일은 나트륨을 조금만 줄여볼까요?");
    }

    /**
     * userContext 와 무관하게 13종 전부를 돌려준다. 서비스가 선정된 주제만 골라 쓰므로
     * 이쪽이 조합을 맞출 필요가 없다 — Mock 이 조합을 따라가기 시작하면 결국 주제 선정
     * 규칙을 두 벌 갖게 된다.
     */
    @Override
    public SkinInsightSentences generateSkinInsight(String userContext) {
        return new SkinInsightSentences(
                "오늘은 수분과 붉은기가 함께 신경 쓰이는 상태예요. 무리하지 않는 선에서 하나씩 챙겨 봐요.",
                Arrays.stream(InsightCategory.values())
                        .map(category -> new SkinInsightSentences.Topic(
                                category.name(), INSIGHT_DESCRIPTIONS.get(category)))
                        .toList());
    }
}
