package com.skinplate.api.domain.auth;

import com.skinplate.api.domain.auth.dto.AuthResponse;
import com.skinplate.api.domain.auth.dto.LoginRequest;
import com.skinplate.api.domain.auth.dto.SignupRequest;
import com.skinplate.api.domain.auth.service.AuthService;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * DB 만 대역으로 두고 비밀번호 인코더와 토큰 발급기는 실제 구현을 쓴다.
 * 검증 대상이 "비밀번호가 실제로 맞는가", "토큰이 실제로 발급되는가"라서
 * 그 둘을 흉내내면 아무것도 증명하지 못한다.
 */
class AuthServiceTest {

    private static final String SECRET = "skinplate-hackathon-secret-key-32bytes-or-more";
    private static final String RAW_PASSWORD = "test1234!";

    private AppUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository, passwordEncoder, new JwtTokenProvider(SECRET, 604800L), true);
    }

    private AppUser savedUser(String email) {
        AppUser user = AppUser.create(email, passwordEncoder.encode(RAW_PASSWORD), "닉네임");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    @DisplayName("회원가입하면 토큰과 사용자 정보를 함께 돌려준다")
    void signupIssuesToken() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.save(any(AppUser.class))).willAnswer(call -> {
            AppUser user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });

        AuthResponse response = authService.signup(
                new SignupRequest("duing@example.com", RAW_PASSWORD, "두잉"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 가입을 막는다")
    void signupRejectsDuplicateEmail() {
        given(userRepository.existsByEmail(anyString())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("duing@example.com", RAW_PASSWORD, "두잉")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("대소문자만 다른 이메일로도 로그인된다")
    void loginIsCaseInsensitive() {
        given(userRepository.findByEmail("duing@example.com"))
                .willReturn(Optional.of(savedUser("duing@example.com")));

        AuthResponse response = authService.login(
                new LoginRequest("DuIng@Example.com", RAW_PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인에 실패한다")
    void loginRejectsWrongPassword() {
        given(userRepository.findByEmail(anyString()))
                .willReturn(Optional.of(savedUser("duing@example.com")));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("duing@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("없는 이메일도 비밀번호 오류와 같은 응답을 준다 — 가입 여부가 새면 안 된다")
    void loginDoesNotRevealAccountExistence() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nobody@example.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("테스트 로그인이 꺼진 환경에서는 거부한다")
    void testLoginBlockedWhenDisabled() {
        AuthService disabled = new AuthService(
                userRepository, passwordEncoder, new JwtTokenProvider(SECRET, 604800L), false);

        assertThatThrownBy(() -> disabled.testLogin(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEST_LOGIN_DISABLED);
    }

    @Test
    @DisplayName("테스트 로그인은 슬롯 계정으로만 들어간다")
    void testLoginUsesSlotAccount() {
        given(userRepository.findByEmail("test@skinplate.app"))
                .willReturn(Optional.of(savedUser("test@skinplate.app")));

        AuthResponse response = authService.testLogin(1);

        assertThat(response.user().email()).isEqualTo("test@skinplate.app");
    }
}
