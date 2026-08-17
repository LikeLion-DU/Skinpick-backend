package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R05_PROTEIN;

@Component
public class ProteinRule implements PlateRule {

    @Override public String code()    { return "R05"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighProtein();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R05_PROTEIN, "단백질 충분",
                "단백질이 충분한 한 끼예요. 피부 컨디션 유지에 도움이 될 수 있어요.");
    }
}
