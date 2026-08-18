package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * R11 · 포화지방 과다. 3단계 계단형 — p75 3.9g / p90 8.2g / p95 12.3g.
 *
 * <p><b>총지방이 아니라 포화지방을 본다.</b> 총지방으로 재면 연어(28g)와 스테이크(39g)가
 * 같은 구간에 들어가는데, 등푸른생선의 지방은 불포화가 주라 같은 벌을 줄 근거가 없다.
 * 포화지방으로 보면 연어 4.4g · 고등어 3.8g 인 반면 스테이크 12.7g · 치즈돈가스 13.6g 로
 * 갈린다. 표준 영양 확장(V9) 전에는 이 값이 없어 "OMEGA3 태그면 한 단계 완화" 라는
 * 대리 지표로 메웠고, 실측값이 생기면서 그 우회로를 지웠다.
 *
 * <p><b>심각도 계수를 태우지 않는다.</b> 포화지방↔특정 피부 지표의 대응을 이 앱이
 * 증명할 수 없다 — R10(고열량)과 같은 판단이고, 같은 이유로 ConcernRules 에도 매지 않는다.
 *
 * <p><b>한계.</b> 표준 테이블에서 찾지 못한 음식은 이 값이 0 이라 발동하지 않는다.
 * AI 스키마에 포화지방이 없기 때문이다 — 모르는 값을 추정해 깎지는 않는다.
 */
@Component
public class SaturatedFatRule implements PlateRule {

    @Override public String code()    { return "R11"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().saturatedFatTier() > 0;
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int tier = context.nutrition().saturatedFatTier();

        return RuleResult.caution(code(), deltaOf(tier),
                "포화지방 많음",
                tier >= 2
                        ? "포화지방이 아주 많은 편이라 피부에 부담이 될 수 있어요."
                        : "포화지방이 많은 편이라 피부에 부담이 될 수 있어요.");
    }

    /** 단계 → 델타. 경계는 Nutrition 이, 델타는 RuleConstants 가 소유한다. */
    private static int deltaOf(int tier) {
        return switch (tier) {
            case 3 -> R11_SAT_FAT_EXTREME;
            case 2 -> R11_SAT_FAT_HIGH;
            default -> R11_SAT_FAT;
        };
    }
}
