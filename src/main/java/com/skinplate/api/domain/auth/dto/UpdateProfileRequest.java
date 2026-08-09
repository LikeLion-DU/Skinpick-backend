package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.entity.SkinType;
import jakarta.validation.constraints.Size;

/**
 * PATCH /auth/me — 보낸 필드만 바꾼다.
 *
 * "건너뛰기"는 이 API 를 호출하지 않는 것이다.
 * UNKNOWN 을 대신 넣으면 "잘 모르겠다고 답한 사용자"와 구분이 사라진다.
 */
public record UpdateProfileRequest(

        SkinType declaredSkinType,

        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해 주세요.")
        String nickname
) {
    public boolean hasSkinType() { return declaredSkinType != null; }
    public boolean hasNickname() { return nickname != null && !nickname.isBlank(); }
}
