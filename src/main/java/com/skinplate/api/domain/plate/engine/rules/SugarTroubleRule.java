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
        // 당류 단계화 — 25~40g 은 기존과 동일, 40g 초과만 감점을 더한다.
        // 경계는 Nutrition 이 쥔다(다른 임계값 넷과 같은 자리), 델타만 RuleConstants 다.
        boolean veryHigh = context.nutrition().isVeryHighSugar();
        int baseDelta = R03_SUGAR_TROUBLE + (veryHigh ? R03_SUGAR_VERY_HIGH_EXTRA : 0);
        int delta = SeverityCalculator.apply(baseDelta, context.skin().getTrouble(), true);

        return RuleResult.caution(code(), delta,
                "당류 과다",
                reason(context, veryHigh),
                "단 음료 대신 물을 곁들이세요.", GAIN_WATER_NOT_SODA);
    }

    private static String reason(PlateContext context, boolean veryHigh) {
        String troubleLevel = SeverityCalculator.isSevere(context.skin().getTrouble(), true)
                ? "많이 올라와 있는" : "올라와 있는";
        String sugarLevel = veryHigh ? "당류가 아주 많은 편이라" : "당류가 많은 편이라";

        return "지금 트러블 지표가 " + troubleLevel + " 상태에서 " + sugarLevel + " 부담이 될 수 있어요.";
    }
}
