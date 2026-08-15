package com.skinplate.api.global.init;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TestAccountInitializerTest {

    @Test
    @DisplayName("원탭 로그인은 슬롯 계정만 대상으로 한다 — 개발 계정으로는 들어갈 수 없다")
    void slotLoginNeverReachesDevAccounts() {
        // 개발 계정은 로그인 폼(이메일·비밀번호)으로만 들어가야 한다.
        // 그래야 S01 폼 검증과 INVALID_CREDENTIALS 응답이 실제로 한 번은 돌아본다.
        for (int slot = 1; slot <= 3; slot++) {
            assertThat(TestAccountInitializer.emailOfSlot(slot)).doesNotStartWith("dev");
        }
    }

    @Test
    @DisplayName("슬롯 번호로 지정된 이메일이 나온다")
    void resolvesEachSlot() {
        assertThat(TestAccountInitializer.emailOfSlot(1)).isEqualTo("test@skinplate.app");
        assertThat(TestAccountInitializer.emailOfSlot(2)).isEqualTo("test2@skinplate.app");
        assertThat(TestAccountInitializer.emailOfSlot(3)).isEqualTo("test3@skinplate.app");
    }

    @Test
    @DisplayName("범위 밖 슬롯은 시연 계정(슬롯 1)으로 떨어진다")
    void fallsBackToDemoAccount() {
        assertThat(TestAccountInitializer.emailOfSlot(99)).isEqualTo("test@skinplate.app");
    }

    @Test
    @DisplayName("슬롯 1에는 시연 프로필(다크서클·수면 부족)이 심어진다 — 측정 2 + 신고 1 + 습관 1 이 전부 나오게")
    void seedsDemoProfileOnSlotOne() throws Exception {
        AppUserRepository repository = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        given(encoder.encode(any())).willReturn("encoded");
        given(repository.existsByEmail(anyString())).willReturn(true);   // 계정 생성은 건너뛴다
        AppUser slotOne = AppUser.createTestAccount("test@skinplate.app", "encoded", "테스트유저");
        given(repository.findByEmail("test@skinplate.app")).willReturn(Optional.of(slotOne));

        TestAccountInitializer initializer = new TestAccountInitializer(repository, encoder);
        ReflectionTestUtils.setField(initializer, "password", "test1234!");
        initializer.run(null);

        assertThat(slotOne.getSkinConcerns()).containsExactly(SkinConcern.DARK_CIRCLE);
        assertThat(slotOne.getSleepPattern()).isEqualTo(SleepPattern.LACKING);
    }

    @Test
    @DisplayName("이미 프로필이 있으면 seed 가 덮어쓰지 않는다 — 시연 중 바꾼 값이 재기동에도 보존된다")
    void doesNotReseedWhenProfileExists() throws Exception {
        AppUserRepository repository = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        given(encoder.encode(any())).willReturn("encoded");
        given(repository.existsByEmail(anyString())).willReturn(true);
        AppUser slotOne = AppUser.createTestAccount("test@skinplate.app", "encoded", "테스트유저");
        slotOne.updateSkinConcerns(Set.of(SkinConcern.ACNE));
        slotOne.changeSleepPattern(SleepPattern.ENOUGH);
        given(repository.findByEmail("test@skinplate.app")).willReturn(Optional.of(slotOne));

        TestAccountInitializer initializer = new TestAccountInitializer(repository, encoder);
        ReflectionTestUtils.setField(initializer, "password", "test1234!");
        initializer.run(null);

        assertThat(slotOne.getSkinConcerns()).containsExactly(SkinConcern.ACNE);
        assertThat(slotOne.getSleepPattern()).isEqualTo(SleepPattern.ENOUGH);
    }
}
