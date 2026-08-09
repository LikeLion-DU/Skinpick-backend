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
 * 서버 기동 시 시연·테스트용 고정 계정을 생성한다. (PRD §16.5)
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

    /**
     * ① 슬롯 계정 — 원탭 로그인(POST /auth/test-login) 대상. is_test_account = true
     * 슬롯 1은 시연 전용이라 피부 타입을 지성으로 미리 박아둔다.
     * 실측이 건조(DRY)로 나오므로 S05 에서 갭 코멘트가 바로 뜬다.
     */
    private static final List<SlotAccount> SLOT_ACCOUNTS = List.of(
            new SlotAccount(1, "test@skinplate.app",  "테스트유저",  SkinType.OILY),
            new SlotAccount(2, "test2@skinplate.app", "테스트유저2", null),
            new SlotAccount(3, "test3@skinplate.app", "테스트유저3", null)
    );

    /**
     * ② 개발 계정 — 로그인 폼으로 이메일·비밀번호를 실제 입력해 테스트한다.
     *    is_test_account = false 라 일반 사용자와 동일한 경로를 탄다.
     *
     * 원탭 로그인만 쓰면 S01 로그인 폼과 실패 응답이 한 번도 안 돌아본다.
     * 피부 타입을 서로 다르게 둬서, 계정만 바꿔 로그인하면
     * S05 갭 카드의 네 분기를 전부 확인할 수 있다.
     *
     *   시연 지표(38/52/64/25/78) → observe() = DRY 기준
     *     slot 2·3  declared = null   → 갭 카드 없음, 인라인 선택 칩
     *     dev1      DRY == DRY        → 일치 메시지
     *     slot 1    OILY vs DRY       → SPECIAL["OILY→DRY"] 전용 문구
     *     dev2      SENSITIVE vs DRY  → SPECIAL 에 없음 → 폴백 문구
     *     dev3      UNKNOWN           → "오늘 측정 기준으로는 …에 가깝습니다"
     */
    private static final List<DevAccount> DEV_ACCOUNTS = List.of(
            new DevAccount("dev1@skinplate.app", "개발계정1", SkinType.DRY),
            new DevAccount("dev2@skinplate.app", "개발계정2", SkinType.SENSITIVE),
            new DevAccount("dev3@skinplate.app", "개발계정3", SkinType.UNKNOWN)
    );

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.test-account.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encodedPassword = passwordEncoder.encode(password);   // 6개 계정이 같은 해시를 공유한다

        for (SlotAccount account : SLOT_ACCOUNTS) {
            create(account.email(), encodedPassword, account.nickname(), account.skinType(),
                    true, "슬롯 " + account.slot());
        }
        for (DevAccount account : DEV_ACCOUNTS) {
            create(account.email(), encodedPassword, account.nickname(), account.skinType(),
                    false, "개발 계정");
        }
    }

    private void create(String email, String encodedPassword, String nickname,
                        SkinType skinType, boolean testAccount, String label) {

        if (userRepository.existsByEmail(email)) return;      // 멱등

        AppUser user = testAccount
                ? AppUser.createTestAccount(email, encodedPassword, nickname)
                : AppUser.create(email, encodedPassword, nickname);

        if (skinType != null) user.declareSkinType(skinType);

        userRepository.save(user);
        log.info("계정 생성: {} ({})", email, label);
    }

    /**
     * slot 번호로 이메일을 찾는다. AuthService 의 test-login 에서만 쓴다.
     * 개발 계정(dev*)은 원탭 로그인 대상이 아니다 — 로그인 폼으로만 들어간다.
     */
    public static String emailOfSlot(int slot) {
        return SLOT_ACCOUNTS.stream()
                .filter(account -> account.slot() == slot)
                .findFirst()
                .map(SlotAccount::email)
                .orElse(SLOT_ACCOUNTS.get(0).email());
    }

    public record SlotAccount(int slot, String email, String nickname, SkinType skinType) {}

    public record DevAccount(String email, String nickname, SkinType skinType) {}
}
