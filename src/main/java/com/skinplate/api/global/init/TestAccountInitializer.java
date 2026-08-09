package com.skinplate.api.global.init;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서버 기동 시 시연·테스트용 고정 계정을 생성한다. (PRD v1.1 §16.5)
 *
 * Flyway seed SQL에 BCrypt 해시를 박지 않는 이유:
 *   - 해시는 사람이 검증할 수 없다
 *   - PasswordEncoder 설정을 바꾸면 조용히 깨진다
 *   - 앱의 인코더로 생성하면 항상 일치한다
 *
 * 이미 존재하면 건너뛰므로 몇 번 재기동해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.test-account.enabled", havingValue = "true")
public class TestAccountInitializer implements ApplicationRunner {

    /** 슬롯 1 = 발표 시연 전용 / 슬롯 2·3 = 팀 테스트 및 심사위원 체험 */
    private static final List<TestAccount> ACCOUNTS = List.of(
            // 슬롯 1은 시연 전용 — 피부 타입을 지성으로 미리 박아둔다.
            // 실측이 건조(DRY)로 나오므로 S05 에서 자가 진단 갭 코멘트가 바로 뜬다.
            new TestAccount(1, "test@skinplate.app",  "테스트유저",  SkinType.OILY),
            new TestAccount(2, "test2@skinplate.app", "테스트유저2", null),
            new TestAccount(3, "test3@skinplate.app", "테스트유저3", null)
    );

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.test-account.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encoded = passwordEncoder.encode(password);

        for (TestAccount account : ACCOUNTS) {
            if (userRepository.existsByEmail(account.email())) continue;

            AppUser user = AppUser.createTestAccount(account.email(), encoded, account.nickname());
            if (account.skinType() != null) user.declareSkinType(account.skinType());

            userRepository.save(user);
            log.info("테스트 계정 생성: {} (슬롯 {})", account.email(), account.slot());
        }
    }

    /** slot 번호로 이메일을 찾는다. AuthService의 test-login에서 사용. */
    public static String emailOfSlot(int slot) {
        return ACCOUNTS.stream()
                .filter(a -> a.slot() == slot)
                .findFirst()
                .map(TestAccount::email)
                .orElse(ACCOUNTS.get(0).email());
    }

    public record TestAccount(int slot, String email, String nickname, SkinType skinType) {}
}
