# 피부 프로필 확장 — 설계서

> 2026-08-15 · 대상: Skinpick-backend · 목업 "피부설정" 화면(피부 타입 / 주요 피부 고민 / 나의 생활 습관) 백엔드 지원 + 추천 반영

## 0. 확정 결정 (구현의 전제)

1. 자가 신고 프로필(고민·습관)은 **추천에 반영한다.** 점수 계산(PlateContext · Rule Engine · SkinScoreCalculator)에는 넣지 않는다 — "자가 신고값 제외" 원칙은 **점수 계산 한정**이며, 추천 반영은 허용이다.
2. **추천은 최초 추천 생성 시점의 프로필을 기준으로 생성하며, 생성 후 결과는 고정한다.** 추천은 분석 시점이 아니라 첫 조회(lazy 생성) 시점에 만들어진다. 시연 대본은 반드시 **프로필 입력 → 피부 분석 → 추천 화면** 순서를 지킨다.
3. **추천 슬롯은 측정 최대 2 + 자가 신고 최대 1 + 습관 최대 1이며, 해당 원천의 데이터가 없으면 해당 슬롯은 비워둔다.** (총 최대 4 — 세 근거가 전부 결과에서 관측 가능해야 한다)
4. 월간 리포트는 구현하지 않는다. 피부 타입(`declaredSkinType`)은 기존 그대로 두고 이번 범위에서 건드리지 않는다.

## 1. 데이터 모델

### enum 4종 — `domain/user/entity/` (기존 `SkinType` 패턴: 한국어 label 보유)

| enum | 값 (목업 라벨) |
|---|---|
| `SkinConcern` | ACNE(여드름) · REDNESS(민감/홍조) · DARK_CIRCLE(다크서클) · DRYNESS(건조/각질) · OILINESS(피지/유분) · TEXTURE(피부결) · PIGMENTATION(색조침착) · ELASTICITY(탄력 저하) · PUFFINESS(부기) |
| `SleepPattern` | LACKING(부족해요) · NORMAL(보통이에요) · ENOUGH(충분해요) |
| `StressLevel` | LOW(낮음) · NORMAL(보통) · HIGH(높음) |
| `ExerciseHabit` | NONE(거의 안 함) · LIGHT(주 1-2회) · REGULAR(주 3회 이상) |

### `V5__skin_profile.sql` (기존 마이그레이션 불변, 신규 추가만)

```sql
ALTER TABLE app_user ADD COLUMN sleep_pattern  VARCHAR(20);
ALTER TABLE app_user ADD COLUMN stress_level   VARCHAR(20);
ALTER TABLE app_user ADD COLUMN exercise_habit VARCHAR(20);

CREATE TABLE user_skin_concern (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    concern VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, concern)
);
```

- 습관 3종: nullable 컬럼. **NULL = 미선택** (`declared_skin_type`과 동일 의미론). 한 번 설정한 습관을 NULL로 되돌리는 API는 없다 — 목업 UI에 해제 개념이 없으므로 의도된 제약이다.
- 고민: `AppUser`에 `@ElementCollection(fetch = LAZY)`. **구현 시 필수 명시** (누락 시 `ddl-auto: validate` 기동 실패 / 기존 테스트 NPE):

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "user_skin_concern", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "concern")                      // 기본 이름은 skin_concerns — DDL과 어긋난다
@Enumerated(EnumType.STRING)
private Set<SkinConcern> skinConcerns = new HashSet<>();   // 초기화 누락 시 순수 객체 픽스처에서 NPE
```

## 2. API — 신규 엔드포인트 없음

### `PATCH /auth/me` 확장 (기존 "보낸 필드만 변경" 유지)

```json
{ "skinConcerns": ["ACNE", "REDNESS", "OILINESS"],
  "sleepPattern": "LACKING", "stressLevel": "NORMAL", "exerciseHabit": "LIGHT" }
```

- 필드 생략(null) = 변경 없음. `skinConcerns: []` = **전부 해제.**
- **`hasSkinConcerns()`는 null 검사만 한다.** 기존 `hasNickname()`의 `isBlank()` 패턴을 복붙하면 `[]` 해제가 조용히 무시된다 — 빈 배열 해제를 검증하는 테스트를 반드시 둔다.
- 잘못된 enum 문자열은 기존 `HttpMessageNotReadableException` 처리로 400. 응답은 갱신 결과 `MeResponse` 200 (PRD §14.3 ④-b).
- `AppUser`는 행위 메서드로만 변경 (`updateSkinConcerns` · `changeSleepPattern` · `changeStressLevel` · `changeExerciseHabit`). setter 금지 유지.

### `MeResponse` 확장

- 추가 필드: `skinConcerns`(List) · `sleepPattern` · `stressLevel` · `exerciseHabit`.
- **`skinConcerns`는 항상 배열로 내려간다. 빈 배열 = 미설정.** `non_null` 직렬화는 컬렉션에 통하지 않는다(빈 Set은 null이 아님) — 키 생략 스펙은 습관 3종에만 적용된다.
- **`from()`에서 `user.getSkinConcerns().stream().sorted().toList()`로 List화한다.** 트랜잭션 안에서 LAZY 컬렉션을 초기화하고(직렬화는 트랜잭션 밖 — `open-in-view: false`), enum 선언 순으로 응답을 고정하는 것을 한 줄로 해결한다. Set을 record 필드에 두지 않는다. `EnumSet.copyOf`는 빈 컬렉션에서 터지므로 쓰지 않는다.

## 3. 추천 반영 — `RecommendationCandidates` · `RecommendationService`

### 슬롯 구성 (확정 결정 ③)

```
measured = topConcerns(metrics, 2)                          // 기존 그대로, severity 순
declared = 고민 → Concern 매핑, measured 제외, enum 선언 순 → 첫 1개
habit    = 나쁜 값만 트리거, 수면 > 스트레스 > 운동 순 → 첫 1개
merged   = measured + declared + habit                      // 원천별 상한, distinct
```

- 고민 → Concern 매핑: ACNE→TROUBLE · REDNESS→REDNESS · DRYNESS→DRY · OILINESS→OILY · TEXTURE→BARRIER_WEAK. 나머지 4종은 신규 Concern.
- 습관 트리거: `SleepPattern.LACKING`→SLEEP_LACK · `StressLevel.HIGH`→STRESS_HIGH · `ExerciseHabit.NONE`→EXERCISE_NONE. NORMAL/ENOUGH/LOW/LIGHT/REGULAR는 트리거 없음.
- `Concern`에 7종 추가: DARK_CIRCLE · PIGMENTATION · ELASTICITY · PUFFINESS · SLEEP_LACK · STRESS_HIGH · EXERCISE_NONE (총 12종). **`TABLE`은 `Map.of` 10쌍 한계를 넘으므로 `Map.ofEntries`로 교체한다** (컴파일 에러 방지).
- `topConcerns` 시그니처는 그대로(측정 5종만 다룸). 신규 7종은 매핑·트리거 경로로만 진입한다.

### 신규 후보 (*=신규 음식, `REASONS`에 고유 문구 추가)

| Concern | recommend | avoid |
|---|---|---|
| DARK_CIRCLE | 시금치*, 달걀 | 술 |
| PIGMENTATION | 토마토, 키위, 파프리카* | 술 |
| ELASTICITY | 닭가슴살*, 달걀, 베리류* | 탄산음료 |
| PUFFINESS | 오이, 바나나* | 라면, 가공육 |
| SLEEP_LACK | 바나나*, 우유* | 커피, 술 |
| STRESS_HIGH | 견과류, 녹차, 연어 | 커피 |
| EXERCISE_NONE | 두부, 달걀, 닭가슴살* | 패스트푸드 |

- 불변식 유지(기존 테스트가 전역 단위로 자동 검증): 추천∩주의 전역 공집합 · 음식마다 고유 문구.
- 재사용 음식의 문구는 두 맥락 모두 통하게 소폭 조정한다(예: 커피 문구에 숙면 방해 추가). 음식별 전역 1문구 원칙은 유지.

### 생성·고정 의미론 (확정 결정 ②)

- `skinAnalysisId`당 1회 생성 후 고정. V3 UNIQUE · `findForUpdate` 락 · exists 재확인 구조 불변.
- 측정 0 + 프로필 0이면 기존처럼 빈 목록 → 저장 생략(exists false 유지). 나중에 프로필을 넣고 재조회하면 그때 생성된다 — 이것이 "최초 **비어있지 않은** 생성 시점 고정"이며, 부수 효과로 피부가 멀쩡해도 신고 고민·나쁜 습관이 있으면 추천이 생긴다(의도).
- `build()`가 프로필을 읽게 되므로 "지표에서 바로 나오는 순수 계산" 주석을 갱신한다. `analysis.getUser()`의 LAZY 컬렉션은 `getOrCreate`의 `@Transactional` 안에서 읽는다.

## 4. 시연 준비

- `TestAccountInitializer` 슬롯 1에 시연 seed 추가: `DARK_CIRCLE` + `SleepPattern.LACKING`. 시연 지표(38/52/64/25/78)의 측정 2개(REDNESS·DRY)와 합쳐 **측정 2 + 신고 1 + 습관 1 = 4개 원천이 전부 화면에 나온다.** 멱등 로직(존재 시 skip)은 그대로.
- S08 카드 수가 늘어난다(추천 ~9장+). Flutter S08 레이아웃 확인은 프론트 저장소 몫으로 기록만 한다.

## 5. 문서 동기화 (코드와 같은 PR에서)

- PRD: §12 ERD(`user_skin_concern` + `app_user` 컬럼 3개) · §14.3 ④-b 명세 · §18.9 추천 표(슬롯 2+1+1 규칙, 확정 결정 ①②③ 문구 포함) · "자가 신고값 제외 원칙은 점수 계산 한정" 한 줄.
- 설계서 `SkinPlate_DTO_Domain.md`: §1.4 마이그레이션 · AppUser · UpdateProfileRequest · MeResponse · RecommendationCandidates 블록.

## 6. 테스트

- `AuthServiceTest`: 부분 갱신(습관만/고민만) · `[]` 전부 해제 · 미전송 필드 불변.
- `RecommendationCandidatesTest`: 신규 매핑·트리거, 기존 불변식(전역 공집합·고유 문구)이 12종을 커버하는지.
- `RecommendationServiceTest`: 측정2+신고1+습관1 슬롯 구성 · 원천 없으면 슬롯 비움 · 측정과 겹치는 신고 제외 · 시연 seed 조합 재현성 · 프로필 없던 기존 픽스처 회귀(NPE 방지).

## 7. 브랜치 — 2 PR, 순차 머지 (②는 ①에 의존)

1. `feat/skin-profile-api` — V5 + enum 4종 + AppUser + PATCH·GET `/auth/me` + 시연 seed + 문서(ERD·④-b)
2. `feat/recommendation-profile` — Concern 7종 + 후보·문구 + 슬롯 로직 + 문서(§18.9)
