package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R09_PROBIOTIC;

@Component
public class ProbioticRule implements PlateRule {

    @Override public String code()    { return "R09"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasTag(IngredientTag.PROBIOTIC);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R09_PROBIOTIC, "발효식품 포함",
                "발효식품이 포함돼 있어요. 꾸준히 챙기면 도움이 될 수 있어요.");
    }
}
