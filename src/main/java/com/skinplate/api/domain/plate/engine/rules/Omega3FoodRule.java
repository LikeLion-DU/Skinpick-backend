package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R14_OMEGA3_FOOD;

/**
 * R14 · 오메가3 재료. 음식 축 가점이라 <b>피부 게이트가 없다</b>.
 *
 * <p>이 룰이 없던 시절 오메가3 가점은 R01(건조)·R08(장벽 약화) 게이트 뒤에만 있었다.
 * 그래서 피부가 정상인 사용자에게 고등어구이가 70점 — 초밥·김밥과 같은 중립값이었다.
 * 등푸른생선이 좋은 이유는 피부가 건조할 때만 생기는 것이 아니다.
 *
 * <p><b>R01·R08 과 독립으로 동작한다.</b> 셋이 겹치면 건조하고 장벽이 약한 사용자의
 * 연어가 <b>+27~32</b> 를 받는데(2026-08-20 재캘리브레이션 후 실측: 심각도 1.2 에서
 * 10+8+9=27, 1.5 에서 12+11+9=32), 그건 이중계상이 아니라 <b>이 사용자에게 실제로
 * 가장 필요한 음식</b>이라는 뜻이다 — 개인화가 하는 일이 그것이다.
 * <p>다만 그 폭이 상한(MAX_SCORE=97)을 넘긴다 — 건조·장벽 약화 사용자에게는 오메가3
 * 음식 여럿이 나트륨·열량과 무관하게 전부 97 로 붙는다. 상한이 만드는 평탄면이다. 총지방으로 지방을 재던 시절에는
 * OMEGA3 태그가 감점 완화의 대리 지표로도 쓰여 상호배제가 필요했지만, R11 이 실측
 * 포화지방으로 바뀌면서 그 겹침이 사라졌다.
 *
 * <p><b>태그가 없으면 가점하지 않는다.</b> 표준 음식표 자체에는 54종(3.7%)에만 붙는다.
 * 다만 2026-08-20(#64)부터 {@code StandardFoodTable.find} 가 <b>조회 이름</b>에서도
 * OMEGA3 를 얹으므로, 실제 발동 범위는 그 54종보다 넓다 — AI 가 이름에 '연어'를 적기만
 * 하면 붙는다.
 */
@Component
public class Omega3FoodRule implements PlateRule {

    @Override public String code()    { return "R14"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasTag(IngredientTag.OMEGA3);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R14_OMEGA3_FOOD, "오메가3 재료",
                "오메가3가 들어 있어 피부 컨디션에 도움이 될 수 있어요.");
    }
}
