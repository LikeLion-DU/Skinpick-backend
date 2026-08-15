package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import com.skinplate.api.domain.user.entity.WaterIntake;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PATCH /auth/me — 보낸 필드만 바꾼다.
 *
 * "건너뛰기"는 이 API 를 호출하지 않는 것이다.
 * UNKNOWN 을 대신 넣으면 "잘 모르겠다고 답한 사용자"와 구분이 사라진다.
 *
 * skinConcerns 만은 빈 배열이 "전부 해제"다 — null(생략)과 [] 를 구분한다.
 * hasNickname 의 isBlank 패턴을 복붙하면 해제가 조용히 무시되므로 null 검사만 한다.
 * 습관 4종은 UI 에 해제 개념이 없어 null = 변경 없음으로 충분하다.
 */
public record UpdateProfileRequest(

        SkinType declaredSkinType,

        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해 주세요.")
        String nickname,

        List<@NotNull(message = "피부 고민 항목에 빈 값이 올 수 없습니다.") SkinConcern> skinConcerns,

        SleepPattern sleepPattern,

        StressLevel stressLevel,

        ExerciseHabit exerciseHabit,

        WaterIntake waterIntake
) {
    public boolean hasSkinType()      { return declaredSkinType != null; }
    public boolean hasNickname()      { return nickname != null && !nickname.isBlank(); }
    public boolean hasSkinConcerns()  { return skinConcerns != null; }   // [] = 전부 해제
    public boolean hasSleepPattern()  { return sleepPattern != null; }
    public boolean hasStressLevel()   { return stressLevel != null; }
    public boolean hasExerciseHabit() { return exerciseHabit != null; }
    public boolean hasWaterIntake()   { return waterIntake != null; }
}
