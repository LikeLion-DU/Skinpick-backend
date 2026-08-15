package com.skinplate.api.infra.openai;

import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.dto.PlateComments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

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

    /** 사진이 몇 장이든 같은 값을 돌려준다. 시연에서 필요한 건 재현 가능한 60점이다. */
    @Override
    public OpenAiSkinResult analyzeSkin(List<FacePhoto> photos) {
        return new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                "피부 장벽은 양호하지만 건조하고 홍조가 관찰됩니다.");
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
}
