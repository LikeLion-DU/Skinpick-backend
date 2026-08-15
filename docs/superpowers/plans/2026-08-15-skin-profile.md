# 피부 프로필 확장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 목업 "피부설정"의 피부 고민(복수)·생활 습관 3종을 백엔드에 저장하고, 추천을 측정 2 + 신고 1 + 습관 1 슬롯으로 확장한다.

**Architecture:** 스펙은 `docs/superpowers/specs/2026-08-15-skin-profile-design.md`. PR ①(Task 0~5)이 저장 API, PR ②(Task 6~9)가 추천 반영. ②는 ①의 머지에 의존한다 — 순차 진행.

**Tech Stack:** Spring Boot 3.3.5 · Java 21 · JPA(Hibernate, `ddl-auto: validate`) · Flyway · JUnit5 + Mockito + AssertJ

## Global Constraints

- 기존 Flyway 마이그레이션 수정 금지. 신규 `V5__skin_profile.sql`만 추가
- Entity: `@NoArgsConstructor(PROTECTED)` + 행위 메서드, setter 금지. DTO 는 전부 record. 연관·컬렉션은 전부 LAZY
- 자가 신고값은 점수 계산(PlateContext·Rule Engine)에 넣지 않는다 — 추천 반영만 허용 (스펙 §0-1)
- 확정 문장 ①: "추천은 최초 추천 생성 시점의 프로필을 기준으로 생성하며, 생성 후 결과는 고정한다"
- 확정 문장 ②: "추천 슬롯은 측정 최대 2 + 자가 신고 최대 1 + 습관 최대 1이며, 해당 원천의 데이터가 없으면 해당 슬롯은 비워둔다"
- 커밋: Conventional Commits + 한국어 (`feat(user): …`). **Claude 서명/Co-Authored-By 금지**
- `git add` 는 항상 파일을 지정한다. 워킹트리에 무관한 수정이 남아 있을 수 있다
- 테스트 실행: `./gradlew test` (전체) / `./gradlew test --tests '클래스명'` (단건)

---

### Task 0: 브랜치 준비 + 잔여 문서 수정 분리

**Files:** 없음 (git 만)

- [ ] **Step 1: 워킹트리의 기존 문서 수정을 develop 에 따로 커밋**

`SkinPlate_DTO_Domain.md`에 이번 작업과 무관한 미커밋 수정(analyze 응답의 `foodAnalysisId` optional 문서화)이 있다. PR 에 섞이지 않게 먼저 분리 커밋한다.

```bash
git checkout develop
git add SkinPlate_DTO_Domain.md
git commit -m "docs(plate): analyze 응답에 foodAnalysisId 가 없음을 명시"
git push origin develop   # push 없이는 PR ① diff(origin/develop 기준)에 이 커밋이 그대로 섞인다
```

push 가 브랜치 보호로 거부되면: 이 커밋과 기존 spec/plan 문서 커밋이 PR ① 에 함께 실린다는 사실을 PR 본문에 한 줄 밝히고 진행한다.

- [ ] **Step 2: PR ① 브랜치 생성**

```bash
git checkout -b feat/skin-profile-api
```

---

### Task 1: enum 4종 추가

**Files:**
- Create: `src/main/java/com/skinplate/api/domain/user/entity/SkinConcern.java`
- Create: `src/main/java/com/skinplate/api/domain/user/entity/SleepPattern.java`
- Create: `src/main/java/com/skinplate/api/domain/user/entity/StressLevel.java`
- Create: `src/main/java/com/skinplate/api/domain/user/entity/ExerciseHabit.java`

**Interfaces:**
- Produces: `SkinConcern`(9종) · `SleepPattern.LACKING/NORMAL/ENOUGH` · `StressLevel.LOW/NORMAL/HIGH` · `ExerciseHabit.NONE/LIGHT/REGULAR` — 이후 모든 태스크가 이 이름을 그대로 쓴다

- [ ] **Step 1: enum 4개 파일 작성** (기존 `SkinType` 패턴 — 한국어 label)

```java
package com.skinplate.api.domain.user.entity;

/**
 * 자가 신고 피부 고민 (목업 "피부설정" 복수 선택 9종).
 * 표시·추천 보완 전용 — 점수 계산(PlateContext)에는 넣지 않는다.
 */
public enum SkinConcern {

    ACNE        ("여드름"),
    REDNESS     ("민감/홍조"),
    DARK_CIRCLE ("다크서클"),
    DRYNESS     ("건조/각질"),
    OILINESS    ("피지/유분"),
    TEXTURE     ("피부결"),
    PIGMENTATION("색조침착"),
    ELASTICITY  ("탄력 저하"),
    PUFFINESS   ("부기");

    private final String label;

    SkinConcern(String label) { this.label = label; }

    public String getLabel() { return label; }
}
```

```java
package com.skinplate.api.domain.user.entity;

/** 수면 패턴 (자가 신고). NULL = 미선택 — declaredSkinType 과 같은 의미론이다. */
public enum SleepPattern {

    LACKING("부족해요"),
    NORMAL ("보통이에요"),
    ENOUGH ("충분해요");

    private final String label;

    SleepPattern(String label) { this.label = label; }

    public String getLabel() { return label; }
}
```

```java
package com.skinplate.api.domain.user.entity;

/** 스트레스 정도 (자가 신고). NULL = 미선택. */
public enum StressLevel {

    LOW   ("낮음"),
    NORMAL("보통"),
    HIGH  ("높음");

    private final String label;

    StressLevel(String label) { this.label = label; }

    public String getLabel() { return label; }
}
```

```java
package com.skinplate.api.domain.user.entity;

/** 운동 습관 (자가 신고). NULL = 미선택. */
public enum ExerciseHabit {

    NONE   ("거의 안 함"),
    LIGHT  ("주 1-2회"),
    REGULAR("주 3회 이상");

    private final String label;

    ExerciseHabit(String label) { this.label = label; }

    public String getLabel() { return label; }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skinplate/api/domain/user/entity/SkinConcern.java \
        src/main/java/com/skinplate/api/domain/user/entity/SleepPattern.java \
        src/main/java/com/skinplate/api/domain/user/entity/StressLevel.java \
        src/main/java/com/skinplate/api/domain/user/entity/ExerciseHabit.java
git commit -m "feat(user): 피부 고민·생활 습관 enum 4종 추가"
```

---

### Task 2: V5 마이그레이션 + AppUser 확장

> V4 는 PR #28(feat/plate-daily-score)이 선점해 V5 를 쓴다.

**Files:**
- Create: `src/main/resources/db/migration/V5__skin_profile.sql`
- Modify: `src/main/java/com/skinplate/api/domain/user/entity/AppUser.java` (필드 블록은 `declaredSkinType` 아래, 행위 메서드는 `declareSkinType` 아래)

**Interfaces:**
- Consumes: Task 1 의 enum 4종
- Produces: `AppUser.getSkinConcerns(): Set<SkinConcern>` · `getSleepPattern()/getStressLevel()/getExerciseHabit()` · `updateSkinConcerns(Collection<SkinConcern>)` · `changeSleepPattern(SleepPattern)` · `changeStressLevel(StressLevel)` · `changeExerciseHabit(ExerciseHabit)`

- [ ] **Step 1: V5 작성**

```sql
-- 피부 프로필 (목업 "피부설정") — 자가 신고 고민·생활 습관.
-- 습관 3종은 NULL = 미선택 (declared_skin_type 과 같은 의미론).
-- 고민은 복수 선택이라 별도 테이블. PK(user_id, concern) 가 중복 선택을 DB 에서 막는다.
ALTER TABLE app_user ADD COLUMN sleep_pattern  VARCHAR(20);
ALTER TABLE app_user ADD COLUMN stress_level   VARCHAR(20);
ALTER TABLE app_user ADD COLUMN exercise_habit VARCHAR(20);

CREATE TABLE user_skin_concern (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    concern VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, concern)
);
```

- [ ] **Step 2: AppUser 에 필드 추가** — `declaredSkinType` 필드와 `lastLoginAt` 사이에 삽입

```java
    /**
     * 자가 신고 피부 고민 (복수 선택). 표시·추천 보완 전용 — 점수 계산에는 넣지 않는다.
     *
     * @Column(name) 을 빠뜨리면 기본 이름이 skin_concerns 가 되어
     * ddl-auto: validate 가 V5 의 concern 컬럼과 어긋나 기동에서 죽는다.
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
```

import 추가: `java.util.Collection`, `java.util.HashSet`, `java.util.Set` (jakarta.persistence.* 는 이미 와일드카드).

- [ ] **Step 3: AppUser 에 행위 메서드 추가** — `declareSkinType` 아래

```java
    public void updateSkinConcerns(Collection<SkinConcern> concerns) {
        // 컬렉션 참조 교체가 아니라 내용 교체 — Hibernate 가 delete+insert 로 처리한다
        this.skinConcerns.clear();
        this.skinConcerns.addAll(concerns);
    }

    public void changeSleepPattern(SleepPattern sleepPattern)    { this.sleepPattern = sleepPattern; }

    public void changeStressLevel(StressLevel stressLevel)       { this.stressLevel = stressLevel; }

    public void changeExerciseHabit(ExerciseHabit exerciseHabit) { this.exerciseHabit = exerciseHabit; }
```

- [ ] **Step 4: 전체 테스트 회귀 확인** (기존 픽스처가 새 필드에서 NPE 나지 않는지)

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 실패 0

- [ ] **Step 5: 기동 검증 — Flyway V5 적용 + `ddl-auto: validate` 통과**

```bash
docker compose up -d postgres
set -a && source .env && set +a
timeout 90 ./gradlew bootRun > /tmp/skinplate-bootrun.log 2>&1 || true   # 90초 후 자동 종료
grep "계정 생성" /tmp/skinplate-bootrun.log        # 나오면 Flyway V5 + validate 통과
grep -A3 "APPLICATION FAILED" /tmp/skinplate-bootrun.log && echo "기동 실패 — 로그 확인" || true
```

이후 스키마 불변식 확인 (user_skin_concern 의 PK 가 실제로 생겼는지):

```bash
docker exec -i skinplate-db psql -U skinplate -d skinplate -tAc \
  "select conname, contype::text from pg_constraint
    where conrelid='user_skin_concern'::regclass order by conname;"
```

Expected: `user_skin_concern_pkey|p` 포함

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V5__skin_profile.sql \
        src/main/java/com/skinplate/api/domain/user/entity/AppUser.java
git commit -m "feat(user): 피부 프로필 컬럼·컬렉션 추가 — V5 마이그레이션"
```

---

### Task 3: PATCH /auth/me 프로필 저장 (TDD)

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/auth/dto/UpdateProfileRequest.java`
- Modify: `src/main/java/com/skinplate/api/domain/auth/dto/MeResponse.java`
- Modify: `src/main/java/com/skinplate/api/domain/auth/service/AuthService.java:99-108` (updateProfile)
- Test: `src/test/java/com/skinplate/api/domain/auth/AuthServiceTest.java`

컨트롤러는 변경 없음 — `AuthController` 의 PATCH 가 `UpdateProfileRequest` 를 이미 그대로 받는다.

**Interfaces:**
- Consumes: Task 2 의 AppUser 메서드
- Produces: `UpdateProfileRequest(SkinType, String, List<SkinConcern>, SleepPattern, StressLevel, ExerciseHabit)` · `MeResponse.skinConcerns(): List<SkinConcern>`(항상 배열, 빈 배열=미설정) · `MeResponse.sleepPattern()/stressLevel()/exerciseHabit()`(null 이면 키 생략)

- [ ] **Step 1: 생성자 호출처 확인** — record 인자 수가 바뀌므로 기존 호출처를 먼저 찾는다

Run: `grep -rn "new UpdateProfileRequest\|new MeResponse" src/`
Expected: `MeResponse.from` 내부 1건 (그 외가 나오면 Step 4 에서 함께 고친다)

- [ ] **Step 2: 실패하는 테스트 작성** — `AuthServiceTest.java` 끝에 추가

import 추가: `com.skinplate.api.domain.auth.dto.MeResponse`, `com.skinplate.api.domain.auth.dto.UpdateProfileRequest`, `com.skinplate.api.domain.user.entity.SkinConcern`, `com.skinplate.api.domain.user.entity.SleepPattern`, `java.util.List`

```java
    @Test
    @DisplayName("프로필(고민·습관)은 보낸 필드만 갱신된다 — 응답은 enum 선언 순으로 고정")
    void updateProfilePartially() {
        AppUser user = savedUser("duing@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MeResponse afterConcerns = authService.updateProfile(1L, new UpdateProfileRequest(
                null, null, List.of(SkinConcern.REDNESS, SkinConcern.ACNE), null, null, null));

        assertThat(afterConcerns.skinConcerns())
                .containsExactly(SkinConcern.ACNE, SkinConcern.REDNESS);   // 선언 순
        assertThat(afterConcerns.sleepPattern()).isNull();

        MeResponse afterSleep = authService.updateProfile(1L, new UpdateProfileRequest(
                null, null, null, SleepPattern.LACKING, null, null));

        // skinConcerns 를 안 보냈으니(null) 그대로다
        assertThat(afterSleep.skinConcerns())
                .containsExactly(SkinConcern.ACNE, SkinConcern.REDNESS);
        assertThat(afterSleep.sleepPattern()).isEqualTo(SleepPattern.LACKING);
    }

    @Test
    @DisplayName("skinConcerns 빈 배열은 전부 해제다 — 변경 없음으로 무시되면 안 된다")
    void emptyConcernsClearsAll() {
        AppUser user = savedUser("duing@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        authService.updateProfile(1L, new UpdateProfileRequest(
                null, null, List.of(SkinConcern.ACNE), null, null, null));

        MeResponse cleared = authService.updateProfile(1L, new UpdateProfileRequest(
                null, null, List.of(), null, null, null));

        assertThat(cleared.skinConcerns()).isEmpty();
    }
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'AuthServiceTest'`
Expected: **컴파일 실패** — `UpdateProfileRequest` 6-인자 생성자 없음

- [ ] **Step 4: 구현**

`UpdateProfileRequest.java` 전체 교체:

```java
package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
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
 * 습관 3종은 UI 에 해제 개념이 없어 null = 변경 없음으로 충분하다.
 */
public record UpdateProfileRequest(

        SkinType declaredSkinType,

        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해 주세요.")
        String nickname,

        List<SkinConcern> skinConcerns,

        SleepPattern sleepPattern,

        StressLevel stressLevel,

        ExerciseHabit exerciseHabit
) {
    public boolean hasSkinType()      { return declaredSkinType != null; }
    public boolean hasNickname()      { return nickname != null && !nickname.isBlank(); }
    public boolean hasSkinConcerns()  { return skinConcerns != null; }   // [] = 전부 해제
    public boolean hasSleepPattern()  { return sleepPattern != null; }
    public boolean hasStressLevel()   { return stressLevel != null; }
    public boolean hasExerciseHabit() { return exerciseHabit != null; }
}
```

`MeResponse.java` 전체 교체:

```java
package com.skinplate.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;

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
           (빈 Set 은 null 이 아니다). 습관 3종만 키 생략 규칙을 따른다. */
        List<SkinConcern> skinConcerns,

        SleepPattern sleepPattern,
        StressLevel stressLevel,
        ExerciseHabit exerciseHabit,

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
                user.isTestAccount(),
                user.getCreatedAt());
    }
}
```

`AuthService.updateProfile` 에 4줄 추가 (기존 두 if 아래):

```java
        if (request.hasSkinConcerns()) user.updateSkinConcerns(new HashSet<>(request.skinConcerns()));
        if (request.hasSleepPattern())  user.changeSleepPattern(request.sleepPattern());
        if (request.hasStressLevel())   user.changeStressLevel(request.stressLevel());
        if (request.hasExerciseHabit()) user.changeExerciseHabit(request.exerciseHabit());
```

import 추가: `java.util.HashSet`

- [ ] **Step 5: 테스트 통과 + 전체 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (신규 2개 포함 전부 통과)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skinplate/api/domain/auth/dto/UpdateProfileRequest.java \
        src/main/java/com/skinplate/api/domain/auth/dto/MeResponse.java \
        src/main/java/com/skinplate/api/domain/auth/service/AuthService.java \
        src/test/java/com/skinplate/api/domain/auth/AuthServiceTest.java
git commit -m "feat(auth): PATCH /auth/me 로 피부 프로필(고민·습관) 저장"
```

---

### Task 4: 슬롯 1 시연 프로필 seed (TDD)

**Files:**
- Modify: `src/main/java/com/skinplate/api/global/init/TestAccountInitializer.java:72-85` (run)
- Test: `src/test/java/com/skinplate/api/global/init/TestAccountInitializerTest.java`

**Interfaces:**
- Consumes: Task 2 의 `updateSkinConcerns`/`changeSleepPattern`
- Produces: 슬롯 1(test@skinplate.app)에 DARK_CIRCLE + LACKING seed — PR ② 의 시연 조합(측정 2+신고 1+습관 1) 전제

- [ ] **Step 1: 실패하는 테스트 작성** — `TestAccountInitializerTest.java` 에 추가

import 추가: `com.skinplate.api.domain.user.entity.AppUser`, `com.skinplate.api.domain.user.entity.SkinConcern`, `com.skinplate.api.domain.user.entity.SleepPattern`, `com.skinplate.api.domain.user.repository.AppUserRepository`, `org.springframework.security.crypto.password.PasswordEncoder`, `org.springframework.test.util.ReflectionTestUtils`, `java.util.Optional`, static `org.mockito.ArgumentMatchers.any/anyString`, static `org.mockito.BDDMockito.given`, static `org.mockito.Mockito.mock`

```java
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'TestAccountInitializerTest'`
Expected: FAIL — skinConcerns 가 비어 있음

- [ ] **Step 3: 구현** — `run()` 끝(두 for 루프 아래)에 `seedDemoProfile();` 호출 추가, 메서드 신설

```java
    /**
     * 슬롯 1 시연 프로필. 시연 지표(38/52/64/25/78)의 측정 2(홍조·건조)와 합쳐
     * 추천 슬롯 네 원천(측정 2 + 신고 1 + 습관 1)이 전부 화면에 나오게 한다.
     * 프로필이 비어 있을 때만 심는다 — 시연 중 바꾼 값은 재기동해도 유지된다.
     */
    private void seedDemoProfile() {
        userRepository.findByEmail(SLOT_ACCOUNTS.get(0).email()).ifPresent(user -> {
            if (user.getSkinConcerns().isEmpty() && user.getSleepPattern() == null) {
                user.updateSkinConcerns(Set.of(SkinConcern.DARK_CIRCLE));
                user.changeSleepPattern(SleepPattern.LACKING);
            }
        });
    }
```

import 추가: `com.skinplate.api.domain.user.entity.SkinConcern`, `com.skinplate.api.domain.user.entity.SleepPattern`, `java.util.Set`

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'TestAccountInitializerTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skinplate/api/global/init/TestAccountInitializer.java \
        src/test/java/com/skinplate/api/global/init/TestAccountInitializerTest.java
git commit -m "feat(user): 슬롯 1 시연 프로필 seed — 다크서클·수면 부족"
```

---

### Task 5: PR ① 문서 갱신 + PR 생성

**Files:**
- Modify: `SkinPlate_PRD.md` — §12.1 ERD(1401행 부근) · §14.3 ④-b(2084행 부근) · §4.4.1 부근에 원칙 적용 범위 한 줄
- Modify: `SkinPlate_DTO_Domain.md` — §1.4 마이그레이션(272행 부근) · AppUser(1283행 부근) · UpdateProfileRequest(2475행 부근) · MeResponse(2530행 부근)

- [ ] **Step 1: PRD 갱신** — 각 섹션을 열어 주변 서식에 맞춰 다음 내용을 반영

1. §12.1 ERD: `app_user` 에 `sleep_pattern VARCHAR(20) NULL` · `stress_level VARCHAR(20) NULL` · `exercise_habit VARCHAR(20) NULL` 추가. 신규 테이블 `user_skin_concern(user_id FK→app_user CASCADE, concern VARCHAR(20), PK(user_id, concern))` 추가
2. §14.3 ④-b PATCH /auth/me: 요청 필드 `skinConcerns`(SkinConcern 배열, `[]`=전부 해제, 생략=변경 없음) · `sleepPattern` · `stressLevel` · `exerciseHabit` 추가. 응답(MeResponse)에 같은 4필드 — `skinConcerns` 는 항상 배열(빈 배열=미설정), 습관 3종은 null 시 키 생략
3. §4.4.1 말미에 한 줄: "자가 신고값(피부 타입·고민·생활 습관)을 점수 계산에 넣지 않는 원칙은 **점수 계산 한정**이다 — 추천 보완(§18.9)에는 쓴다."

- [ ] **Step 2: 설계서(SkinPlate_DTO_Domain.md) 갱신**

1. §1.4 마이그레이션 목록에 V5 블록(Task 2 의 SQL 전문) 추가
2. AppUser 코드 블록을 Task 2 완료본으로 교체
3. UpdateProfileRequest · MeResponse 코드 블록을 Task 3 완료본으로 교체

- [ ] **Step 3: Commit + PR 생성**

```bash
git add SkinPlate_PRD.md SkinPlate_DTO_Domain.md
git commit -m "docs(spec): 피부 프로필을 ERD·API 명세에 반영"
git push -u origin feat/skin-profile-api
gh pr create --base develop --title "feat(user): 피부 프로필(고민·생활 습관) 저장 API" --body "$(cat <<'EOF'
## 🚀 작업 내용
목업 피부설정 화면의 세 블록 중 백엔드에 없던 두 블록을 채웠습니다. 피부 고민(복수 선택 9종)과 생활 습관(수면·스트레스·운동)을 PATCH /auth/me 로 저장하고 GET /auth/me 로 돌려줍니다. 시연 슬롯 1 계정에는 다크서클·수면 부족 프로필을 미리 심어 두었습니다.

## 🤔 고민했던 내용
빈 배열과 생략을 구분하는 문제가 핵심이었습니다. 고민 목록만은 [] 가 "전부 해제"라서, 기존 hasNickname 의 isBlank 패턴을 그대로 쓰면 해제가 조용히 무시됩니다. null 검사만 하도록 갈랐고 테스트로 못 박았습니다. 응답의 고민 목록은 항상 배열로 나가는데, non_null 직렬화가 컬렉션에는 통하지 않기 때문입니다(빈 Set 은 null 이 아닙니다).

## 💬 리뷰 중점사항
LAZY 컬렉션을 응답에 담기 전에 트랜잭션 안에서 정렬하며 초기화하는 부분과, V5 의 컬렉션 테이블 컬럼명이 엔티티 매핑과 정확히 일치하는지 봐주시면 좋겠습니다. 자가 신고값은 점수 계산에 넣지 않는 원칙은 그대로이고, 추천 반영은 다음 PR 입니다.
EOF
)"
```

- [ ] **Step 4: 여기서 중단하고 PR ① 머지를 기다린다** — Task 6 이후는 머지된 develop 에서 분기해야 한다. 서브에이전트는 머지할 수 없으므로 사람 확인 지점이다.

---

### Task 6: (PR ②) 추천 축 7종 + 후보·문구 확장 (TDD)

> **선행 조건**: PR ① 이 develop 에 머지된 뒤 진행한다.

```bash
git checkout develop && git pull
git checkout -b feat/recommendation-profile
```

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationCandidates.java` (Concern enum 19행 · TABLE 23-33행 · REASONS 83-109행)
- Test: `src/test/java/com/skinplate/api/domain/recommendation/RecommendationCandidatesTest.java`

**Interfaces:**
- Produces: `Concern` 12종 (기존 5 + DARK_CIRCLE, PIGMENTATION, ELASTICITY, PUFFINESS, SLEEP_LACK, STRESS_HIGH, EXERCISE_NONE) · 각각 `of(concern)` 후보 보유

- [ ] **Step 1: 실패하는 테스트 작성** — `RecommendationCandidatesTest.java` 에 추가

```java
    @Test
    @DisplayName("H. 12개 축 전부 후보 표가 있다 — enum 만 늘리고 표를 빠뜨리면 NPE 로 무대에서 죽는다")
    void everyConcernHasCandidates() {
        for (Concern concern : Concern.values()) {
            assertThat(RecommendationCandidates.of(concern))
                    .as("%s 의 후보", concern).isNotNull();
        }
        assertThat(Concern.values()).hasSize(12);
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'RecommendationCandidatesTest'`
Expected: FAIL — `hasSize(12)` (현재 5)

- [ ] **Step 3: 구현** — `RecommendationCandidates.java` 수정 3곳

Concern enum 교체:

```java
    /** 추천 축. 앞 5개는 측정(SkinMetrics), 뒤 7개는 자가 신고 고민·습관에서만 진입한다. */
    public enum Concern {
        DRY, REDNESS, TROUBLE, OILY, BARRIER_WEAK,
        DARK_CIRCLE, PIGMENTATION, ELASTICITY, PUFFINESS,
        SLEEP_LACK, STRESS_HIGH, EXERCISE_NONE
    }
```

TABLE 교체 — **12쌍은 `Map.of` 의 10쌍 한계를 넘으므로 `Map.ofEntries` 로 바꾼다**:

```java
    private static final Map<Concern, Candidates> TABLE = Map.ofEntries(
            Map.entry(Concern.DRY,          new Candidates(List.of("연어", "아보카도", "오이", "견과류"),
                                                           List.of("커피", "술"))),
            Map.entry(Concern.REDNESS,      new Candidates(List.of("브로콜리", "녹차", "토마토"),
                                                           List.of("매운 음식", "술"))),
            Map.entry(Concern.TROUBLE,      new Candidates(List.of("키위", "고구마", "견과류"),
                                                           List.of("탄산음료", "초콜릿", "튀김"))),
            Map.entry(Concern.OILY,         new Candidates(List.of("채소", "두부", "흰살생선"),
                                                           List.of("튀김", "라면", "패스트푸드"))),
            Map.entry(Concern.BARRIER_WEAK, new Candidates(List.of("연어", "달걀", "아몬드"),
                                                           List.of("인스턴트", "가공육"))),
            // 자가 신고 전용 축 — 측정 지표로는 볼 수 없는 고민이다
            Map.entry(Concern.DARK_CIRCLE,  new Candidates(List.of("시금치", "달걀"),
                                                           List.of("술"))),
            Map.entry(Concern.PIGMENTATION, new Candidates(List.of("토마토", "키위", "파프리카"),
                                                           List.of("술"))),
            Map.entry(Concern.ELASTICITY,   new Candidates(List.of("닭가슴살", "달걀", "베리류"),
                                                           List.of("탄산음료"))),
            Map.entry(Concern.PUFFINESS,    new Candidates(List.of("오이", "바나나"),
                                                           List.of("라면", "가공육"))),
            // 습관 축 — 나쁜 값일 때만 트리거된다
            Map.entry(Concern.SLEEP_LACK,   new Candidates(List.of("바나나", "우유"),
                                                           List.of("커피", "술"))),
            Map.entry(Concern.STRESS_HIGH,  new Candidates(List.of("견과류", "녹차", "연어"),
                                                           List.of("커피"))),
            Map.entry(Concern.EXERCISE_NONE, new Candidates(List.of("두부", "달걀", "닭가슴살"),
                                                            List.of("패스트푸드"))));
```

REASONS — 재사용 음식 문구 5건을 두 맥락에 모두 통하게 교체, 신규 6건 추가 (음식별 전역 1문구 원칙 유지):

```java
            // 교체 (기존 문구가 새 맥락에서 어색해지는 것들)
            Map.entry("오이",      "수분이 대부분이라 물기를 채우고 붓기를 가라앉히는 데 좋습니다."),
            Map.entry("커피",      "카페인이 수분을 빼앗고 잠들기도 어렵게 만듭니다."),
            Map.entry("탄산음료",  "당류가 많아 트러블과 탄력 저하를 함께 부추길 수 있습니다."),
            Map.entry("라면",      "나트륨이 높아 붓기를 부르고 수분을 빼앗아 갑니다."),
            Map.entry("가공육",    "나트륨과 첨가물이 많아 붓기와 장벽 회복 모두에 부담이 됩니다."),
            // 신규
            Map.entry("시금치",    "철분과 루테인이 들어 있어 눈가 그늘 관리에 곁들이기 좋습니다."),
            Map.entry("파프리카",  "비타민C가 풍부해 칙칙해진 톤을 관리하는 데 도움이 됩니다."),
            Map.entry("닭가슴살",  "단백질이 풍부해 피부 탄력의 재료를 채워 줍니다."),
            Map.entry("베리류",    "안토시아닌 같은 항산화 성분이 탄력 저하를 늦추는 데 좋습니다."),
            Map.entry("바나나",    "칼륨이 나트륨 배출을 도와 붓기를 가라앉히고 저녁 간식으로도 부담이 없습니다."),
            Map.entry("우유",      "트립토판이 들어 있어 잠들기 어려운 날 저녁에 알맞습니다."));
```

(기존 항목 중 위 5건은 제거하고 교체본만 남긴다 — `Map.ofEntries` 는 키 중복 시 기동에서 죽는다)

- [ ] **Step 4: 통과 확인** — 기존 전역 불변식(추천∩주의 공집합 · 고유 문구)이 12종을 자동 커버한다

Run: `./gradlew test --tests 'RecommendationCandidatesTest'`
Expected: PASS (H 포함, 84행 공집합·99행 고유 문구 테스트도 통과)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationCandidates.java \
        src/test/java/com/skinplate/api/domain/recommendation/RecommendationCandidatesTest.java
git commit -m "feat(recommendation): 추천 축 7종·후보 표 확장"
```

---

### Task 7: 신고 고민 → 추천 축 매핑 (TDD)

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationCandidates.java` (`of` 메서드 아래)
- Test: `src/test/java/com/skinplate/api/domain/recommendation/RecommendationCandidatesTest.java`

**Interfaces:**
- Consumes: Task 1 `SkinConcern` · Task 6 `Concern` 12종
- Produces: `RecommendationCandidates.mapDeclared(SkinConcern): Concern` — 전사 함수

- [ ] **Step 1: 실패하는 테스트 작성**

import 추가: `com.skinplate.api.domain.user.entity.SkinConcern`

```java
    @Test
    @DisplayName("G. 신고 9종은 전부 추천 축으로 매핑되고, 그 축은 후보 표에 있다")
    void everyDeclaredConcernMapsToACandidateTable() {
        for (SkinConcern declared : SkinConcern.values()) {
            Concern mapped = RecommendationCandidates.mapDeclared(declared);
            assertThat(RecommendationCandidates.of(mapped))
                    .as("%s → %s 의 후보", declared, mapped).isNotNull();
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'RecommendationCandidatesTest'`
Expected: **컴파일 실패** — `mapDeclared` 없음

- [ ] **Step 3: 구현** — `of()` 아래에 추가

```java
    /**
     * 자가 신고 고민 → 추천 축. (설계서 2026-08-15 §3)
     * switch 가 전사라서 SkinConcern 에 값을 추가하고 여기를 빠뜨리면 컴파일이 깨진다.
     */
    public static Concern mapDeclared(com.skinplate.api.domain.user.entity.SkinConcern concern) {
        return switch (concern) {
            case ACNE         -> Concern.TROUBLE;
            case REDNESS      -> Concern.REDNESS;
            case DRYNESS      -> Concern.DRY;
            case OILINESS     -> Concern.OILY;
            case TEXTURE      -> Concern.BARRIER_WEAK;
            case DARK_CIRCLE  -> Concern.DARK_CIRCLE;
            case PIGMENTATION -> Concern.PIGMENTATION;
            case ELASTICITY   -> Concern.ELASTICITY;
            case PUFFINESS    -> Concern.PUFFINESS;
        };
    }
```

(파일 상단에 `import com.skinplate.api.domain.user.entity.SkinConcern;` 를 추가하고 시그니처를 `mapDeclared(SkinConcern concern)` 로 줄여도 된다 — 기존 import 스타일에 맞춘다)

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'RecommendationCandidatesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationCandidates.java \
        src/test/java/com/skinplate/api/domain/recommendation/RecommendationCandidatesTest.java
git commit -m "feat(recommendation): 신고 고민 9종 → 추천 축 매핑"
```

---

### Task 8: 추천 슬롯 조립 — 측정 2 + 신고 1 + 습관 1 (TDD)

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationService.java` (`build` 92-131행, 74-76행 주석)
- Test: `src/test/java/com/skinplate/api/domain/recommendation/RecommendationServiceTest.java`

**Interfaces:**
- Consumes: Task 2 AppUser 프로필 접근자 · Task 6 `Concern`/후보 · Task 7 `mapDeclared`
- Produces: 확정 문장 ② 의 슬롯 동작 (외부 API 변화 없음)

- [ ] **Step 1: 픽스처 확장** — **기존 `givenAnalysis(SkinMetrics)` 메서드(103-113행)를 아래 두 메서드로 교체**한다 (그냥 추가하면 시그니처 중복으로 컴파일 에러)

import 추가: `com.skinplate.api.domain.user.entity.ExerciseHabit`, `com.skinplate.api.domain.user.entity.SkinConcern`, `com.skinplate.api.domain.user.entity.SleepPattern`, `com.skinplate.api.domain.user.entity.StressLevel`, `java.util.Set`, `java.util.function.Consumer`

```java
    private void givenAnalysis(SkinMetrics metrics) {
        givenAnalysis(metrics, user -> {});
    }

    private void givenAnalysis(SkinMetrics metrics, Consumer<AppUser> profile) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        profile.accept(user);

        SkinAnalysis analysis = SkinAnalysis.create(user, metrics, 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(analysis));
        given(skinAnalysisRepository.findForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
    }
```

- [ ] **Step 2: 실패하는 테스트 4개 작성**

```java
    @Test
    @DisplayName("시연 seed 조합 — 측정 2 + 신고 1 + 습관 1 네 원천이 전부 화면에 나온다")
    void demoSeed_showsAllFourSources() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78), user -> {
            user.updateSkinConcerns(Set.of(SkinConcern.DARK_CIRCLE));
            user.changeSleepPattern(SleepPattern.LACKING);
        });

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토",          // 측정: 홍조
                                 "연어", "아보카도", "오이", "견과류",   // 측정: 건조
                                 "시금치", "달걀",                       // 신고: 다크서클
                                 "바나나", "우유");                      // 습관: 수면 부족
        assertThat(response.avoid()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("매운 음식", "술", "커피");
    }

    @Test
    @DisplayName("신고 고민이 측정과 전부 겹치면 신고 슬롯은 비워둔다 — 같은 축을 두 번 세지 않는다")
    void declaredOverlappingMeasured_leavesSlotEmpty() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78), user ->
                user.updateSkinConcerns(Set.of(SkinConcern.REDNESS, SkinConcern.DRYNESS)));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토", "연어", "아보카도", "오이", "견과류");
    }

    @Test
    @DisplayName("피부가 멀쩡해도 신고 고민·나쁜 습관이 있으면 그 근거로 추천이 생긴다")
    void healthySkinWithProfile_stillRecommends() {
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.updateSkinConcerns(Set.of(SkinConcern.ACNE));
            user.changeStressLevel(StressLevel.HIGH);
        });

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("키위", "고구마", "견과류",   // 신고: 여드름 → TROUBLE
                                 "녹차", "연어");               // 습관: 스트레스 (견과류는 중복 제거)
    }

    @Test
    @DisplayName("습관이 좋은 값이거나 여러 개 나빠도 — 슬롯은 비우거나 수면>스트레스>운동 하나만")
    void habitSlot_goodValuesEmpty_priorityPicksOne() {
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.changeSleepPattern(SleepPattern.ENOUGH);
            user.changeStressLevel(StressLevel.LOW);
            user.changeExerciseHabit(ExerciseHabit.REGULAR);
        });
        assertThat(recommendationService.getOrCreate(USER_ID, ANALYSIS_ID).recommend()).isEmpty();

        saved.clear();
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.changeSleepPattern(SleepPattern.LACKING);
            user.changeStressLevel(StressLevel.HIGH);
            user.changeExerciseHabit(ExerciseHabit.NONE);
        });

        assertThat(recommendationService.getOrCreate(USER_ID, ANALYSIS_ID).recommend())
                .extracting(RecommendedFoodDto::foodName)
                .containsExactly("바나나", "우유");   // SLEEP_LACK 만 — 스트레스·운동은 밀린다
    }
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'RecommendationServiceTest'`
Expected: 신규 4개 FAIL (기존 3개는 통과 — 프로필 없는 픽스처의 회귀 가드다)

- [ ] **Step 4: 구현** — `RecommendationService.java`

`build()` 첫 줄의 `topConcerns` 호출을 `slotConcerns(analysis)` 로 바꾸고, 메서드 2개 신설:

```java
    /**
     * 추천 슬롯: 측정 최대 2 + 자가 신고 최대 1 + 습관 최대 1. (설계서 2026-08-15 §0)
     * 해당 원천의 데이터가 없으면 그 슬롯은 비워둔다.
     * 프로필은 최초 추천 생성 시점 값으로 고정된다 — 이후 바꿔도 이 분석의 추천은 안 변한다.
     */
    private List<Concern> slotConcerns(SkinAnalysis analysis) {
        List<Concern> concerns = new ArrayList<>(
                RecommendationCandidates.topConcerns(analysis.getMetrics(), TOP_CONCERN_COUNT));

        AppUser user = analysis.getUser();

        user.getSkinConcerns().stream()
                .sorted()                                    // Set 이라 입력 순서가 없다 — 선언 순으로 고정
                .map(RecommendationCandidates::mapDeclared)
                .filter(mapped -> !concerns.contains(mapped))   // 측정이 이미 본 축이면 신고 슬롯을 안 쓴다
                .findFirst()
                .ifPresent(concerns::add);

        habitConcern(user).ifPresent(concerns::add);
        return concerns;
    }

    /** 나쁜 값만 트리거. 우선순위는 수면 > 스트레스 > 운동 — 하나만 뽑는다. */
    private static Optional<Concern> habitConcern(AppUser user) {
        if (user.getSleepPattern() == SleepPattern.LACKING) return Optional.of(Concern.SLEEP_LACK);
        if (user.getStressLevel() == StressLevel.HIGH)      return Optional.of(Concern.STRESS_HIGH);
        if (user.getExerciseHabit() == ExerciseHabit.NONE)  return Optional.of(Concern.EXERCISE_NONE);
        return Optional.empty();
    }
```

import 추가: `com.skinplate.api.domain.user.entity.AppUser`, `com.skinplate.api.domain.user.entity.ExerciseHabit`, `com.skinplate.api.domain.user.entity.SleepPattern`, `com.skinplate.api.domain.user.entity.StressLevel`, `java.util.Optional`

`createOnce` **내부** 74-76행 주석(built.isEmpty() 가드 위) 수정 — "build 는 지표에서 바로 나오는 순수 계산이다" 문장을 다음으로 교체:

```java
        // 이 경우 exists 는 계속 false 지만, build 는 지표·프로필에서 바로 나오는 계산이라
        // 다음 조회에서 프로필이 생겼다면 그때 만들어진다 — "최초 생성 시점 고정"의 실제 의미다.
```

`getSkinConcerns()` 는 `getOrCreate` 의 `@Transactional` 안에서 읽힌다 — LAZY 컬렉션 초기화 문제 없음.

- [ ] **Step 5: 통과 + 전체 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — `PlateRuleEngineTest`(60/87) 포함 전부 통과 (룰 엔진은 건드리지 않았다)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skinplate/api/domain/recommendation/service/RecommendationService.java \
        src/test/java/com/skinplate/api/domain/recommendation/RecommendationServiceTest.java
git commit -m "feat(recommendation): 추천 슬롯 측정 2 + 신고 1 + 습관 1"
```

---

### Task 9: PR ② 문서 갱신 + PR 생성

**Files:**
- Modify: `SkinPlate_PRD.md` §18.9(추천 후보 표, 3471행 부근)
- Modify: `SkinPlate_DTO_Domain.md` RecommendationCandidates 블록(3980행 부근)

- [ ] **Step 1: PRD §18.9 갱신**

1. 후보 표에 신규 7축(Task 6 표 내용) 추가
2. 슬롯 규칙 명문화 — 확정 문장 두 개를 원문 그대로 기재:
   - "추천은 최초 추천 생성 시점의 프로필을 기준으로 생성하며, 생성 후 결과는 고정한다."
   - "추천 슬롯은 측정 최대 2 + 자가 신고 최대 1 + 습관 최대 1이며, 해당 원천의 데이터가 없으면 해당 슬롯은 비워둔다."
3. 시연 대본 주의: 프로필 입력 → 피부 분석 → 추천 화면 순서 고정 (먼저 열면 프로필 없는 추천으로 굳는다)
4. "자가 신고값 반영은 추천 한정 — 점수 계산 제외 원칙은 §4.4.1 참조" 한 줄 (확정 결정 ① 을 §18.9 에서도 찾을 수 있게)
5. 프론트 확인 필요 사항 기록: 시연 seed 조합에서 추천 11장 + 주의 3장 — S09 이전에 S08(Flutter) 레이아웃이 이 분량을 감당하는지 프론트 저장소에서 확인

- [ ] **Step 2: 설계서 RecommendationCandidates 블록을 Task 7 완료본으로 교체**

- [ ] **Step 3: Commit + PR 생성**

```bash
git add SkinPlate_PRD.md SkinPlate_DTO_Domain.md
git commit -m "docs(spec): 추천 슬롯 규칙과 후보 표를 §18.9 에 반영"
git push -u origin feat/recommendation-profile
gh pr create --base develop --title "feat(recommendation): 프로필 반영 추천 — 측정 2 + 신고 1 + 습관 1 슬롯" --body "$(cat <<'EOF'
## 🚀 작업 내용
추천이 AI 측정 지표만 보던 것을, 사용자가 직접 고른 피부 고민과 생활 습관까지 보도록 확장했습니다. 슬롯은 측정 최대 2 + 자가 신고 최대 1 + 습관 최대 1이며, 원천 데이터가 없으면 그 슬롯은 비웁니다. 시연 계정(슬롯 1)은 이전 PR 의 seed 와 합쳐 네 원천이 전부 화면에 나옵니다.

## 🤔 고민했던 내용
자가 신고를 측정과 동등하게 섞으면 신고값에는 심각도가 없어 정렬 근거가 무너지고, 상한을 좁게 잡으면 습관이 영영 안 보입니다. 원천별로 슬롯을 보장하는 쪽을 골랐습니다. 점수 계산에는 여전히 자가 신고값이 들어가지 않습니다 — 이 원칙은 점수 한정이고, 추천 보완은 허용으로 문서에 명시했습니다.

## 💬 리뷰 중점사항
추천은 최초 생성 시점 프로필로 고정됩니다(기존 lazy 생성·락·UNIQUE 구조 그대로). 프로필을 나중에 바꿔도 이미 만든 추천이 안 바뀌는 것이 의도인지 봐주세요. 후보 표의 전역 불변식(추천∩주의 공집합, 음식별 고유 문구)은 기존 테스트가 12축을 자동 커버합니다.
EOF
)"
```
