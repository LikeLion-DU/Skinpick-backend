package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R06_VITAMIN;

/**
 * R06 · 비타민·항산화 풍부.
 *
 * <p><b>재료 태그와 실측 영양값 중 하나만 서면 걸린다.</b> 태그 판정은 그대로 남긴다 —
 * AI 가 사진에서 본 브로콜리·시금치는 표준 테이블에 없는 음식에서도 유일한 단서다.
 * 다만 태그만으로는 1,443종 중 42종(3%)밖에 못 잡았다. 표준 영양 확장(V9)으로 들어온
 * 비타민 A·C 실측값을 OR 로 함께 본다.
 *
 * <p><b>가점은 한 번뿐이다.</b> 룰이 하나이므로 태그와 실측이 둘 다 서도 +7 한 번이다 —
 * 신호가 둘이라고 음식이 두 배 좋아지는 것은 아니다.
 *
 * <p>실측 쪽은 100kcal 당 밀도로 본다. 절대량이면 큰 음식이 유리해져 점수가 음식의
 * 질이 아니라 양을 재게 된다 ({@code Nutrition} 의 밀도 경계 주석 참조).
 */
@Component
public class VitaminRule implements PlateRule {

    @Override public String code()    { return "R06"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return hasVitaminTag(context) || context.nutrition().isVitaminRich();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R06_VITAMIN, "비타민 풍부",
                hasVitaminTag(context)
                        ? "비타민·항산화 재료가 들어 있어 도움이 될 수 있어요."
                        : "열량 대비 비타민이 넉넉한 한 끼예요.");
    }

    private static boolean hasVitaminTag(PlateContext context) {
        return context.food().hasAnyTag(
                IngredientTag.VITAMIN_C, IngredientTag.VITAMIN_A, IngredientTag.ANTIOXIDANT);
    }
}
