package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.dto.CareFocusDto;
import com.skinplate.api.domain.skin.entity.SkinCareFocus;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * S05 의 "지금 피부가 필요로 하는 관리" — 관리 축 칩과 권고 문단을 만든다. (PRD §4.1)
 *
 * <p><b>AI 를 부르지 않는다.</b> {@link SkinHighlightBuilder} 와 같은 자리다 — 지표에서
 * 규칙으로 도출하므로 같은 사진은 언제 열어도 같은 문구이고, 예전에 저장된 분석에도
 * 나온다(지표는 처음부터 저장돼 있었다).
 *
 * <p><b>{@code summary} 와 다른 것을 말한다.</b> summary 는 AI 가 사진에서 <b>관찰한 것</b>
 * ("피부 장벽은 양호하지만 건조하고 홍조가 관찰됩니다")이고, 여기는 그 관찰에서 나오는
 * <b>식단 방향</b>이다. 둘을 합치면 관찰과 권고가 한 문단에 섞여 어느 쪽이 사실인지
 * 흐려진다.
 *
 * <p><b>{@code GET /skin-insights} 와도 다르다.</b> 저쪽은 생활 습관(수면·스트레스·운동·
 * 물)을 함께 넣어 AI 가 쓰는 개인화 인사이트이고, 여기는 지표만 보는 규칙 도출이다.
 * 그래서 새 엔드포인트를 만들지 않고 기존 분석 응답에 두 필드를 얹었다 — 이 화면은
 * 이미 분석 하나를 들고 있으므로 왕복을 늘릴 이유가 없다.
 */
@Component
public class SkinCareGuide {

    /** 문단에 싣는 축 수. 넷을 이어 붙이면 카드 하나가 화면 절반을 먹는다. */
    private static final int MESSAGE_FOCUS_MAX = 3;

    public List<CareFocusDto> focus(SkinMetrics metrics) {
        return SkinCareFocus.observe(metrics).stream().map(CareFocusDto::from).toList();
    }

    /**
     * 축별 권고 한 문장씩을 이어 붙인 문단. 축이 없을 수는 없다
     * ({@code observe} 가 최소 하나를 낸다).
     *
     * <p>세 축까지만 싣는다. 잘리는 것은 문단뿐이고 {@code careFocus} 배열은 온전히
     * 내려간다 — {@code SkinTypeDto} 가 라벨에 상태 둘만 싣는 것과 같은 규약이다.
     */
    public String message(SkinMetrics metrics) {
        return SkinCareFocus.observe(metrics).stream()
                .limit(MESSAGE_FOCUS_MAX)
                .map(SkinCareFocus::getGuidance)
                .collect(Collectors.joining(" "));
    }
}
