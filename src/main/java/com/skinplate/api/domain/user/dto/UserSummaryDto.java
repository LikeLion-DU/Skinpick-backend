package com.skinplate.api.domain.user.dto;

import com.skinplate.api.domain.user.entity.AppUser;

/** 인증 응답에 함께 실리는 최소 사용자 정보. */
public record UserSummaryDto(
        Long userId,
        String email,
        String nickname
) {
    public static UserSummaryDto from(AppUser user) {
        return new UserSummaryDto(user.getId(), user.getEmail(), user.getNickname());
    }
}
