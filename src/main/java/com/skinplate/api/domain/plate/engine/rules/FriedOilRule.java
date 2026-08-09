package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class FriedOilRule implements PlateRule {

    @Override public String code()    { return "R07"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isOily() && context.food().isFried();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(R07_FRIED_OIL, context.skin().getOil(), true);

        return RuleResult.caution(code(), delta,
                "튀김 조리",
                "튀김옷을 일부 제거해 보세요.", GAIN_REMOVE_BATTER);
    }
}
