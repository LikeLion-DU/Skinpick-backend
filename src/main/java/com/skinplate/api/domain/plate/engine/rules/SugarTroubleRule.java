package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R03 · 당류 과다.
 *
 * <p><b>피부 게이트가 없다.</b> 트러블이 정상이어도 당류가 많은 한 끼는 그 자체로 부담이다 —
 * 게이트가 있던 시절에는 트러블 60 이하 사용자에게 당류 감점이 <b>한 번도 걸리지 않아서</b>,
 * 당류 33g 짜리 국물떡볶이가 아무 감점 없이 70점을 받았다.
 *
 * <p>개인화는 발동 여부가 아니라 <b>크기</b>로 한다 — 심각도 계수가 정상 0.6 · 중간 1.2 ·
 * 심함 1.5 로 붙는다. 트러블이 심한 사람이 같은 케이크를 먹으면 두 배 넘게 깎인다.
 */
@Component
public class SugarTroubleRule implements PlateRule {

    @Override public String code()    { return "R03"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighSugar();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        BigDecimal sugarG = context.nutrition().getSugarG();
        int trouble = context.skin().getTrouble();

        int delta = deltaOf(Nutrition.sugarTierOf(sugarG), trouble);

        // 단 음료를 물로 바꾸면 당류가 0.4 배가 된다. 그때 단계가 몇으로 내려가는지를
        // 지금 계산한다 — 임계를 25g 에서 15g 으로 내리면서 "줄여도 같은 단계에 남는"
        // 구간이 생겼다(37.5~40g). 옛 고정값 +7 은 그 구간에서 회복이 0 인데도 +7 이라
        // 말했고, 표준 음식표에 실제로 그런 음식이 3종 있다(고구마맛탕 등).
        int afterNoDrink = deltaOf(
                Nutrition.sugarTierOf(sugarG.multiply(SUGAR_AFTER_NO_DRINK)), trouble);
        int expectedGain = afterNoDrink - delta;

        String reason = reason(context, Nutrition.sugarTierOf(sugarG) >= 2);

        // 회복이 0 이면 행동 카드를 주지 않는다 — R04·R10 과 같은 규칙이다.
        if (expectedGain == 0) {
            return RuleResult.caution(code(), delta, "당류 과다", reason);
        }

        return RuleResult.caution(code(), delta, "당류 과다", reason,
                "단 음료 대신 물을 곁들이세요.", expectedGain);
    }

    /** 단계 → 델타. 경계는 Nutrition 이, 델타는 RuleConstants 가 소유한다. */
    private static int deltaOf(int tier, int trouble) {
        if (tier == 0) return 0;
        int baseDelta = R03_SUGAR_TROUBLE + (tier >= 2 ? R03_SUGAR_VERY_HIGH_EXTRA : 0);
        return SeverityCalculator.apply(baseDelta, trouble, true);
    }

    /**
     * 트러블이 정상인 사람에게 "지금 트러블 지표가 올라와 있는 상태에서" 라고 말하면 거짓이다.
     * 게이트를 뗀 대가로 문장도 두 갈래가 된다 — 음식만 말하는 쪽과 피부를 잇는 쪽.
     */
    private static String reason(PlateContext context, boolean veryHigh) {
        String sugarLevel = veryHigh ? "당류가 아주 많은 편이라" : "당류가 많은 편이라";

        if (SeverityCalculator.isMild(context.skin().getTrouble(), true)) {
            return sugarLevel + " 피부에 부담이 될 수 있어요.";
        }

        String troubleLevel = SeverityCalculator.isSevere(context.skin().getTrouble(), true)
                ? "많이 올라와 있는" : "올라와 있는";

        return "지금 트러블 지표가 " + troubleLevel + " 상태에서 " + sugarLevel + " 부담이 될 수 있어요.";
    }
}
