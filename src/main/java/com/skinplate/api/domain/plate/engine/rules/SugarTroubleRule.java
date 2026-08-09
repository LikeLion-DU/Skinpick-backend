package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SugarTroubleRule implements PlateRule {

    @Override public String code()    { return "R03"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().hasTrouble() && context.nutrition().isHighSugar();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(
                R03_SUGAR_TROUBLE, context.skin().getTrouble(), true);

        return RuleResult.caution(code(), delta,
                "당류 과다",
                "단 음료 대신 물을 곁들이세요.", GAIN_WATER_NOT_SODA);
    }
}
