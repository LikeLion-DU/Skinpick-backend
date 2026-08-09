package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SodiumRule implements PlateRule {

    @Override public String code()    { return "R04"; }
    @Override public int    priority() { return 10; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighSodium();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int excess = context.nutrition().sodiumExcessMg();
        int penalty = Math.min(SODIUM_MAX_PENALTY, Math.abs(R04_SODIUM) + excess / SODIUM_STEP_MG);

        String action = context.food().isSoup()
                ? "국물을 절반만 남기면 Skin Plate 점수가 상승합니다."
                : "간이 센 반찬은 절반만 드셔보세요.";

        return RuleResult.caution(code(), -penalty, "나트륨 과다", action, GAIN_SOUP_HALF);
    }
}
