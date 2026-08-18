package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

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
        // 당류 단계화 — 40g 초과만 기본 델타에 더한다(심각도를 곱하기 전).
        // 경계는 Nutrition 이 쥔다(다른 임계값들과 같은 자리), 델타만 RuleConstants 다.
        boolean veryHigh = context.nutrition().isVeryHighSugar();
        int baseDelta = R03_SUGAR_TROUBLE + (veryHigh ? R03_SUGAR_VERY_HIGH_EXTRA : 0);
        int delta = SeverityCalculator.apply(baseDelta, context.skin().getTrouble(), true);

        return RuleResult.caution(code(), delta,
                "당류 과다",
                reason(context, veryHigh),
                "단 음료 대신 물을 곁들이세요.", GAIN_WATER_NOT_SODA);
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
