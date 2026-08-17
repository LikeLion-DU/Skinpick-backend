package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R10 · 고열량 (PRD §18.6 확장 룰 — 2026-08-17 구현).
 *
 * 피부 지표와 직접 매지 않는 <b>보조 룰</b>이다 — 고열량↔특정 피부 지표의 매핑을
 * 이 앱이 증명할 수 없어 심각도 계수 없이 고정 -5 로 둔다. 같은 이유로
 * ConcernRules 에도 매지 않는다(DARK_CIRCLE 과 같은 판단).
 * 시연 음식(520·610kcal)에는 걸리지 않아 예시 60·87 이 그대로다.
 */
@Component
public class HighCalorieRule implements PlateRule {

    @Override public String code()    { return "R10"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighCalorie();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.caution(code(), R10_HIGH_CALORIE,
                "열량이 높음",
                "열량이 높은 편이라 오늘 다른 끼니에서 균형을 맞춰보면 좋겠어요.",
                "밥이나 면 양을 조금 줄여보세요.", GAIN_LESS_RICE);
    }
}
