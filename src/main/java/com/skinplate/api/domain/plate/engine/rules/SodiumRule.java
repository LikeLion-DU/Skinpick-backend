package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R04 · 나트륨 과다. 3단계 계단형 — p75 / p90 / p95.
 *
 * <p><b>초과량 비례에서 계단으로 바꾼 이유.</b> 비례식은 500mg 마다 1점씩 움직여서
 * "왜 이 점수인가" 카드가 설명하기 어려웠고, 무엇보다 <b>국물을 절반 남겼을 때 몇 점이
 * 오르는지를 미리 말할 수 없었다</b> — 그래서 고정값 +8 을 광고하고 실제로는 다른 값이
 * 오르곤 했다. 단계형은 회복치를 정확히 계산할 수 있다.
 */
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
        int sodiumMg = context.nutrition().getSodiumMg();
        int penalty = penaltyOf(Nutrition.sodiumTierOf(sodiumMg));

        // 국물을 절반 남기면 나트륨도 절반이다. 그때 단계가 몇으로 내려가는지를 지금 계산해
        // 카드에 싣는다 — 시뮬레이션(SkinPlateService)이 쓰는 식과 같아야 거짓이 없다.
        int afterHalvingSoup = penaltyOf(Nutrition.sodiumTierOf(sodiumMg / 2));
        int expectedGain = penalty - afterHalvingSoup;

        String sodiumLevel = context.nutrition().isVeryHighSodium() ? "매우 높은" : "높은";
        String reason = "나트륨이 " + sodiumLevel + " 편이라 피부 컨디션에 부담이 될 수 있어요.";

        // 절반으로 줄여도 같은 단계에 남을 만큼 짠 한 끼가 있다(5,000mg → 2,500mg).
        // 그때 행동 카드를 붙이면 버튼을 눌러도 점수가 그대로다 — 카드를 주지 않는다.
        if (expectedGain == 0) {
            return RuleResult.caution(code(), -penalty, "나트륨 과다", reason);
        }

        String action = context.food().isSoup()
                ? "국물을 절반만 남기면 Skin Plate 점수가 상승합니다."
                : "간이 센 반찬은 절반만 드셔보세요.";

        return RuleResult.caution(code(), -penalty, "나트륨 과다", reason, action, expectedGain);
    }

    /** 단계 → 감점(양수). 경계는 Nutrition 이, 델타는 RuleConstants 가 소유한다. */
    private static int penaltyOf(int tier) {
        return switch (tier) {
            case 3 -> -R04_SODIUM_EXTREME;
            case 2 -> -R04_SODIUM_VERY_HIGH;
            case 1 -> -R04_SODIUM_HIGH;
            default -> 0;
        };
    }
}
