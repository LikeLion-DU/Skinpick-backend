package com.skinplate.api.domain.skin.dto;

import java.util.List;

/**
 * AI 추정 피부 나이. <b>Skin Score 계산에는 들어가지 않는다.</b>
 *
 * 실제 생물학적 나이의 측정값이 아니라 사진 기반 외관 추정이다. 앱은
 * "사진 속 피부결, 주름, 탄력, 피부톤 등을 종합한 AI 추정값입니다" 를 함께 띄운다.
 *
 * @param axes 나이 전용 축. <b>7개</b>다 — redness 는 AI 가 평가하지만 응답에 넣지 않는다.
 *             상태 지표에 이미 redness 가 있어서, 둘 다 내리면 화면에 붉은기 숫자가
 *             둘이 되고 사용자가 어느 쪽을 믿을지 알 수 없다. AI 판단과
 *             {@code assessment} 근거에는 그대로 반영되고 원본은 raw_ai_response 에 남는다
 */
public record SkinAgeDto(int estimatedSkinAge, List<ScoredItemDto> axes, String assessment) {}
