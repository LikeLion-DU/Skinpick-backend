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
        // 점수식은 기존 초과량 비례 그대로다 — 이미 비례 감점이라 단계를 점수에
        // 겹치면 같은 초과가 두 번 벌을 받는다. 단계(VERY_HIGH)는 문장에만 쓴다.
        int excess = context.nutrition().sodiumExcessMg();
        int penalty = Math.min(SODIUM_MAX_PENALTY, Math.abs(R04_SODIUM) + excess / SODIUM_STEP_MG);

        String action = context.food().isSoup()
                ? "국물을 절반만 남기면 Skin Plate 점수가 상승합니다."
                : "간이 센 반찬은 절반만 드셔보세요.";

        String sodiumLevel = context.nutrition().isVeryHighSodium() ? "매우 높은" : "높은";
        String reason = "나트륨이 " + sodiumLevel + " 편이라 피부 컨디션에 부담이 될 수 있어요.";

        return RuleResult.caution(code(), -penalty, "나트륨 과다", reason, action, GAIN_SOUP_HALF);
    }
}
