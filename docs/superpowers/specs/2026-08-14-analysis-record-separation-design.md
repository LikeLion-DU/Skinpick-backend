# 분석 / 기록 분리 — 최종 구현 스펙

| 항목 | 내용 |
|---|---|
| 확정일 | 2026-08-14 |
| 전제 | **분석은 임시, 기록은 명시적 선택.** `POST /plates` 의 "분석 = 저장" 을 폐기한다 |
| 금지 | DB migration · 새 상태 저장소 · R2/서버 이미지 저장 · AI 재호출 · 점수 계산 변경 |
| 재사용 | 기존 jjwt · `PlateRuleEngine` · `findForUpdate` · `raw_ai_response` 컬럼 |

---

## 1. 유저 플로우

```
음식 촬영 → POST /plates/analyze → 결과 화면(임시)
                                      │
                    ┌─────────────────┴─────────────────┐
              [기록에 저장하기]                    뒤로가기 · 나가기
                    │                                   │
        POST /plates/records                     임시 결과 폐기
                    ↓                                   ↓
        Plate 생성 → 히스토리 · 오늘의 기록 · 리포트    아무 흔적 없음
```

**분석만 한 음식은 어디에도 집계되지 않는다.** 서버에 임시 Plate 를 만들지 않으므로 정리할 것도 없다.

## 2. 왜 이 구조인가 — 두 요구가 충돌한다

| | |
|---|---|
| 서버에 임시 상태를 두지 않는다 | → 저장 시점에 음식 데이터는 클라이언트에서 올 수밖에 없다 |
| 클라이언트를 신뢰하지 않는다 | → 저장 시점에 서버가 원본을 알아야 한다 |

**둘은 동시에 성립하지 않는다.** 무상태면 데이터 출처가 클라이언트이고, 그러면 나트륨을 1850 → 200 으로 바꿔 보낼 수 있다. 서버가 점수를 다시 계산해도 *계산의 입력* 을 클라이언트가 정한다.

**서명이 이 충돌을 푼다.** 서버가 분석 결과를 서명해 돌려주고, 저장할 때 그대로 되받아 서명을 검증하면 **그 데이터가 서버 자신의 출력임이 증명된다.** 상태 0, 조작 불가, 재기동·다중 인스턴스 무관.

## 3. API 계약

### 3.1 `POST /api/v1/plates/analyze`

```
multipart/form-data
  image           file   필수
  skinAnalysisId  Long   선택 — 생략하면 최신 피부 분석
→ 201
```

`data` (`ApiResponse` 로 감싼다):

```jsonc
{
  "analysisToken": "eyJhbGciOiJIUzI1NiJ9...",   // §4
  "plateScore": 60,
  "baseScore": 70,
  "skinAnalysisId": 100,
  "summary": "...",
  "food": { "foodName": "돼지고기 김치찌개", "foodCategory": "한식/찌개",
            "cookingMethod": "BOILED", "spicy": true,
            "ingredients": [...], "nutrition": {...} },
  "feedbacks": { ... },
  "appliedRules": ["R04", "R02", "R05", "R09"]
}
```

**저장되지 않는다.** `plateId` 도 `createdAt` 도 없다 — 아직 기록이 아니기 때문이다. `SkinPlateResponse` 를 재사용하지 않고 별도 `PlateAnalysisResponse` 를 쓰는 이유가 이것이다. `plateId: null` 을 내려보내면 앱이 "저장된 것" 으로 오해할 여지가 생긴다.

`foodDetected=false` 이거나 음식명이 비면 기존대로 **422 `FOOD_NOT_DETECTED`**. `skinAnalysisId` 가 남의 것이거나 피부 분석이 하나도 없으면 **404 `SKIN_ANALYSIS_NOT_FOUND`** — **유료 호출 전에** 판정한다(기존 `resolveSkinAnalysisId` 가 이미 그렇게 한다).

### 3.2 `POST /api/v1/plates/records`

```jsonc
{ "analysisToken": "..." }     // @NotBlank
→ 201  (멱등 재요청도 201, 같은 plateId)
```

`data` 는 기존 **`SkinPlateResponse`** 그대로다 — `plateId` · `skinAnalysisId` · `plateScore` · `baseScore` · `summary` · `food` · `feedbacks` · `appliedRules` · `createdAt`.

| 실패 | 코드 |
|---|---|
| 서명 불일치 · 형식 오류 · `iss`/`aud` 불일치 | 400 `INVALID_INPUT` |
| 만료 | **422 `ANALYSIS_EXPIRED`** (신설) |
| `sub` ≠ 인증 userId | 400 `INVALID_INPUT` |
| 토큰의 `skinAnalysisId` 가 현재 사용자 소유 아님 | 404 `SKIN_ANALYSIS_NOT_FOUND` |

> **만료를 401 로 내리면 사용자가 로그아웃된다.** 앱 코드로 확인했다:
>
> ```dart
> // unauthorized_interceptor.dart:24
> final isAuthRequest = err.requestOptions.path.contains('/auth/');
> if (err.response?.statusCode == 401 && !isAuthRequest) {
>   await _tokenStorage.clear();   // ← 토큰을 지운다
>   _onUnauthorized();             // ← 로그인 화면으로 보낸다
> }
> ```
>
> `/plates/records` 는 `/auth/` 가 아니므로 **분석이 만료됐을 뿐인데 로그인 세션이 날아간다.** 그래서 401 을 쓰지 않는다.
>
> **422 를 쓴다.** 요청 자체는 정상 인증됐고("이해했지만 처리할 수 없다"), 기존 `FACE_NOT_DETECTED`·`FOOD_NOT_DETECTED` 가 같은 422 에 같은 "다시 촬영해 주세요" 계열 메시지를 쓴다 — 앱의 처리 경로와 문구 톤이 이미 맞는다.
>
> ```java
> ANALYSIS_EXPIRED (HttpStatus.UNPROCESSABLE_ENTITY, "분석 결과가 만료됐어요. 다시 촬영해 주세요."),
> ```
>
> `TOKEN_EXPIRED` 를 재사용하지 않는 이유도 같다 — 그 메시지는 "다시 로그인해 주세요" 다.

### 3.3 `POST /api/v1/plates` — 제거한다

배포된 앱이 없고 사용처는 Flutter 하나뿐이라 breaking impact 가 없다. **다만 §9 의 순서를 지킨다** — 앱 전환과 호출 0 확인이 끝난 뒤에 지운다. 남겨두면 "분석 = 기록" 경로가 살아 있어 이 기능이 우회된다.

## 4. analysisToken

### 4.1 서명 키를 분리한다 — 이게 이 설계의 보안 핵심

```java
JwtAuthenticationFilter:40   Long userId = tokenProvider.parseUserId(token);
JwtTokenProvider.parseUserId  서명·만료만 검증 — aud 를 보지 않는다
```

**인증 필터는 서명만 맞으면 통과시킨다.** 같은 `JWT_SECRET` 으로 analysisToken 을 서명하면 `sub = userId` 가 들어 있으므로 **그 토큰을 `Bearer` 로 보내도 인증이 된다.** 토큰에 `aud=plate-record` 를 넣어도 막히지 않는다 — 인증 경로가 `aud` 를 읽지 않기 때문이다.

권한 상승은 아니다(자기 자신으로 인증된다). 하지만 **30분짜리 두 번째 자격증명**이 생기고, 이 토큰은 음식 데이터라 로그·화면에 노출될 여지가 인증 토큰보다 크다.

**해법: 별도 키로 서명한다.**

```java
// 새 환경변수 없이 기존 시크릿에서 파생한다 (domain separation)
byte[] derived = Mac("HmacSHA256", JWT_SECRET).doFinal("analysis".getBytes(UTF_8));
SecretKey analysisKey = Keys.hmacShaKeyFor(derived);
```

**인증 경로에서는 서명 검증 자체가 실패한다.** `JwtTokenProvider` 와 `JwtAuthenticationFilter` 를 한 줄도 건드리지 않는다.

> 새 클래스 `AnalysisTokenProvider` 로 분리한다. `JwtTokenProvider` 에 메서드를 더하면 두 키가 한 클래스에 살게 되고, 그러면 어느 키로 서명했는지가 호출부 실수 하나로 뒤집힌다.

### 4.2 클레임

| 클레임 | 값 | 저장 API 의 검증 |
|---|---|---|
| `sub` | 인증된 userId | **현재 `@CurrentUser` 와 일치해야 한다** |
| `jti` | `UUID.randomUUID()` | 멱등성 키 (§6) |
| `iss` | `skinplate` | 일치 |
| `aud` | `plate-record` | 일치 |
| `iat` / `exp` | 발급 시각 / +30분 | 만료 거부 |
| `skinAnalysisId` | Long | **현재 사용자 소유 재확인** (§4.3) |
| `food` | 분석 원본(§4.4) | 서명으로 무결성 보장 |

**여섯 가지를 전부 검증한다** — 서명 · 만료 · `iss` · `aud` · `sub`==인증 userId · `skinAnalysisId` 소유.

### 4.3 `skinAnalysisId` 를 왜 다시 확인하나

서명이 위조를 막으므로 토큰의 값 자체는 신뢰할 수 있다. 그런데도 다시 확인하는 이유는 **시간이 흘렀기 때문**이다. 분석과 저장 사이 30분 동안 그 피부 분석이 사라졌을 수 있다. 기존 경로를 그대로 쓴다.

```java
SkinPlateService.resolveSkinAnalysis(userId, skinAnalysisId)   // findByIdAndUserId → 없으면 404
```

### 4.4 페이로드 크기 — 실측 근거

```
food_analysis.raw_ai_response   min 356 / avg 378 / max 383 bytes   (34/34 채워짐)
```

분석 원본 383B + `skinAnalysisId` + 표준 클레임 → JSON 약 450B → base64url 약 600자 → 헤더·서명 포함 **토큰 700~900자**. 요청 **본문**에 싣는다. 헤더에 넣지 않는다 — 일부 프록시의 헤더 상한(8KB)에는 여유가 있지만, 본문이 의미상 맞고 로그에 남을 위험도 낮다.

**별도 임시 저장소가 필요 없는 근거가 이 숫자다.**

## 5. `raw_ai_response` 에 `_meta` 를 더한다

```
현재 최상위 키   foodName · foodCategory · cookingMethod · spicy · foodDetected · ingredients · nutrition
읽는 코드        없음 (팩토리 인자로만 등장)
```

**최상위 형제 키로 붙인다.**

```jsonc
{ "foodName": "...", "nutrition": {...}, /* …기존 그대로… */
  "_meta": { "jti": "..." } }
```

중첩(`{"ai": {...}, "_meta": {...}}`) 을 쓰지 않는 이유 — **기존 34행과 형태가 갈린다.** 지금은 읽는 코드가 없어 안 깨지지만, 나중에 읽는 쪽이 생기면 두 형태를 다 처리해야 한다. 형제 키는 옛 행의 구조를 그대로 두고, `raw_ai_response->'_meta'->>'jti'` 가 옛 행에서 `null` 을 준다 — **그게 정확한 답이다. 그 행들엔 jti 가 없었다.**

키 충돌은 불가능하다. `FoodAnalysisPrompt.SCHEMA` 가 `additionalProperties: false` 라 AI 가 `_meta` 를 만들 수 없다.

**컬럼의 의미를 문서에서 고친다** — "AI 원본 응답" → **"AI 분석 원본 + 서버 메타데이터"**. `FoodAnalysis.rawAiResponse` javadoc 과 설계서 양쪽. 컬럼명은 바꾸지 않는다.

## 6. 멱등성

**같은 `analysisToken` 을 몇 번 보내도 기록은 하나다.** `foodName`·점수·시각 같은 추측값을 쓰지 않는다 — 같은 음식을 두 번 먹는 것은 정상이다.

저장 트랜잭션 안에서:

```
1. skinAnalysisRepository.findForUpdate(skinAnalysisId)     ← 이미 존재하는 PESSIMISTIC_WRITE
2. jti 로 기존 food_analysis 조회
3. 있으면  → 그 Plate 를 그대로 반환 (새로 만들지 않는다)
4. 없으면  → 정상 저장
```

**1번이 동시성을 끊는다.** 추천 생성에서 같은 문제를 풀려고 만든 락을 재사용한다 — 새 코드 0.

```
요청 A  락 획득 → jti 없음 → 저장 → commit
요청 B  락 대기 → 획득 → jti 발견 → 기존 Plate 반환
```

서로 다른 분석의 동시 저장은 직렬화되지 않아도 된다 — **그건 중복이 아니라 정상 기록 두 건**이다.

### 6.1 조회 쿼리

`raw_ai_response` 는 jsonb 라 JPQL 로 `->>` 를 못 쓴다. **네이티브 쿼리**를 `FoodAnalysisRepository` 에 추가한다.

```java
@Query(value = "select f.id from food_analysis f "
             + "where f.user_id = :userId and f.raw_ai_response->'_meta'->>'jti' = :jti "
             + "limit 1", nativeQuery = true)
Optional<Long> findIdByUserIdAndJti(@Param("userId") Long userId, @Param("jti") String jti);
```

**인덱스는 만들지 않는다**(마이그레이션). `idx_food_analysis_user_created(user_id, created_at)` 로 사용자 범위가 좁혀지고, 사용자당 행이 수십~수백이라 순차 스캔으로 충분하다. 이 판단의 근거는 규모이므로, **행이 수만 단위가 되면 부분 인덱스를 검토한다.**

그다음 `skinPlateRepository` 에서 그 `food_analysis_id` 를 가진 Plate 를 찾는다. `skin_plate.food_analysis_id` 는 **UNIQUE** 라 하나뿐이다.

### 6.2 멱등성의 범위 — 정직하게

| 상황 | 보장 |
|---|---|
| 버튼 연타 | ✅ UI disabled + 락 |
| 타임아웃 후 같은 토큰 재시도 | ✅ jti 조회 |
| 같은 분석의 동시 요청 | ✅ `findForUpdate` 직렬화 |
| **DB 레벨 절대 보장** | ❌ **UNIQUE 제약이 없다 — 마이그레이션 금지 때문** |

락이 걸리지 않는 유일한 경로는 **서로 다른 인스턴스가 같은 행 락을 우회하는 경우인데, 단일 인스턴스 배포(가비아 VM)라 발생하지 않는다.** 다중 인스턴스로 가면 락은 DB 레벨이라 여전히 유효하다.

## 7. Flutter

### 7.1 결과 화면 상태

| 상태 | 화면 |
|---|---|
| `ANALYZING` | 로딩 |
| `READY` | 분석 결과 + **[기록에 저장하기]** |
| `SAVING` | 버튼 disabled + 로딩 |
| `SAVED` | "오늘의 기록에 저장됐어요" · **버튼을 다시 누를 수 없다** |
| `SAVE_FAILED` | 오류 안내 + **같은 토큰으로 재시도** |

**타임아웃이나 오류에서 새 분석을 하지 않는다.** `analysisToken` 이 상태에 남아 있으므로 재시도는 저장 요청만 다시 보내는 것이다. 서버가 멱등하므로 몇 번을 보내도 기록은 하나다.

### 7.2 이탈 시

Android back · 화면 pop · 카메라 재진입 · 백그라운드 · 앱 종료 — **전부 서버 호출 없이 임시 결과가 사라진다.** `PlateState` 가 화면 상태이므로 별도 정리 코드가 필요 없다.

`SAVED` 이후 뒤로 가면 기록은 남는다(이미 저장됐다).

### 7.3 로컬 이미지

```
분석          imageBytes 메모리에만
[기록에 저장]  → Plate 저장 성공 → imageBytes 를 앱 전용 영구 저장소에 기록
```

**저장에 성공한 뒤에만 쓴다.** 분석만 하고 나간 사진은 디스크에 남지 않는다.

- 위치: `path_provider` 의 **application documents** (캐시 디렉터리 아님 — OS 가 임의로 비운다)
- 파일명: `plates/{plateId}.jpg`
- 수명: 앱 재실행에 유지 · 앱 삭제 시 삭제 · 기기 이동 없음

`path_provider` 는 현재 `pubspec.yaml` 에 **없다.** 최소 의존성으로 추가한다. `image ^4.2.0` 은 이미 있다.

### 7.4 이미지 저장 실패 — 기록을 유지한다

DB 저장과 파일 쓰기는 한 트랜잭션으로 묶을 수 없다. Plate 저장이 성공한 뒤 파일 쓰기가 실패하면:

**기록은 유지하고, 이미지 없이 음식 아이콘으로 표시한다.**

"저장 실패" 라고 안내하면서 서버에는 기록이 남는 모순을 피한다. **되돌릴 방법이 없다** — 이번 범위에 삭제 기능이 없다. 재시도 상태 관리도 만들지 않는다: 실패 원인이 대개 디스크 부족이라 재시도해도 같고, **기록이 본체이고 사진은 부가 데이터다.** 히스토리는 음식명·점수·시각으로 이미 완결돼 있다.

## 8. 기존 데이터

분리 이전에 저장된 Plate 는 **정상 기록으로 둔다.** 손대지 않는다. `_meta` 가 없어 멱등성 조회에 안 걸리지만, 그 기록들은 재시도 대상이 아니므로 문제가 없다. 히스토리·리포트는 옛 행과 새 행을 구분 없이 표시한다.

## 9. 구현 순서 — `POST /plates` 제거는 마지막

```
1  analyze API          2  records API        3  Flutter 전환
4  호출 0 확인 (grep)    5  E2E               6  POST /plates 제거
7  전체 테스트
```

**4번을 건너뛰지 않는다.** 앱이 아직 옛 경로를 부르는 채로 지우면 시연 당일에 발견한다.

## 10. 변경 범위

**Backend (신규)**

```
global/security/AnalysisTokenProvider.java        파생 키 · 발급 · 검증
domain/plate/dto/PlateAnalysisResponse.java       분석 결과 + analysisToken
domain/plate/dto/PlateRecordRequest.java          { analysisToken }  @NotBlank
```

**Backend (수정)**

```
domain/plate/controller/SkinPlateController.java  analyze · records 추가 → 나중에 create 제거
domain/plate/service/SkinPlateService.java        analyze() / record() 분리
domain/food/service/FoodAnalysisService.java      toEntity 에 jti 를 받아 _meta 로 기록
domain/food/repository/FoodAnalysisRepository.java  jti 네이티브 조회 1개
global/exception/ErrorCode.java                   ANALYSIS_EXPIRED 추가
domain/food/entity/FoodAnalysis.java              rawAiResponse javadoc 의미 수정
```

**Flutter**

```
plate_dtos · plate_remote_datasource · plate_repository(+impl) · plate_notifier
plate_result_page          저장 CTA · 5개 상태
로컬 이미지 저장 유틸        path_provider 추가
```

**변경하지 않는 것** — OpenAI 분석 · `PlateRuleEngine` · 점수 계산 · 피부 분석 · `skin_plate_feedback` · 히스토리/리포트 집계 · DB 스키마 · `JwtTokenProvider` · `JwtAuthenticationFilter`

## 11. 테스트

**Backend**

| | |
|---|---|
| 토큰 | 정상 발급 · 서명 불일치 거부 · **인증 키로 서명한 토큰 거부** · 만료 거부 · `iss`/`aud` 불일치 거부 · `sub`≠인증 userId 거부 |
| **역방향** | **analysisToken 을 `Bearer` 로 보내면 401** — §4.1 의 위험을 고정한다 |
| 소유 | 남의 `skinAnalysisId` → 404 |
| 저장 | 정상 저장 · `_meta.jti` 기록 · 기존 필드 구조 보존 |
| 멱등 | 같은 jti 재요청 → **같은 plateId, 행 증가 0** |
| 동시성 | 같은 분석 동시 저장 → 1건 · 다른 분석 동시 저장 → 2건 |
| 호환 | `_meta` 없는 기존 행이 조회를 깨뜨리지 않는다 |
| 재사용 | RuleEngine 재평가로 점수가 나온다 · **AI 재호출 0**(Mock 호출 횟수 검증) |

**Flutter** — `READY` 상태 · 저장 중 disabled · `SAVED` 재저장 불가 · 실패 후 같은 토큰 재시도 · 저장 안 하고 나가면 record 호출 0 · 만료 안내 · 로컬 이미지 성공/실패(기록 유지 + fallback)

**반드시 넣는다: 422 만료 응답이 로그아웃을 일으키지 않는다** — 토큰 저장소가 유지되는지 검증한다. 이 한 줄이 §3.2 의 실수를 다시 못 들어오게 막는다.

**E2E**

```
분석 → 저장 안 함 → 나가기        → 히스토리 0건
분석 → 저장                       → 히스토리 1건
저장 타임아웃 → 같은 토큰 재시도    → 최종 1건
저장 버튼 연타                     → 최종 1건
```

## 12. 작업량 · 리스크

| | 난이도 | 작업량 |
|---|---|---|
| 토큰 발급·검증 (파생 키 포함) | 중간 | 1.5h |
| analyze / record 분리 + 멱등 | 중간 | 2h |
| 백엔드 테스트 | — | 1.5h |
| Flutter 상태·CTA·재시도 | 중간 | 3h |
| 로컬 이미지 + `path_provider` | 낮음 | 1.5h |

**합계 하루 안쪽.**

| 리스크 | 평가 |
|---|---|
| **ML Kit 실기기 게이트** | **높음 · 이 기능과 무관하게 프로젝트 최대 위험.** 미검증. 이 작업 때문에 뒤로 밀지 않는다 |
| ~~만료 응답이 로그아웃을 유발~~ | **해소** · 422 로 확정(§3.2). 401 이었다면 시연 중 세션이 날아갔다 |
| `POST /plates` 조기 제거 | 중간 · §9 순서로 방지 |
| jsonb 스캔 성능 | 낮음 · 사용자당 수십 행 |
| 로컬 이미지 실패 | 낮음 · 기록 유지 정책으로 흡수 |

## 13. 제외 범위

기록 삭제(P2 후보) · Cloudflare R2 · 서버 이미지 저장 · `image_url` 컬럼 복구 · DB migration · 서버 idempotency 테이블 · 이미지 저장 재시도 상태 관리 · 웹 확장

## 14. GO / NO-GO — **GO**

마이그레이션 0 · 임시 저장소 0 · R2 0 · AI 재호출 0 · 새 환경변수 0. 기존 jjwt · RuleEngine · `findForUpdate` · `raw_ai_response` 를 재사용하고, 인증 토큰과는 **키 자체가 다르다.**

**단, 순서는 지킨다.** 실기기 ML Kit 3방향 게이트가 여전히 프로젝트 최대 위험이고, 이 기능 때문에 그 검증을 미루지 않는다.
