# Flutter 작업 지시 — 분석/기록 분리

백엔드는 **완료·PR #24** (`feat/analysis-record-separation`). 이 문서만으로 작업할 수 있게 계약을 전부 적었다.

---

## 무엇이 바뀌나

**지금** — 음식을 촬영해 분석하면 **그 순간 서버에 기록이 생긴다.**

**바뀐 뒤** — 분석은 임시 결과다. 사용자가 **[기록에 저장하기]** 를 눌렀을 때만 기록이 생긴다.

```
촬영 → 분석 → 결과 화면 ─┬─ [기록에 저장하기] → 기록 생성 → 히스토리·리포트에 반영
                          └─ 뒤로가기          → 아무 흔적 없음
```

저장하지 않고 나가면 서버에 아무것도 남지 않는다. 앱이 지울 것도 없다.

**왜** — 촬영만 해보고 나간 음식이 히스토리와 주간 리포트에 섞이면 사용자가 자기 기록을 믿지 않는다.

---

## API 계약

### ① 분석 — `POST /api/v1/plates/analyze`

`multipart/form-data`

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `image` | file | ✅ | **파라미터 이름이 기존 `POST /plates` 와 같다** |
| `skinAnalysisId` | int | ❌ | 생략하면 서버가 최신 피부 분석을 쓴다 |

**200 OK** — 201 이 아니다. 아무것도 저장되지 않았다.

```jsonc
{ "success": true, "data": {
    "analysisToken": "eyJhbGciOiJIUzI1NiJ9...",   // ← 새 필드
    "skinAnalysisId": 101,
    "plateScore": 60,
    "baseScore": 70,
    "summary": "...",
    "food": { "foodName": "...", "foodCategory": "...", "cookingMethod": "...",
              "spicy": true, "ingredients": [...], "nutrition": {...} },
    "feedbacks": { "good": [...], "caution": [...], "action": [...] },
    "appliedRules": ["R04", "R02"]
  }, "error": null }
```

**기존 응답과 다른 점 두 개** — `analysisToken` 이 생겼고, **`plateId` 와 `createdAt` 이 없다.** 아직 기록이 아니기 때문이다. `food.foodAnalysisId` 도 없다(저장 전이라 서버가 키 자체를 뺀다).

### ② 저장 — `POST /api/v1/plates/records`

`application/json`

```jsonc
{ "analysisToken": "..." }
```

**본문에 이 필드 하나뿐이다.** `food`·`nutrition`·`plateScore` 등을 다시 조립해서 보내지 마라 — 서버가 받지 않는다. 점수는 서버가 토큰 안의 AI 원본으로 다시 계산한다.

**201 Created** — 기존 `POST /plates` 응답과 **완전히 같은 형태**다(`plateId`·`createdAt` 포함). 기존 `SkinPlateDto` 를 그대로 쓸 수 있다.

**AI 는 다시 호출되지 않는다.** 저장은 빠르다(1초 이내). 25초짜리 타임아웃을 걸 필요가 없다.

### 에러

| 코드 | HTTP | 사용자에게 | 앱 동작 |
|---|---|---|---|
| `ANALYSIS_EXPIRED` | 422 | 분석 결과가 만료됐어요. 다시 촬영해 주세요. | **재촬영 유도** |
| `FOOD_NOT_DETECTED` | 422 | 음식을 인식하지 못했습니다. 다시 촬영해 주세요. | 재촬영 (기존) |
| `SKIN_ANALYSIS_NOT_FOUND` | 404 | 분석 결과를 찾을 수 없습니다. | 피부 분석 먼저 안내 |
| `INVALID_INPUT` | 400 | 요청 값이 올바르지 않습니다. | 재촬영 |

> **⚠️ 만료를 401 로 바꾸지 마라.** `unauthorized_interceptor.dart:25` 가 `/auth/` 가 아닌 모든 401 에서 `_tokenStorage.clear()` 를 부른다. 401 이면 **분석이 만료됐을 뿐인데 로그인 세션이 날아간다.** 그래서 서버가 422 로 내린다.

---

## 작업 1 — 결과 화면 상태와 저장 CTA

### 상태 5종

| 상태 | 화면 |
|---|---|
| `ANALYZING` | 로딩 |
| `READY` | 분석 결과 + **[기록에 저장하기]** 버튼 |
| `SAVING` | 버튼 **disabled** + 로딩 |
| `SAVED` | "오늘의 기록에 저장됐어요" · **버튼 다시 못 누름** |
| `SAVE_FAILED` | 오류 안내 + **[다시 시도]** |

### 재시도 규칙

**타임아웃이나 오류가 나도 새로 분석하지 마라.** `analysisToken` 을 상태에 들고 있다가 **같은 토큰으로 저장 요청만 다시 보낸다.**

서버가 멱등하다 — **같은 토큰을 몇 번 보내도 기록은 하나이고, 매번 같은 `plateId` 를 201 로 돌려준다.** 중복 걱정 없이 재시도해도 된다.

판정 기준은 토큰 안의 `jti` 다. 앱이 "오늘 같은 음식이 있나" 같은 방식으로 저장 성공 여부를 추측하면 안 된다 — 같은 음식을 두 번 먹는 것은 정상이다.

### 이탈

Android back · 화면 pop · 카메라 재진입 · 백그라운드 · 앱 종료 — **전부 서버 호출 없이 끝난다.** 저장을 안 눌렀으면 서버에 아무것도 없다.

> **주의: 상태가 자동으로 정리되지 않는다.** `plateNotifierProvider` 는 keep-alive 고 `reset()` 은 **호출하는 곳이 없다.** 임시 결과는 **다음 `create()`(→ `analyze()`) 가 덮어쓸 때** 사라진다. "화면을 나가면 정리된다"고 가정하고 짜면 틀린다. `SAVED` 가드를 상태로 확실히 걸어라.

### 에러 매핑 두 줄

`ANALYSIS_EXPIRED` 는 현재 `ServerFailure` 로 떨어진다. 메시지는 노출되지만 **재촬영 경로를 안 탄다.**

1. `lib/core/network/dio_client.dart:54` 부근 — 코드 스위치의 `AnalysisFailure` 분기에 `'ANALYSIS_EXPIRED'` 추가
2. `lib/core/error/failure.dart:30` — `shouldRetakePhoto` 목록에 `'ANALYSIS_EXPIRED'` 추가

### 데이터 계층

기존 `create(XFile image, {int? skinAnalysisId})` 를 둘로 나눈다.

```
analyze(XFile image, {int? skinAnalysisId})  → PlateAnalysisDto (analysisToken 포함)
saveRecord(String analysisToken)             → SkinPlateDto (기존 그대로)
```

`plate_dtos` · `plate_remote_datasource` · `plate_repository`(+impl) · `plate_notifier` · `plate_result_page`.

**기존 `POST /plates` 는 아직 서버에 살아 있다.** 앱이 완전히 전환되고 호출이 0인 걸 확인한 뒤 백엔드에서 지운다 — 전환이 끝나면 알려 달라.

---

## 작업 2 — 로컬 음식 사진

서버·CDN 에 사진을 올리지 않는다. **R2 · S3 · image_url 컬럼 · 서버 이미지 저장 전부 이번 범위 밖이다.**

```
분석          imageBytes 는 메모리에만 (지금과 같음)
[기록에 저장]  → 저장 성공(201) → 그때 비로소 디스크에 쓴다
```

**분석만 하고 나간 사진은 디스크에 남기지 않는다.**

### 저장 절차

```
1. getApplicationDocumentsDirectory()          ← 캐시 디렉터리 아님(OS 가 임의로 비운다)
2. <documents>/plates 없으면 create(recursive: true)
3. <documents>/plates/{plateId}.jpg 에 기록
4. 히스토리: 파일이 있으면 Image.file
5. 파일이 없거나 읽기 실패면 음식 아이콘 fallback
```

### JPEG 로 변환한 뒤 저장한다

**사진 출처가 둘이고, JPEG 가 보장되지 않는다.**

| 경로 | 포맷 |
|---|---|
| `controller.takePicture()` (`food_capture_page.dart:225`) | JPEG |
| `PhotoPicker.fromGallery()` (`photo_picker.dart:25`) | **원본 포맷** |

`image_picker` 의 `imageQuality: 80` 도 JPEG 를 보장하지 않는다 — `image_picker_android` 의 `ImageResizer.java:181` 이 **알파 채널이 있으면 PNG 로 압축**하고, 디코드 못 하는 파일은 원본을 그대로 돌려준다.

`.jpg` 라는 이름이 내용과 어긋나지 않도록 **`image` 패키지로 디코드 → `encodeJpg`** 한다. `image: ^4.2.0` 이 **이미 있다**(얼굴 크롭용).

### 새 의존성

`path_provider` **하나만** 추가한다. 다른 것은 추가하지 마라 — 상태 관리 라이브러리도 새로 넣지 않는다.

### 이미지 저장이 실패하면 — **기록은 유지한다**

DB 저장과 파일 쓰기는 한 트랜잭션으로 묶을 수 없다. 서버 저장(201)이 끝난 뒤 파일 쓰기가 실패하면:

**기록은 그대로 두고, 이미지 없이 음식 아이콘으로 표시한다.**

"저장 실패"라고 안내하면서 서버에는 기록이 남는 모순을 피한다. **되돌릴 방법이 없다** — 기록 삭제 기능이 이번 범위에 없다. 재시도 상태 관리도 만들지 마라: 실패 원인이 대개 디스크 부족이라 재시도해도 같고, **기록이 본체이고 사진은 부가 데이터다.** 히스토리는 음식명·점수·시각으로 이미 완결돼 있다.

### 수명

앱 재실행에 유지 · **앱 삭제 시 함께 삭제** · 기기 변경 시 이동하지 않음.

---

## 만료 (30분)

토큰 TTL 은 30분이다. 지나서 저장하면 422 `ANALYSIS_EXPIRED` 가 온다.

**자동으로 재분석하지 마라.** "분석 결과가 만료됐어요. 다시 촬영해 주세요." 를 띄우고 사용자가 결정하게 한다 — 재분석은 AI 비용이 다시 나가고, 30분 방치된 결과를 사용자 모르게 되살리는 것도 옳지 않다.

촬영→분석→저장은 보통 30초 안이라 정상 흐름에서는 걸리지 않는다.

---

## 하지 말 것

- 요청 본문에 점수·영양값·음식 정보를 담아 보내기 (서버가 받지 않는다)
- 타임아웃 시 새로 분석하기 (같은 토큰으로 저장만 재시도)
- 만료를 401 로 처리하기 (로그아웃된다)
- 음식명·점수·시각으로 중복 저장 여부 추측하기
- 룰 라벨·점수 계산을 앱에 하드코딩하기 (서버 응답을 그대로 쓴다)
- R2 · S3 · 서버 이미지 업로드
- 기록 삭제·상세 편집 등 부가 기능 (P2)
- 웹 대응 확장 (우선순위: Android → iOS → Web)

---

## 테스트

- `READY` 상태에서 결과가 보인다
- 저장 중 버튼 disabled
- `SAVED` 이후 재저장 불가
- 저장 실패 후 **같은 토큰으로** 재시도 가능
- 저장하지 않고 나가면 **record API 호출 0**
- **422 `ANALYSIS_EXPIRED` 가 ① 로그아웃을 일으키지 않고(토큰 저장소 유지) ② `shouldRetakePhoto` 를 true 로 만든다** ← 이 두 줄이 위 두 함정을 막는다
- 로컬 이미지 저장 성공
- 로컬 이미지 저장 실패 → **기록 유지 + 아이콘 fallback**

---

## 백엔드 붙는 법

PR #24 머지 후 `develop`. 로컬은 `docker compose up -d postgres` → `./gradlew bootRun`.

실기기로 개발 PC 에 붙으려면 cleartext 설정이 필요하다. `chore/debug-cleartext` 브랜치에 **디버그 전용 설정이 이미 작성돼 있다(미커밋)** — `android/app/src/debug/res/xml/network_security_config.xml`. 빌드 타입 리소스 우선순위로 `main` 을 덮어쓰고 **릴리즈에는 적용되지 않는다.**

---

## 참고

전체 설계 근거: `docs/superpowers/specs/2026-08-14-analysis-record-separation-design.md` (백엔드 저장소)

API 명세: `SkinPlate_PRD.md` §14.3 ⑦ · ⑦-c
