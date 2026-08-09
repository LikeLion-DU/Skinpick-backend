package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class HydrationFoodRule implements PlateRule {

    @Override public String code()    { return "R01"; }
    @Override public int    priority() { return 30; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isDry()
                && context.food().hasAnyTag(IngredientTag.OMEGA3, IngredientTag.VITAMIN_A);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        // hydration은 낮을수록 나쁘므로 higherIsWorse = false
        int delta = SeverityCalculator.apply(
                R01_HYDRATION_FOOD, context.skin().getHydration(), false);

        return RuleResult.good(code(), delta, "수분 보충 재료");
    }
}
