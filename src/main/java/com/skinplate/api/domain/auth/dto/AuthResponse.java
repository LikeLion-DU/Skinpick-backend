package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.dto.UserSummaryDto;
import com.skinplate.api.domain.user.entity.AppUser;

/**
 * 회원가입 · 로그인 · 테스트 로그인이 모두 같은 형태로 응답한다.
 *
 * refreshToken 필드를 미리 만들어 두지 않았다.
 * 쓰지 않는 필드를 null로 내려보내면 프론트가 "이거 언제 채워지나요"를 묻게 된다.
 * JSON은 필드 추가에 하위 호환이므로 Phase 2에서 넣는 편이 낫다.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryDto user
) {
    private static final String BEARER = "Bearer";

    public static AuthResponse of(String accessToken, long expiresInSeconds, AppUser user) {
        return new AuthResponse(accessToken, BEARER, expiresInSeconds, UserSummaryDto.from(user));
    }
}
