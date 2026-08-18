package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R12_REFINED_CARB;

/**
 * R12 · 정제 탄수 과다.
 *
 * <p><b>HIGH_GI 태그 하나로 판단하지 않는다.</b> 태그는 재료 이름에서 뽑기 때문에
 * 감자 된장국(70kcal · 탄수 10.6g)까지 잡는다 — 감자가 들었다는 것과 정제 탄수를 많이
 * 먹는다는 것은 다른 말이다. 실제 탄수량을 함께 물어 두 신호가 다 설 때만 깎는다.
 * 그러면 1,443종 중 101종이 40종으로 좁혀지고, 떡볶이(46.7g)·감자튀김(52.7g)은 그대로 남는다.
 *
 * <p>태깅이 넓어지면(면류·빵류는 아직 대부분 누락이다) 이 룰이 자동으로 함께 넓어진다 —
 * 늘기 전까지는 오탐 없이 동작한다.
 *
 * <p><b>튀김옷에는 태그를 붙이지 않는다.</b> 튀김의 부담은 R07 이 이미 세고 있어,
 * 여기서 또 깎으면 같은 사실이 두 번 벌을 받는다.
 */
@Component
public class RefinedCarbRule implements PlateRule {

    @Override public String code()    { return "R12"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasTag(IngredientTag.HIGH_GI)
                && context.nutrition().hasRefinedCarbLoad();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.caution(code(), R12_REFINED_CARB,
                "정제 탄수 많음",
                "흰쌀·떡·밀가루 같은 정제 탄수가 많은 편이라 피부에 부담이 될 수 있어요.");
    }
}
