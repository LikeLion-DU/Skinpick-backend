package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R06_VITAMIN;

@Component
public class VitaminRule implements PlateRule {

    @Override public String code()    { return "R06"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasAnyTag(
                IngredientTag.VITAMIN_C, IngredientTag.VITAMIN_A, IngredientTag.ANTIOXIDANT);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R06_VITAMIN, "비타민 풍부");
    }
}
