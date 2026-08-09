package com.skinplate.api.domain.plate.engine.rules;

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
        int delta = SeverityCalculator.apply(
                R02_SPICY_REDNESS, context.skin().getRedness(), true);

        return RuleResult.caution(code(), delta,
                "매운맛 자극",
                "매운 양념을 덜어내고 드셔보세요.", GAIN_LESS_SPICY);
    }
}
