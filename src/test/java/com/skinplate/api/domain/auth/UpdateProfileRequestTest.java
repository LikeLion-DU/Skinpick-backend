package com.skinplate.api.domain.auth;

import com.skinplate.api.domain.auth.dto.UpdateProfileRequest;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.WaterIntake;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProfileRequestTest {

    @Test
    @DisplayName("skinConcerns 배열의 null 원소는 검증에서 걸린다 — sorted() NPE 로 500 이 나는 것을 400 으로 막는다")
    void nullConcernElementIsRejected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new UpdateProfileRequest(
                    null, null, Arrays.asList(SkinConcern.ACNE, null), null, null, null, null));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    @DisplayName("waterIntake 는 습관 3종과 같다 — null 은 변경 없음, 값이 있으면 갱신 대상")
    void waterIntakeFollowsHabitSemantics() {
        assertThat(new UpdateProfileRequest(null, null, null, null, null, null, null)
                .hasWaterIntake()).isFalse();
        assertThat(new UpdateProfileRequest(null, null, null, null, null, null, WaterIntake.LACKING)
                .hasWaterIntake()).isTrue();
    }
}
