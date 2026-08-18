package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R15_FIBER;
import static com.skinplate.api.domain.plate.engine.RuleConstants.R15_FIBER_HIGH;

/**
 * R15 · 식이섬유 풍부. 2단계 — 100kcal 당 p75(3.0g) / p90(5.0g).
 *
 * <p><b>이 룰이 "좋은 음식이 올라간다"를 담당한다.</b> 이 룰이 없던 시절 가점 통로는
 * 재료 태그뿐이었고, 1,443종 중 비타민·항산화 태그를 가진 것이 42종(3%)이라
 * 콩나물무침과 감자튀김이 똑같이 70점이었다. 정상 피부에서 81점 이상에 닿는 음식이
 * 하나도 없었다는 것이 그 결과다.
 *
 * <p><b>절대량이 아니라 밀도로 본다.</b> 절대량이면 감자튀김(5.8g)이 샐러드(3.8g)를
 * 이긴다 — 감자튀김이 더 크기 때문이다. 밀도는 콩나물무침 4.8 · 샐러드 1.3 ·
 * 떡볶이 0.7 · 돈가스 0.4 로 상식과 같은 순서를 낸다.
 *
 * <p>단백질 밀도와의 상관은 r = 0.10 이라 R05 와 겹치지 않는다. 같은 방식으로
 * 검토한 아연은 r = 0.38 이고 상위가 굴·조개·갈비탕·수육이라 채택하지 않았다 —
 * 부담이 큰 음식에 가점을 주는 룰이 된다.
 */
@Component
public class FiberRule implements PlateRule {

    @Override public String code()    { return "R15"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().fiberTier() > 0;
    }

    @Override
    public RuleResult apply(PlateContext context) {
        boolean high = context.nutrition().fiberTier() >= 2;

        return RuleResult.good(code(), high ? R15_FIBER_HIGH : R15_FIBER,
                "식이섬유 풍부",
                high
                        ? "열량 대비 식이섬유가 아주 넉넉한 한 끼예요."
                        : "열량 대비 식이섬유가 넉넉한 한 끼예요.");
    }
}
