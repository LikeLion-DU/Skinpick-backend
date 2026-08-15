package com.skinplate.api.domain.auth.service;

import com.skinplate.api.domain.auth.dto.AuthResponse;
import com.skinplate.api.domain.auth.dto.LoginRequest;
import com.skinplate.api.domain.auth.dto.MeResponse;
import com.skinplate.api.domain.auth.dto.SignupRequest;
import com.skinplate.api.domain.auth.dto.UpdateProfileRequest;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.init.TestAccountInitializer;
import com.skinplate.api.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * TestAccountInitializer 를 주입해서 읽으면 prod 에서 그 빈이 없어 기동이 실패한다.
     * 프로퍼티를 직접 읽고, 값이 없으면 닫히는 쪽으로 떨어뜨린다.
     */
    private final boolean testAccountEnabled;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       @Value("${app.auth.test-account.enabled:false}") boolean testAccountEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.testAccountEnabled = testAccountEnabled;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        AppUser user = userRepository.save(AppUser.create(
                email, passwordEncoder.encode(request.password()), request.nickname()));

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 이메일이 없을 때와 비밀번호가 틀릴 때의 응답을 같게 둔다.
        // 구분하면 공격자가 가입된 이메일 목록을 수집할 수 있다.
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.markLoggedIn();
        return issueToken(user);
    }

    /**
     * 원탭 로그인. 슬롯 계정만 대상이며 개발 계정(dev*)은 들어올 수 없다.
     *
     * 플래그 검사가 이 엔드포인트를 닫는 유일한 지점이다.
     * 계정 생성을 막는 @ConditionalOnProperty 는 로그인을 막지 않는다 —
     * 시연 배포에서 심어둔 계정이 DB 에 남은 채 플래그만 내려가면
     * 이 검사가 없는 한 누구나 들어온다. (PRD §9.6 환경별 설정)
     */
    @Transactional
    public AuthResponse testLogin(int slot) {
        if (!testAccountEnabled) {
            throw new BusinessException(ErrorCode.TEST_LOGIN_DISABLED);
        }

        AppUser user = userRepository.findByEmail(TestAccountInitializer.emailOfSlot(slot))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        user.markLoggedIn();
        return issueToken(user);
    }

    public MeResponse me(Long userId) {
        return MeResponse.from(findUser(userId));
    }

    /** 보낸 필드만 바꾼다. 아무것도 안 보내면 아무것도 바뀌지 않는다. */
    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest request) {
        AppUser user = findUser(userId);

        if (request.hasSkinType()) user.declareSkinType(request.declaredSkinType());
        if (request.hasNickname()) user.changeNickname(request.nickname());
        if (request.hasSkinConcerns()) user.updateSkinConcerns(new HashSet<>(request.skinConcerns()));
        if (request.hasSleepPattern())  user.changeSleepPattern(request.sleepPattern());
        if (request.hasStressLevel())   user.changeStressLevel(request.stressLevel());
        if (request.hasExerciseHabit()) user.changeExerciseHabit(request.exerciseHabit());

        return MeResponse.from(user);
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthResponse issueToken(AppUser user) {
        String accessToken = tokenProvider.createToken(user.getId(), user.getRole());
        return AuthResponse.of(accessToken, tokenProvider.getValiditySeconds(), user);
    }

    /** 저장은 소문자로 정규화돼 있으므로 조회도 같은 규칙을 따라야 한다. */
    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
