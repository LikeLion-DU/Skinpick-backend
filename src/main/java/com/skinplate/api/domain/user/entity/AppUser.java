package com.skinplate.api.domain.user.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Getter
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt 해시. 평문은 이 필드는 물론 로그에도 남기지 않는다. */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_test_account", nullable = false)
    private boolean testAccount;

    /**
     * 사용자가 스스로 고른 피부 타입. (PRD §4.4.1)
     *
     * NULL 과 UNKNOWN 은 다르다.
     *   NULL    = 건너뜀. 나중에 다시 물어봐도 된다
     *   UNKNOWN = "잘 모르겠어요"를 골랐다. 다시 물어보면 실례다
     *
     * Rule Engine 에는 넣지 않는다. PlateContext 는 계속 (SkinMetrics, FoodAnalysis) 뿐이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SkinType declaredSkinType;

    private LocalDateTime lastLoginAt;

    // ---- 팩토리 ----

    public static AppUser create(String email, String encodedPassword, String nickname) {
        AppUser user = new AppUser();
        user.email = normalizeEmail(email);
        user.password = encodedPassword;
        user.nickname = nickname;
        user.role = Role.USER;
        user.testAccount = false;
        return user;
    }

    public static AppUser createTestAccount(String email, String encodedPassword, String nickname) {
        AppUser user = create(email, encodedPassword, nickname);
        user.testAccount = true;
        return user;
    }

    // ---- 행위 ----

    public void markLoggedIn() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void declareSkinType(SkinType skinType) {
        this.declaredSkinType = skinType;
    }

    /**
     * 가입 시점에 소문자로 정규화한다.
     * Test@skinplate.app 으로 가입하고 test@skinplate.app 으로 로그인하려는
     * 사용자는 반드시 나온다. 저장 시점에 맞춰두면 조회 코드가 이 문제를 몰라도 된다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
