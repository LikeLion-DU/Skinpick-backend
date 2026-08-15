package com.skinplate.api.domain.user.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    /**
     * 자가 신고 피부 고민 (복수 선택). 표시·추천 보완 전용 — 점수 계산에는 넣지 않는다.
     *
     * @Column(name) 을 빠뜨리면 기본 이름이 skin_concerns 가 되어
     * ddl-auto: validate 가 V4 의 concern 컬럼과 어긋나 기동에서 죽는다.
     * 필드 초기화를 빠뜨리면 순수 객체 픽스처(AppUser.create)에서 NPE 다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_skin_concern", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "concern", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<SkinConcern> skinConcerns = new HashSet<>();

    /** 생활 습관 3종. NULL = 미선택 — declaredSkinType 과 같은 의미론이다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SleepPattern sleepPattern;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StressLevel stressLevel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExerciseHabit exerciseHabit;

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

    public void updateSkinConcerns(Collection<SkinConcern> concerns) {
        // 컬렉션 참조 교체가 아니라 내용 교체 — Hibernate 가 delete+insert 로 처리한다
        this.skinConcerns.clear();
        this.skinConcerns.addAll(concerns);
    }

    public void changeSleepPattern(SleepPattern sleepPattern)    { this.sleepPattern = sleepPattern; }

    public void changeStressLevel(StressLevel stressLevel)       { this.stressLevel = stressLevel; }

    public void changeExerciseHabit(ExerciseHabit exerciseHabit) { this.exerciseHabit = exerciseHabit; }

    /**
     * 가입 시점에 소문자로 정규화한다.
     * Test@skinplate.app 으로 가입하고 test@skinplate.app 으로 로그인하려는
     * 사용자는 반드시 나온다. 저장 시점에 맞춰두면 조회 코드가 이 문제를 몰라도 된다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
