package com.skinplate.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;

import java.time.LocalDateTime;

public record MeResponse(
        Long userId,
        String email,
        String nickname,

        /* null 이면 non_null 직렬화로 키 자체가 생략된다.
           앱은 키가 없으면 "아직 안 정함"으로 보고 인라인 선택 칩을 띄운다. */
        SkinType declaredSkinType,

        /* boolean 접근자의 JSON 키는 Jackson 버전과 네이밍 전략에 따라
           isTestAccount / testAccount 로 갈릴 여지가 있다.
           프론트 DTO가 isTestAccount를 기대하므로 방어적으로 고정한다. */
        @JsonProperty("isTestAccount") boolean isTestAccount,

        LocalDateTime joinedAt
) {
    public static MeResponse from(AppUser user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getDeclaredSkinType(),
                user.isTestAccount(),
                user.getCreatedAt());
    }
}
