package com.skinplate.api.domain.skin.entity;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * "지금 피부가 필요로 하는 관리" 축. 오늘의 지표에서 <b>규칙으로 도출한다</b> — AI 에게
 * 묻지 않는다({@link SkinTrait} · {@link SkinHighlightBuilder} 와 같은 계열이다).
 *
 * <p><b>축이 곧 룰이다.</b> 각 축은 이 피부 상태에서 실제로 점수를 움직이는 룰과 짝이다 —
 * 화면의 칩이 "이걸 챙기면 오른다"를 말하는데 채점이 그렇지 않으면 앱이 거짓말을 한다.
 *
 * <pre>
 *   수분·장벽   isDry() || isBarrierWeak()   R01(수분 보충 재료) · R08(오메가3 × 장벽)
 *   항산화      hasRedness() || hasTrouble() R02(매운맛 감점) 를 받는 상태 · R06(항산화 가점)
 *   건강한 지방  isBarrierWeak()              R08 · R14(오메가3 재료)
 *   유분 관리    isOily()                     R07(튀김 감점) 를 받는 상태
 *   식이섬유    hasTrouble() || isOily()      R15(식이섬유 가점)
 * </pre>
 *
 * <p><b>임계값을 새로 만들지 않았다.</b> 조건은 전부 {@link SkinMetrics} 의 판정자를
 * 그대로 부른다. 경계를 바꾸면 룰·뱃지·타입과 함께 이 축도 같이 움직인다.
 *
 * <p><b>문장은 조건보다 약하게 쓴다.</b> 조건이 {@code ||} 인 축(수분·장벽, 항산화,
 * 식이섬유)에서 "A 하고 B 한 편이라"처럼 단정하면, 한쪽만 걸린 사용자가 같은 응답의
 * {@code metricDetails} 에서 정상 등급을 보면서 그 지표가 나쁘다는 문장을 읽는다.
 * 그래서 "~거나" · "~나" 로 쓴다 — 축을 쪼개는 대신 문장을 조건에 맞춘 것이다.
 * 새 축을 넣을 때도 같다: {@code &&} 면 단정해도 되고, {@code ||} 면 단정하면 안 된다.
 *
 * <p>해당하는 축이 하나도 없을 때 {@link #BALANCE} 가 남는다 — 지표가 모두 안정적인
 * 사용자에게 빈 배열을 주면 화면의 칩 줄이 사라져 "분석이 덜 됐다"로 읽힌다.
 * 그 경우에도 말할 것이 있다: 지금 상태를 유지하는 것.
 */
public enum SkinCareFocus {

    HYDRATION("수분·장벽",
            metrics -> metrics.isDry() || metrics.isBarrierWeak(),
            "수분이 부족하거나 장벽이 약한 편이라, 수분 유지에 도움이 되는 식습관을 챙겨보세요."),

    ANTIOXIDANT("항산화",
            metrics -> metrics.hasRedness() || metrics.hasTrouble(),
            "붉은기나 트러블이 관찰되니, 채소와 과일처럼 항산화 성분이 풍부한 식품을 섭취해보세요."),

    HEALTHY_FAT("건강한 지방 섭취",
            SkinMetrics::isBarrierWeak,
            "장벽이 약한 편이라, 오메가3와 불포화지방산 같은 건강한 지방을 균형 있게 섭취해보세요."),

    SEBUM_CARE("유분 관리",
            SkinMetrics::isOily,
            "유분이 많은 편이라, 튀김과 기름진 조리를 줄이면 부담이 덜해요."),

    FIBER("식이섬유",
            metrics -> metrics.hasTrouble() || metrics.isOily(),
            "트러블이나 유분이 올라와 있어, 식이섬유가 풍부한 식품이 도움이 될 수 있어요."),

    /**
     * 위 다섯 중 아무것도 해당하지 않을 때만 나온다. 다른 축과 <b>함께 오지 않는다</b> —
     * "유분 관리"와 "지금 균형 유지"가 같은 줄에 있으면 서로를 부정한다.
     */
    BALANCE("지금 균형 유지",
            metrics -> false,
            "지금은 주요 지표가 모두 안정적이에요. 균형 잡힌 식단을 그대로 이어가 보세요.");

    private final String label;
    private final Predicate<SkinMetrics> condition;
    private final String guidance;

    SkinCareFocus(String label, Predicate<SkinMetrics> condition, String guidance) {
        this.label = label;
        this.condition = condition;
        this.guidance = guidance;
    }

    public String getLabel() { return label; }

    /** 이 축 하나에 대한 권고 한 문장. {@code careMessage} 는 이 문장들을 이어 붙인 것이다. */
    public String getGuidance() { return guidance; }

    /**
     * 오늘 해당하는 축을 <b>선언 순서대로</b> 돌려준다. 심각도로 정렬하지 않는다 —
     * 같은 지표를 두 축이 나눠 보고 있어서(장벽이 수분·장벽과 건강한 지방에 함께 걸린다)
     * 심각도로 세우면 두 칩의 순서가 지표 한 점 차이로 뒤바뀐다.
     *
     * <p>해당이 없으면 {@link #BALANCE} 하나다.
     */
    public static List<SkinCareFocus> observe(SkinMetrics metrics) {
        List<SkinCareFocus> matched = Arrays.stream(values())
                .filter(focus -> focus != BALANCE)
                .filter(focus -> focus.condition.test(metrics))
                .toList();

        return matched.isEmpty() ? List.of(BALANCE) : matched;
    }
}
