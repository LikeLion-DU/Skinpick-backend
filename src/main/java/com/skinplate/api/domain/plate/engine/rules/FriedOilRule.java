package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Oiliness;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

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
        return context.skin().isOily()
                && (context.food().isFried()
                        || context.food().getTraits().getOiliness() == Oiliness.HIGH);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        boolean fried = context.food().isFried();

        // 튀김이 아닌 기름진 음식은 약한 계수로 — 튀김과 같은 벌을 주면
        // "튀김 조리"라는 룰 이름부터 거짓이 된다.
        int delta = SeverityCalculator.apply(R07_FRIED_OIL, context.skin().getOil(), true,
                fried ? 1.0 : OILINESS_NON_FRIED_FACTOR);

        String oilLevel = SeverityCalculator.isSevere(context.skin().getOil(), true)
                ? "많이 높은" : "높은";

        if (fried) {
            return RuleResult.caution(code(), delta,
                    "튀김 조리",
                    "지금 유분이 " + oilLevel + " 상태에서 튀김 조리가 부담이 될 수 있어요.",
                    "튀김옷을 일부 제거해 보세요.", GAIN_REMOVE_BATTER);
        }

        // 튀김옷이 없으니 REMOVE_BATTER 행동 카드가 성립하지 않는다 — 주의만 준다.
        return RuleResult.caution(code(), delta,
                "기름진 음식",
                "지금 유분이 " + oilLevel + " 상태에서 기름기가 많은 음식이라 부담이 될 수 있어요.");
    }
}
