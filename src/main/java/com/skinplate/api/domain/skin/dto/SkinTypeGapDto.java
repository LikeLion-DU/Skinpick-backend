package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.user.entity.SkinType;

/**
 * "평소 알고 계셨던 타입"과 "오늘 측정에서 관찰된 타입"의 비교. (PRD §4.4.1)
 *
 * 선언 타입이 없으면 이 객체 자체가 null 이고, 앱은 그 자리에
 * "평소 본인 피부는?" 인라인 선택 칩을 띄운다.
 */
public record SkinTypeGapDto(
        SkinType declared,
        SkinType observed,
        boolean matched,
        String message
) {}
