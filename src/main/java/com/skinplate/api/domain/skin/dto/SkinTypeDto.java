package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.user.entity.SkinType;

import java.util.List;

/**
 * AI 가 사진에서 읽은 피부 타입.
 *
 * {@code SkinAnalysisResponse.skinTypeGap.observed} 와는 <b>다른 값</b>이다.
 * 그쪽은 5개 지표에서 규칙으로 도출한다(PRD §14.3) — 같은 지표면 항상 같은 타입이
 * 나와야 갭 코멘트가 재현 가능하기 때문이다. 이 필드는 AI 관찰을 그대로 보여준다.
 *
 * 둘이 갈리는 것은 오류가 아니라 정보다. 갭 카드는 규칙값을 계속 쓴다.
 *
 * @param primary DRY · NORMAL · OILY · COMBINATION 만 온다. SENSITIVE 는 traits 쪽이다
 */
public record SkinTypeDto(SkinType primary, List<SkinTrait> traits) {}
