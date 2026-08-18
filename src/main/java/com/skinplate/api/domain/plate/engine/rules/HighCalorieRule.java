package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R10 · 고열량. 2단계 계단형 — p90(660kcal) / p95(790kcal).
 *
 * <p>피부 지표와 직접 매지 않는 <b>보조 룰</b>이다 — 고열량↔특정 피부 지표의 매핑을
 * 이 앱이 증명할 수 없어 심각도 계수 없이 고정 델타로 둔다. 같은 이유로
 * ConcernRules 에도 매지 않는다(DARK_CIRCLE 과 같은 판단).
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
        int caloriesKcal = context.nutrition().getCaloriesKcal();
        int penalty = penaltyOf(Nutrition.calorieTierOf(caloriesKcal));

        // 밥·면을 줄이면 열량이 3/4 로 준다. 그때 단계가 몇으로 내려가는지를 지금 계산한다 —
        // 옛 고정값(+5)은 1,200kcal 을 넘는 한 끼에서도 "+5" 라 말하고 실제로는 0 이 올랐다.
        int afterLessRice = penaltyOf(Nutrition.calorieTierOf(
                (int) Math.round(caloriesKcal * CALORIES_AFTER_LESS_RICE)));
        int expectedGain = penalty - afterLessRice;

        String level = caloriesKcal > Nutrition.CALORIES_VERY_HIGH
                ? "열량이 아주 높은 편이라" : "열량이 높은 편이라";
        String reason = level + " 오늘 다른 끼니에서 균형을 맞춰보면 좋겠어요.";

        // 3/4 로 줄여도 같은 단계에 남는 한 끼가 있다(1,200kcal → 900kcal). 그때 행동
        // 카드를 붙이면 버튼을 눌러도 점수가 그대로다 — 애니메이션이 0 을 세는 셈이라
        // 카드를 아예 주지 않는다. 주의 문장은 그대로 남는다.
        if (expectedGain == 0) {
            return RuleResult.caution(code(), -penalty, "열량이 높음", reason);
        }

        return RuleResult.caution(code(), -penalty, "열량이 높음", reason,
                "밥이나 면 양을 조금 줄여보세요.", expectedGain);
    }

    private static int penaltyOf(int tier) {
        return switch (tier) {
            case 2 -> -R10_VERY_HIGH_CALORIE;
            case 1 -> -R10_HIGH_CALORIE;
            default -> 0;
        };
    }
}
