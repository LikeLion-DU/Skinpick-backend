package com.skinplate.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import com.skinplate.api.domain.user.entity.WaterIntake;

import java.time.LocalDateTime;
import java.util.List;

public record MeResponse(
        Long userId,
        String email,
        String nickname,

        /* null 이면 non_null 직렬화로 키 자체가 생략된다.
           앱은 키가 없으면 "아직 안 정함"으로 보고 인라인 선택 칩을 띄운다. */
        SkinType declaredSkinType,

        /* 항상 배열로 나간다. 빈 배열 = 미설정 — non_null 은 컬렉션에 통하지 않는다
           (빈 Set 은 null 이 아니다). 습관 4종만 키 생략 규칙을 따른다. */
        List<SkinConcern> skinConcerns,

        SleepPattern sleepPattern,
        StressLevel stressLevel,
        ExerciseHabit exerciseHabit,
        WaterIntake waterIntake,

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
                /* 트랜잭션 안에서 LAZY 컬렉션을 초기화하며 enum 선언 순으로 고정한다.
                   Set 을 그대로 담으면 직렬화(트랜잭션 밖)에서 LazyInitializationException 이다.
                   EnumSet.copyOf 는 빈 컬렉션에서 터지므로 쓰지 않는다. */
                user.getSkinConcerns().stream().sorted().toList(),
                user.getSleepPattern(),
                user.getStressLevel(),
                user.getExerciseHabit(),
                user.getWaterIntake(),
                user.isTestAccount(),
                user.getCreatedAt());
    }
}
