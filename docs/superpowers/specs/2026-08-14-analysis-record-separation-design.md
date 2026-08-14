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
→ 200
```

> **200 인 이유는 코드베이스의 선례다.** 같은 컨트롤러의 `POST /{plateId}/simulate` 가 이미 200 이고 주석까지 달려 있다 — *"저장하지 않으므로 200 이다."* 반대로 `POST /plates`(`SkinPlateController:42`) 와 `POST /skin/analyses`(`SkinAnalysisController:38`) 는 실제로 행을 만들기 때문에 `ResponseEntity.status(CREATED)` 를 쓴다.
>
> **POST 는 규칙이 일관된다** — 행을 만들면 `ResponseEntity.status(CREATED)`, 아니면 `ApiResponse<T>` 를 그대로 반환(200). `analyze` 는 아무것도 만들지 않으므로 후자다.

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
> // unauthorized_interceptor.dart:25-27
> final isAuthRequest = err.requestOptions.path.contains('/auth/');
> if (err.response?.statusCode == 401 && !isAuthRequest) {
>   await _tokenStorage.clear();   // ← 토큰을 지운다
>   _onUnauthorized();             // ← 로그인 화면으로 보낸다
> }
> ```
>
> `/plates/records` 는 `/auth/` 가 아니므로 **분석이 만료됐을 뿐인데 로그인 세션이 날아간다.** 그래서 401 을 쓰지 않는다.
>
> **422 를 쓴다.** 요청 자체는 정상 인증됐고("이해했지만 처리할 수 없다"), 기존 `FACE_NOT_DETECTED`·`FOOD_NOT_DETECTED` 가 같은 422 에 같은 "다시 촬영해 주세요" 계열 메시지를 쓴다.
>
> ```java
> ANALYSIS_EXPIRED (HttpStatus.UNPROCESSABLE_ENTITY, "분석 결과가 만료됐어요. 다시 촬영해 주세요."),
> ```
>
> `TOKEN_EXPIRED` 를 재사용하지 않는 이유도 같다 — 그 메시지는 "다시 로그인해 주세요" 다.
>
> **단, 앱이 자동으로 재촬영 경로를 타지는 않는다.** `dio_client.dart:54` 의 코드 스위치가 `FACE_NOT_DETECTED`/`FOOD_NOT_DETECTED`/`AI_ANALYSIS_FAILED`/`AI_TIMEOUT` 만 `AnalysisFailure` 로 보내고, 나머지는 `ServerFailure` 로 떨어진다. 메시지 자체는 그대로 노출되지만 `shouldRetakePhoto`(`failure.dart:30`) 가 false 다.
>
> → **`ANALYSIS_EXPIRED` 를 두 목록에 추가한다**(§10). 각 한 줄. 빠뜨리면 만료 안내는 뜨는데 재촬영 버튼이 안 뜬다.

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

### 4.4 저장 시 점수는 서버가 다시 계산한다

**분석 응답의 점수를 그대로 저장하지 않는다.** 저장에 쓰이는 입력은 두 개뿐이다.

```
analysisToken.food          ← 서명으로 무결성이 보장된 AI 원본
        +
서버 DB 의 SkinMetrics       ← token.skinAnalysisId 로 조회
        ↓
   PlateRuleEngine           ← 기존 엔진 그대로
        ↓
plateScore · baseScore · feedbacks · appliedRules   ← 저장되는 값
```

**요청 본문은 `analysisToken` 하나다.** `food`·`nutrition`·`plateScore`·`baseScore`·`feedbacks`·`appliedRules` 를 받는 필드가 존재하지 않는다 — 받지 않으므로 조작할 대상이 없다.

**토큰의 `skinAnalysisId` 로 조회한다 — 최신 분석으로 갈아타지 않는다.** 분석 시점에 쓰인 그 피부 분석이 기준이다.

피부 지표를 토큰에 싣지 않고 DB 에서 읽는 이유는 두 가지다. 토큰이 그만큼 커지지 않고, **§4.3 의 존재·소유 재확인이 같은 조회로 끝난다.** `SkinAnalysis` 는 수정 경로가 없으므로 지표 자체는 분석 때와 같다.

**OpenAI 는 이 단계에서 호출하지 않는다.** 인식 결과는 이미 토큰 안에 있다.

### 4.5 페이로드 크기 — 실측 근거

```
food_analysis.raw_ai_response   min 368 / avg 419 / max 431 bytes   (37/37 채워짐, 2026-08-14 실측)
```

분석 원본 431B + `skinAnalysisId` + 표준 클레임 → JSON 약 500B → base64url 약 670자 → 헤더·서명 포함 **토큰 800~1000자**. 요청 **본문**에 싣는다. 헤더에 넣지 않는다 — 일부 프록시의 헤더 상한(8KB)에는 여유가 있지만, 본문이 의미상 맞고 로그에 남을 위험도 낮다.

재료가 많은 음식은 이보다 커진다. **상한은 프록시 본문 20MB 이므로 몇 배가 되어도 무관하다.**

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

중첩(`{"ai": {...}, "_meta": {...}}`) 을 쓰지 않는 이유 — **기존 37행과 형태가 갈린다.** 지금은 읽는 코드가 없어 안 깨지지만, 나중에 읽는 쪽이 생기면 두 형태를 다 처리해야 한다. 형제 키는 옛 행의 구조를 그대로 두고, `raw_ai_response->'_meta'->>'jti'` 가 옛 행에서 `null` 을 준다 — **그게 정확한 답이다. 그 행들엔 jti 가 없었다.**

키 충돌은 불가능하다. `FoodAnalysisPrompt.SCHEMA` 가 `additionalProperties: false` 라 AI 가 `_meta` 를 만들 수 없다.

**컬럼의 의미를 문서에 명시한다** — **"AI 분석 원본 + 서버 메타데이터"**. `FoodAnalysis.rawAiResponse` 에는 지금 javadoc 이 없으므로 **새로 단다**(같은 문구가 `SkinAnalysis:35` 에는 이미 있다). 설계서도 같이 고친다. 컬럼명은 바꾸지 않는다.

## 6. 멱등성

**같은 `analysisToken` 을 몇 번 보내도 기록은 하나다.** `foodName`·점수·시각 같은 추측값을 쓰지 않는다 — 같은 음식을 두 번 먹는 것은 정상이다.

**락 · jti 조회 · 삽입이 반드시 한 트랜잭션이어야 한다.**

```
── transactionTemplate.execute / @Transactional 하나 안에서 ──────────
1. skinAnalysisRepository.findForUpdate(skinAnalysisId)     ← PESSIMISTIC_WRITE
2. jti 로 기존 food_analysis 조회
3. 있으면  → 그 Plate 를 그대로 반환 (새로 만들지 않는다)
4. 없으면  → 정상 저장
──────────────────────────────────────────────────────────────────
```

**1번이 동시성을 끊는다.** 추천 생성(`RecommendationService:81`)이 같은 문제를 같은 방식으로 이미 푼다 — 락 → 재확인 → 삽입이 한 `@Transactional` 안에 있다. 새 코드 0.

```
요청 A  락 획득 → jti 없음 → 저장 → commit(락 해제)
요청 B  락 대기 → 획득 → jti 발견 → 기존 Plate 반환
```

**Postgres 는 `FOR UPDATE` 락을 커밋까지 잡고 있고**, READ COMMITTED 에서 B 의 조회는 락 획득 후 새 스냅샷을 뜨므로 **A 가 커밋한 행을 본다.** 이 두 성질이 성립의 근거다.

> **⚠️ 구현 함정.** 현재 `SkinPlateService.create()` 는 피부 분석 조회를 `transactionTemplate.execute` **바깥**에서 한다(AI 호출을 트랜잭션 밖에 두려고). 그 모양을 그대로 따라 `findForUpdate` 를 트랜잭션 앞에 두면 **직렬화가 깨진다.**
>
> 다행히 조용히 깨지지 않는다 — 트랜잭션 없이 `PESSIMISTIC_WRITE` 를 걸면 `TransactionRequiredException` 이 난다. 그래도 **소유권 확인용 조회와 락용 조회를 분리**해서 쓴다: 소유권은 트랜잭션 앞(값싼 검증), 락은 트랜잭션 안.

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

그다음 그 `food_analysis_id` 를 가진 Plate 를 찾는다. `SkinPlateRepository` 에 **`findByFoodAnalysisIdAndUserId` 를 추가한다** — 현재는 `findByIdAndUserId` 와 `findInRange` 뿐이다. `skin_plate.food_analysis_id` 가 **UNIQUE**(V1:76) 라 결과는 최대 하나다.

> 이 UNIQUE 는 **멱등성의 안전망이 아니다.** 저장할 때마다 `food_analysis` 행을 새로 만들므로, 토큰 재전송이 중복 삽입까지 갔다면 서로 다른 `food_analysis_id` 라 제약에 걸리지 않는다. 중복을 막는 것은 **오직 §6 의 락 + jti 조회**다.

### 6.2 멱등성의 범위 — 정직하게

| 상황 | 보장 |
|---|---|
| 버튼 연타 | ✅ UI disabled + 락 |
| 타임아웃 후 같은 토큰 재시도 | ✅ jti 조회 |
| 같은 분석의 동시 요청 | ✅ `findForUpdate` 직렬화 |
| **DB 레벨 절대 보장** | ❌ **UNIQUE 제약이 없다 — 마이그레이션 금지 때문** |
| 30분 경계를 넘긴 재시도 | ❌ 아래 참조 |

락은 DB 레벨이라 인스턴스 수와 무관하게 유효하다 — 단일 인스턴스(가비아 VM)든 나중에 늘리든 같다.

**남는 구멍 하나** — 저장은 성공했는데 응답이 유실되고, 사용자가 **30분이 지난 뒤** 재시도하면 만료(422)가 뜬다. 다시 촬영해 저장하면 **첫 기록이 남아 있는 채로 두 번째가 생긴다.** 만료 검증이 jti 조회보다 먼저 일어나기 때문이다.

의도적으로 남긴다. 발생하려면 "응답 유실 + 30분 방치 + 재촬영"이 겹쳐야 하고, 막으려면 만료된 토큰도 일단 파싱해야 해서 **만료 정책 자체가 무의미해진다.** 사용자는 히스토리에서 중복을 볼 수 있고, 삭제 기능(P2)이 들어오면 자연히 해소된다.

### 6.3 중복 식별자는 jti 하나뿐이다

> **멱등성은 동일 `analysisToken.jti` 에 대해서만 보장한다. 새로운 분석을 수행하면 새로운 jti 가 발급되므로, 사용자가 동일한 음식을 다시 촬영한 경우 정상적인 별도 기록으로 처리한다.**

따라서 다음 휴리스틱은 **구현하지 않는다.**

- `foodName` 동일 여부
- `plateScore` 동일 여부
- `createdAt` 시간 차이
- 이미지 유사도
- 동일 음식명 + 짧은 시간 간격

**같은 음식을 두 번 먹는 것은 정상이다.** 추측으로 막으면 정상 기록을 잃는다 — 잃은 기록은 사용자가 복구할 방법이 없고(삭제·수정 기능이 없다), 중복은 눈에 보이기라도 한다.

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

Android back · 화면 pop · 카메라 재진입 · 백그라운드 · 앱 종료 — **전부 서버 호출 없이 끝난다.** 저장을 누르지 않았으므로 서버에는 아무것도 없다.

**정확한 메커니즘을 알고 있어야 한다** — `plateNotifierProvider` 는 keep-alive 라 화면을 나가도 상태가 살아 있다(`reset()` 은 존재하지만 호출하는 곳이 없다). 임시 결과는 **다음 `create()` 가 덮어쓸 때** 사라진다. 기능상 문제는 없다(서버 상태가 없고 `SAVED` 가드가 있다). 다만 **"화면을 나가면 정리된다"고 가정하고 구현하면 안 된다** — 그런 처리는 일어나지 않는다.

`SAVED` 이후 뒤로 가면 기록은 남는다(이미 저장됐다).

### 7.3 로컬 이미지

```
분석          imageBytes 메모리에만
[기록에 저장]  → Plate 저장 성공 → imageBytes 를 앱 전용 영구 저장소에 기록
```

**저장에 성공한 뒤에만 쓴다.** 분석만 하고 나간 사진은 이 디렉터리에 기록하지 않는다.

**저장 절차**

```
1. getApplicationDocumentsDirectory()
2. <documents>/plates 가 없으면 create(recursive: true)
3. <documents>/plates/{plateId}.jpg 에 기록
4. 히스토리: 파일이 있으면 Image.file 로 표시
5. 파일이 없거나 읽기 실패면 음식 아이콘 fallback
```

캐시 디렉터리를 쓰지 않는다 — OS 가 임의로 비운다. 수명은 **앱 재실행에 유지 · 앱 삭제 시 삭제 · 기기 이동 없음.**

**JPEG 로 변환한 뒤 저장한다.** 코드로 확인한 결과 **JPEG 가 보장되지 않는다** — 음식 사진의 출처가 둘이다.

| 경로 | 포맷 |
|---|---|
| `controller.takePicture()` (`food_capture_page:225`) | JPEG |
| `PhotoPicker.fromGallery()` (`photo_picker.dart:25`) | **원본 포맷** — PNG·WebP 가능 |

`imageQuality: 80` 이 JPEG 를 보장하지도 않는다. `image_picker_android` 의 `ImageResizer.java:181` 이 **알파 채널이 있으면 PNG 로 압축**하고, 디코드 못 하는 파일은 아예 원본 경로를 그대로 돌려준다.

`.jpg` 라는 이름이 내용과 어긋나지 않도록 저장 직전에 `image` 패키지로 디코드 → `encodeJpg` 한다. **`image: ^4.2.0` 이 이미 있다**(얼굴 크롭용) — 새 의존성 0. 디코드가 실패하면 위 5번 fallback 으로 떨어진다.

`path_provider` 는 `pubspec.yaml` 에 **없다.** 이것만 최소 의존성으로 추가한다.

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
domain/plate/controller/SkinPlateController.java     analyze · records 추가 → 나중에 create 제거
domain/plate/service/SkinPlateService.java           analyze() / record() 분리
domain/food/service/FoodAnalysisService.java         toEntity 가 jti 를 받아 _meta 로 기록
domain/food/repository/FoodAnalysisRepository.java   jti 네이티브 조회 1개
domain/plate/repository/SkinPlateRepository.java     findByFoodAnalysisIdAndUserId 추가
global/exception/ErrorCode.java                      ANALYSIS_EXPIRED 추가
domain/food/entity/FoodAnalysis.java                 rawAiResponse 에 javadoc 을 새로 단다 (현재 없다)
```

> **`toEntity` 시그니처 전환.** §9 의 6단계 전까지는 기존 `create()` 도 이 메서드를 부른다. 그 구간에서는 **jti 를 받는 오버로드를 추가**하고 옛 경로는 그대로 둔다. `create()` 를 지울 때 오버로드도 같이 정리한다.

**Flutter**

```
plate_dtos · plate_remote_datasource · plate_repository(+impl) · plate_notifier
plate_result_page             저장 CTA · 5개 상태
core/network/dio_client.dart  ANALYSIS_EXPIRED → AnalysisFailure  (한 줄)
core/error/failure.dart       shouldRetakePhoto 에 ANALYSIS_EXPIRED (한 줄)
로컬 이미지 저장 유틸           path_provider 추가
```

**문서** — `SkinPlate_PRD.md` §14 API 명세(엔드포인트 2개 추가 · 1개 제거)와 `SkinPlate_DTO_Domain.md` 계약 대조표. CLAUDE.md 가 "코드가 문서와 어긋나면 그 자리에서 고친다" 를 요구한다.

**변경하지 않는 것** — OpenAI 분석 · `PlateRuleEngine` · 점수 계산 · 피부 분석 · `skin_plate_feedback` · 히스토리/리포트 집계 · DB 스키마 · `JwtTokenProvider` · `JwtAuthenticationFilter`

## 11. 테스트

**Backend**

| | |
|---|---|
| 토큰 | 정상 발급 · 서명 불일치 거부 · **인증 키로 서명한 토큰 거부** · 만료 거부 · `iss`/`aud` 불일치 거부 · `sub`≠인증 userId 거부 |
| **역방향** | **analysisToken 을 `Bearer` 로 보내면 401** — §4.1 의 위험을 고정한다 |
| 소유 | 남의 `skinAnalysisId` → 404 |
| 저장 | 정상 저장 · `_meta.jti` 가 직렬화 결과에 들어간다 · 기존 필드 구조 보존 |
| 멱등 | 같은 jti 재요청 → **같은 plateId 반환, `save` 미호출** |
| **재평가** | **토큰의 `food` + 서버 조회 `SkinMetrics` 로 엔진이 다시 돈다** — 토큰의 food 를 바꿔 서명하면 점수가 따라 바뀐다 |
| **조작 불가** | **요청 본문에 점수·영양값을 넣을 경로가 없다** — `PlateRecordRequest` 에 `analysisToken` 외 필드가 없음을 검증 |
| **AI 재호출 0** | record 단계에서 OpenAI Mock 호출 횟수 = 0 |

**Flutter** — `READY` 상태 · 저장 중 disabled · `SAVED` 재저장 불가 · 실패 후 같은 토큰 재시도 · 저장 안 하고 나가면 record 호출 0 · 만료 안내 · 로컬 이미지 성공/실패(기록 유지 + fallback)

**반드시 넣는다** — 422 `ANALYSIS_EXPIRED` 가 ① **로그아웃을 일으키지 않고**(토큰 저장소 유지) ② **`shouldRetakePhoto` 를 true 로 만든다**. 이 두 줄이 §3.2 의 두 함정을 다시 못 들어오게 막는다.

### 11.1 DB 의존 검증은 E2E 로 내린다 — 단위 테스트로 못 쓴다

**이 저장소에는 통합 테스트 인프라가 없다.** `build.gradle` 에 Testcontainers 도 H2 도 없고, `@SpringBootTest` 가 **0건**이며 `src/test/resources` 디렉터리 자체가 없다. 기존 110개는 전부 Mockito · standalone MockMvc · `ApplicationContextRunner` 다.

**Testcontainers 를 넣지 않는다.** 새 의존성 · 새 테스트 프로파일 · 느린 기동을 남은 일정에 얹을 이유가 없고, 이 프로젝트는 이미 **docker-compose DB 상대의 수동 E2E**(G2) 로 이 층을 검증해 왔다. 아래 셋은 그 방식으로 확인한다.

| 검증 | 방법 |
|---|---|
| 동일 분석 동시 저장 → 1건 | 같은 토큰으로 `curl` 2개를 동시에 쏘고 `select count(*)` |
| 서로 다른 분석 동시 저장 → 2건 | 위와 같게, 토큰 2개 |
| `_meta` 없는 기존 행 호환 | 네이티브 쿼리를 실 DB 에 직접 실행 — **이미 확인했다**(37행 전부 null) |

**단위 테스트로 남는 것** — 토큰 검증 7종 · 역방향 거부 · 재평가 · 조작 경로 부재 · AI 재호출 0 · 멱등 분기(`save` 미호출). 이쪽이 로직의 대부분이고 Mockito 로 전부 커버된다.

### 11.2 E2E

```
분석 → 저장 안 함 → 나가기        → 히스토리 0건
분석 → 저장                       → 히스토리 1건
저장 타임아웃 → 같은 토큰 재시도    → 최종 1건
저장 버튼 연타                     → 최종 1건
같은 토큰 동시 요청 2개             → 최종 1건   ← 11.1 의 직렬화 검증
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
