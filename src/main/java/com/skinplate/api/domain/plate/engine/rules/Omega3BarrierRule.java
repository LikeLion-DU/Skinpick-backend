package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class Omega3BarrierRule implements PlateRule {

    @Override public String code()    { return "R08"; }
    @Override public int    priority() { return 30; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isBarrierWeak() && context.food().hasTag(IngredientTag.OMEGA3);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(
                R08_OMEGA3_BARRIER, context.skin().getBarrier(), false);

        return RuleResult.good(code(), delta, "오메가3 함유");
    }
}
