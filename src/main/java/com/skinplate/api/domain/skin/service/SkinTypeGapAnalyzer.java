package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.dto.SkinTypeGapDto;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.SkinType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * "평소 알고 계셨던 타입"과 "오늘 측정에서 관찰된 타입"을 비교한다. (PRD §4.4.1)
 *
 * 이 클래스는 Skin Plate Score 에 아무 영향을 주지 않는다.
 * 자가 신고값이 점수에 개입하면 "같은 사진 두 번 찍어도 같은 점수"라는
 * 주장에 사용자 입력이라는 변수가 하나 더 끼어든다.
 */
@Component
public class SkinTypeGapAnalyzer {

    /** 자주 나오는 조합만 전용 문구를 둔다. 나머지는 폴백으로 충분하다. */
    private static final Map<String, String> SPECIAL = Map.of(
            key(SkinType.OILY, SkinType.DRY),
            "지성이라고 생각하셨지만 오늘은 유분보다 수분 부족이 두드러집니다. 유분기는 수분이 모자랄 때도 늘어날 수 있습니다.",

            key(SkinType.OILY, SkinType.COMBINATION),
            "유분은 많은데 수분이 부족한 상태입니다. 흔히 '수분 부족형 지성'이라고 부릅니다.",

            key(SkinType.DRY, SkinType.OILY),
            "건성이라고 생각하셨지만 오늘은 유분이 많은 편입니다. 세안 후 수분 공급이 부족하지 않은지 살펴보세요.",

            key(SkinType.SENSITIVE, SkinType.NORMAL),
            "민감성이라고 하셨는데 오늘은 자극이 적은 안정된 상태입니다.",

            key(SkinType.COMBINATION, SkinType.DRY),
            "복합성이라고 생각하셨지만 오늘은 전반적으로 건조합니다."
    );

    /**
     * @param declared 사용자가 고른 값. null(건너뜀)이면 null 을 반환한다.
     * @return 앱이 그대로 렌더링할 수 있는 비교 결과. null 이면 앱이 선택 칩을 띄운다.
     */
    public SkinTypeGapDto analyze(SkinType declared, SkinMetrics metrics) {
        if (declared == null) return null;

        SkinType observed = SkinType.observe(metrics);

        if (declared == SkinType.UNKNOWN) {
            return new SkinTypeGapDto(declared, observed, false,
                    "오늘 측정 기준으로는 " + observed.getLabel() + "에 가깝습니다.");
        }
        if (declared == observed) {
            return new SkinTypeGapDto(declared, observed, true,
                    "평소 생각하신 " + declared.getLabel() + " 그대로입니다. 오늘 측정과 일치합니다.");
        }

        String message = SPECIAL.getOrDefault(key(declared, observed),
                "평소 " + declared.getLabel() + "이라고 생각하셨지만, "
                        + "오늘 측정은 " + observed.getLabel() + "에 가깝습니다.");

        return new SkinTypeGapDto(declared, observed, false, message);
    }

    private static String key(SkinType declared, SkinType observed) {
        return declared.name() + "→" + observed.name();
    }
}
