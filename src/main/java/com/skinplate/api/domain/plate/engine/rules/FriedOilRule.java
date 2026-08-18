package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Oiliness;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R07 · 튀김 · 기름진 음식.
 *
 * <p><b>피부 게이트가 없다.</b> 유분이 정상이어도 튀김은 그 자체로 부담이다 — 게이트가
 * 있던 시절에는 유분 70 이하 사용자에게 <b>감자튀김과 닭가슴살 샐러드가 똑같이 70점</b>이었다.
 *
 * <p>개인화는 발동 여부가 아니라 <b>크기</b>로 한다 — 정상 -6 · 중간 -12 · 심함 -15.
 */
@Component
public class FriedOilRule implements PlateRule {

    @Override public String code()    { return "R07"; }
    @Override public int    priority() { return 20; }

    /**
     * 튀김(FRIED)이거나, 튀김이 아니어도 관찰된 기름기가 HIGH 면 발동한다 —
     * 삼겹살 구이(GRILLED)가 기존의 사각지대였다. UNKNOWN 은 발동하지 않으므로
     * 특성이 없던 시절 행은 기존과 동일하다.
     */
    @Override
    public boolean supports(PlateContext context) {
        return context.food().isFried()
                || context.food().scoringTraits().getOiliness() == Oiliness.HIGH;
    }

    @Override
    public RuleResult apply(PlateContext context) {
        boolean fried = context.food().isFried();

        // 튀김이 아닌 기름진 음식은 약한 계수로 — 튀김과 같은 벌을 주면
        // "튀김 조리"라는 룰 이름부터 거짓이 된다.
        int delta = SeverityCalculator.apply(R07_FRIED_OIL, context.skin().getOil(), true,
                fried ? 1.0 : OILINESS_NON_FRIED_FACTOR);

        if (fried) {
            // REMOVE_BATTER 는 조리법을 GRILLED 로, 기름기를 MEDIUM 으로 바꿔 이 룰을
            // 통째로 끈다 — 회복치는 곧 이 감점의 절댓값이다. 고정값 5 를 싣던 시절에는
            // 유분이 정상이면 6, 심하면 15 가 올랐다. 게이트를 떼면서 그 카드가 모든
            // 사용자에게 보이게 됐고, 그만큼 틀린 숫자도 모든 화면에 실렸다.
            return RuleResult.caution(code(), delta,
                    "튀김 조리",
                    reason(context, "튀김 조리라", "튀김 조리가"),
                    "튀김옷을 일부 제거해 보세요.", -delta);
        }

        // 튀김옷이 없으니 REMOVE_BATTER 행동 카드가 성립하지 않는다 — 주의만 준다.
        return RuleResult.caution(code(), delta,
                "기름진 음식",
                reason(context, "기름기가 많은 음식이라", "기름기가 많은 음식이라"));
    }

    /**
     * 유분이 정상인 사람에게 "지금 유분이 높은 상태에서" 라고 말하면 거짓이다.
     * 게이트를 뗀 대가로 문장도 두 갈래가 된다 — 음식만 말하는 쪽과 피부를 잇는 쪽.
     */
    private static String reason(PlateContext context, String foodOnly, String withSkin) {
        if (SeverityCalculator.isMild(context.skin().getOil(), true)) {
            return foodOnly + " 피부에 부담이 될 수 있어요.";
        }

        String oilLevel = SeverityCalculator.isSevere(context.skin().getOil(), true)
                ? "많이 높은" : "높은";

        return "지금 유분이 " + oilLevel + " 상태에서 " + withSkin + " 부담이 될 수 있어요.";
    }
}
