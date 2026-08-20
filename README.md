# Skin Plate — 오늘의 피부를 위한 오늘의 한 끼

AI가 피부를 분석하는 것에서 멈추지 않고, **눈앞의 식사를 바꾸는 한 문장**까지 도달하는 서비스의 백엔드입니다.

```
피부 분석  →  음식 분석  →  행동 제안
```

기존 피부 분석 서비스는 "당신의 피부는 이렇습니다"에서 끝납니다. Skin Plate는 **"그래서 오늘 이 음식을 이렇게 드세요"** 까지 갑니다.

> "국물을 절반만 남기면 Skin Plate 점수가 상승합니다."

- 🌐 **API** — https://1-201-116-157.sslip.io/api/v1/health
- 📖 **Swagger** — https://1-201-116-157.sslip.io/swagger-ui/index.html
- 📱 Flutter 앱은 별도 저장소입니다.

---

## 무엇을 하는가

| 기능 | 설명 |
|---|---|
| **AI 피부 분석** | 정면·좌·우 3장을 **한 번의 Vision 호출**로 분석해 수분·유분·홍조·트러블·장벽 지표와 Skin Score(0~100)를 낸다 |
| **Skin Plate** | 음식 사진 1장 → 음식·재료·영양소 인식 → **현재 내 피부 상태 기준**으로 점수화 |
| **What-if 시뮬레이션** | "국물을 남기면", "튀김옷을 벗기면" — 재료를 빼고 다시 계산해 점수 변화를 보여준다 |
| **추천** | 지금 피부 상태에 맞는 대체 메뉴 제안 |
| **일간 / 주간 리포트** | 먹은 것과 피부 지표를 겹쳐 보여주는 집계 |
| **피부 인사이트** | 누적 데이터에서 뽑은 개인화 코멘트 |

### 핵심 차별점

**Skin Plate Score는 "이 음식이 건강한가"가 아니라 "이 음식이 지금 당신의 피부에 맞는가"를 측정합니다.**
같은 라면이라도 홍조 지수가 높은 사용자에게는 더 낮은 점수가 나옵니다.

| | 기존 서비스 | Skin Plate |
|---|---|---|
| 분석 대상 | 피부만 | 피부 + 음식 |
| 결과 | 수치와 진단 | 수치 + **오늘의 행동** |
| 개인화 기준 | 피부 타입 (고정) | **오늘의 피부 상태 (가변)** |
| 재방문 | 월 1회 | 식사마다 |

---

## 이 프로젝트의 설계 결정

### AI는 인식, 점수는 Backend

| 주체 | 책임 |
|---|---|
| OpenAI | 피부 특징 추출, 음식 인식, 자연어 문장 생성 |
| **Backend** | **Skin Plate Score 계산, 피부↔음식 매칭, Rule Engine** |

점수 계산을 LLM에 맡기면 같은 사진에서 매번 다른 점수가 나옵니다. **재현성이 이 제품의 주장**이라 점수는 전부 규칙 기반으로 계산합니다. 심사위원이 같은 사진을 두 번 찍어도 같은 점수가 나와야 합니다.

### 영양소는 AI 추정값을 쓰지 않는다

룰 엔진은 `sodiumMg`를 1500과 비교하는데, 그 값이 AI가 사진을 보고 추정한 숫자면 같은 사진에서도 1400~2100 사이로 흔들립니다. 경계를 넘나드는 순간 점수가 8점씩 뜁니다.

그래서 **공공데이터 기반 표준 음식 테이블**(`standard-food.json`, 약 400KB)을 두고 AI가 인식한 음식명을 2단계(정확한 이름 → 기본명)로 매칭해 영양값을 확정합니다.

### Rule Engine

`PlateRule` 인터페이스 구현체 14개가 각각 하나의 규칙을 담당합니다. 룰 추가는 `@Component` 하나를 더하는 것으로 끝나고, 엔진도 기존 룰도 건드리지 않습니다.

```
SodiumRule · SugarTroubleRule · FriedOilRule · SaturatedFatRule · RefinedCarbRule
SpicyRednessRule · HighCalorieRule · ProteinRule · FiberRule · VitaminRule
Omega3FoodRule · Omega3BarrierRule · ProbioticRule · HydrationFoodRule
```

델타·계수는 `RuleConstants`, 영양 임계값은 `Nutrition` — 튜닝 지점이 두 파일로 모여 있습니다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Framework | Spring Boot 3.3.5 |
| Language | Java 21 (record · sealed · pattern matching) |
| DB | PostgreSQL (로컬 Docker 16 · 배포 Supabase 17) |
| 마이그레이션 | Flyway 10.22 (`ddl-auto: validate`) |
| ORM | Spring Data JPA (`open-in-view: false`) |
| 인증 | Spring Security 6.3 + jjwt 0.12 (BCrypt · JWT) |
| AI | OpenAI `gpt-5.6-luna` — Vision + Structured Outputs |
| HTTP 클라이언트 | WebClient (WebFlux) |
| 문서화 | springdoc-openapi 2.6 (Swagger UI) |
| 배포 | Docker + 가비아 VM (2 vCore · 4GB) · Caddy 리버스 프록시 |
| 테스트 | JUnit 5 + Spring Boot Test (42개 테스트 클래스) |

**클라이언트** — Flutter 3.24 / Riverpod / go_router / Dio + Retrofit / freezed (별도 저장소)

### 아키텍처

```
Flutter ──HTTPS multipart──▶ Caddy (TLS · 본문 20MB 상한)
                               │
                               ▼
                      Spring Boot 3
        Security Filter (JWT → userId 주입)
                      Controller
                       Service ──── WebClient(Base64) ──▶ OpenAI Vision
                          │
                     Rule Engine (Skin Plate Score)
                          │
                     Repository ──▶ PostgreSQL (Supabase)
```

**이미지 저장소가 없는 것이 맞습니다.** 서버는 이미지를 받아 Base64로 OpenAI에 보내고 버립니다. 결과 화면은 앱이 방금 찍은 로컬 파일을 씁니다.

---

## API

응답은 `ApiResponse<T>` 로 감싸고, 인증은 `Authorization: Bearer {token}` 입니다.

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/v1/auth/signup` | 회원가입 |
| `POST` | `/api/v1/auth/login` | 로그인 |
| `POST` | `/api/v1/auth/test-login` | 데모용 슬롯 로그인 (본문 생략 가능) |
| `GET` `PATCH` | `/api/v1/auth/me` | 내 프로필 조회 · 수정 |
| `POST` | `/api/v1/skin/analyses` | **피부 분석** — 정면·좌·우 3장 multipart |
| `GET` | `/api/v1/skin/analyses/latest` | 최신 피부 분석 |
| `GET` | `/api/v1/skin/analyses/{id}` | 피부 분석 상세 |
| `POST` | `/api/v1/plates/analyze` | **음식 분석** — 사진 1장 multipart (저장 없음) |
| `POST` | `/api/v1/plates/simulate` | 분석 토큰으로 What-if 시뮬레이션 |
| `POST` | `/api/v1/plates/records` | 분석 결과 저장 |
| `GET` | `/api/v1/plates` | 기록 목록 |
| `GET` `DELETE` | `/api/v1/plates/{id}` | 기록 상세 · 삭제 |
| `POST` | `/api/v1/plates/{id}/simulate` | 저장된 기록으로 시뮬레이션 |
| `GET` | `/api/v1/recommendations` | 피부 상태 기반 메뉴 추천 |
| `GET` | `/api/v1/reports/daily` | 일간 리포트 |
| `GET` | `/api/v1/reports/weekly` | 주간 리포트 |
| `GET` | `/api/v1/skin-insights` | 개인화 인사이트 |
| `GET` | `/api/v1/health` | 헬스체크 |

전체 스키마는 Swagger UI에서 확인할 수 있습니다.

---

## 로컬 실행

```bash
# 1. 환경 변수
cp .env.example .env        # OPENAI_API_KEY · JWT_SECRET 채우기

# 2. Postgres
docker compose up -d postgres

# 3. 실행
set -a && source .env && set +a
./gradlew bootRun
```

`테스트 계정 생성: test@skinplate.app` 로그가 뜨면 정상입니다.
OpenAI 키 없이 돌리려면 `.env` 에 `AI_MOCK=true` 를 넣으면 고정 응답으로 동작합니다.

```bash
./gradlew test                                  # 전체
./gradlew test --tests '*PlateRuleEngineTest'   # 룰 엔진 (예시 65점 / 97점 재현)
```

필요한 환경 변수는 `OPENAI_API_KEY` `JWT_SECRET` `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` 이며, `.env.example` 에 전부 정리돼 있습니다. **시크릿은 저장소에도 이미지에도 들어가지 않습니다.**

---

## 프로젝트 구조

```
src/main/java/com/skinplate/api/
├── domain/
│   ├── auth/            회원가입 · 로그인 · 프로필
│   ├── user/            AppUser · SkinType
│   ├── skin/            피부 분석 (지표 · Skin Score · highlights)
│   ├── food/            음식 인식 · 표준 음식 테이블
│   ├── plate/
│   │   ├── engine/      Rule Engine + rules/ (PlateRule 14개)
│   │   └── service/     Skin Plate Score · 시뮬레이션
│   ├── recommendation/  메뉴 추천
│   ├── insight/         개인화 인사이트
│   └── report/          일간 · 주간 리포트
├── infra/openai/        WebClient · 프롬프트 · Mock 클라이언트
└── global/              security · config · exception · common
```

각 도메인은 `controller / service / repository / entity / dto` 로 나뉩니다.
DB 스키마는 Flyway(`src/main/resources/db/migration/V1~V10`)가 소유합니다.

---

## 개발 규칙 (요약)

- 모든 DTO는 `record`, 변환은 `Response.from(entity)` / `Request.toCommand()`
- Entity는 `@NoArgsConstructor(PROTECTED)` + 정적 팩토리, setter 금지, 연관관계 전부 `LAZY`
- 조회는 예외 없이 `findByIdAndUserId` — 타인 리소스는 403이 아니라 **404**
- 사용자 식별은 `@CurrentUser Long userId` 하나뿐, 요청 본문의 `userId`는 무시
- OpenAI 호출은 **트랜잭션 밖**, 저장만 `@Transactional`
- 예외는 `BusinessException(ErrorCode)` 하나로
- 브랜치 `{type}/{설명}` · 커밋 `{type}({scope}): 작업 내용` (Conventional Commits + 한국어)

자세한 내용은 [`CLAUDE.md`](CLAUDE.md), 전체 명세는 [`SkinPlate_PRD.md`](SkinPlate_PRD.md) · [`SkinPlate_DTO_Domain.md`](SkinPlate_DTO_Domain.md) 를 참고하세요.

---

**개발 기간** 2026-08-08 ~ 08-21 · **발표** 08-25
