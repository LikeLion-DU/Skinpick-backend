package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Spiciness;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SpicyRednessRule implements PlateRule {

    @Override public String code()    { return "R02"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().hasRedness() && context.food().isSpicyFood();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        // 발동은 spicy/CAPSAICIN(표준 테이블이 이긴다), 강도는 AI 관찰값이 정한다.
        // UNKNOWN·NONE(캡사이신으로만 발동)은 1.0 — 특성이 없던 시절과 같은 점수다.
        Spiciness spiciness = context.food().scoringTraits().getSpiciness();
        int delta = SeverityCalculator.apply(
                R02_SPICY_REDNESS, context.skin().getRedness(), true, intensityOf(spiciness));

        // LESS_SPICY 는 spicy 를 끄고 CAPSAICIN 을 빼므로 이 룰이 통째로 사라진다 —
        // 회복치는 곧 이 감점의 절댓값이다. 고정값 6 을 싣던 시절에는 홍조 64 에서
        // 실제로 12 가 올랐고, 강도가 HOT 이면 16 이었다. 카드가 확인 가능한 거짓이었다.
        return RuleResult.caution(code(), delta,
                "매운맛 자극",
                reason(context, spiciness),
                "매운 양념을 덜어내고 드셔보세요.", -delta);
    }

    private static double intensityOf(Spiciness spiciness) {
        return switch (spiciness) {
            case MILD -> SPICINESS_MILD_FACTOR;
            case HOT -> SPICINESS_HOT_FACTOR;
            case NONE, MEDIUM, UNKNOWN -> 1.0;
        };
    }

    /** 인과를 단정하지 않는다 — "부담이 될 수 있어요"까지만. (PRD §18.9 표현 규칙) */
    private static String reason(PlateContext context, Spiciness spiciness) {
        String rednessLevel = SeverityCalculator.isSevere(context.skin().getRedness(), true)
                ? "많이 높은" : "높은";
        String strength = switch (spiciness) {
            case HOT -> "강한 매운맛이 들어 있어";
            case MILD -> "약한 매운맛이 들어 있어";
            default -> "매운 재료가 들어 있어";
        };

        return "지금 붉은기가 " + rednessLevel + " 상태에서 " + strength + " 부담이 될 수 있어요.";
    }
}
