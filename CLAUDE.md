# CLAUDE.md — skinplate-api

Skin Plate 백엔드. **AI 피부 분석 → 음식 분석 → 행동 제안**.
**개발 2026-08-08 ~ 08-21 · GitHub 업로드 마감 08-21 · 발표 08-25.**
**08-22~24 는 없는 기간으로 본다** — 업로드가 21일에 닫히므로 QA·버그 수정·배포까지 21일 안에 끝난다.
Spring Boot 3.3.5 / Java 21 / PostgreSQL(로컬 16 · Supabase 17) / Flyway / Spring Security + jjwt / OpenAI gpt-5.6-luna.
Flutter 앱은 별도 저장소. 배포는 Docker + **가비아 VM**(2 vCore · 4GB · 무료 트래픽 1TB).
HTTPS 는 자동이 아니다 — 리버스 프록시가 인증서와 **본문 상한 20MB**(3장 업로드)를 같이 맡는다.

## 기준 문서

| 파일 | 범위 |
|---|---|
| `SkinPlate_PRD.md` (v1.4) | 요구사항 · API 명세 · 룰 정의표 · 일정 · 리스크 |
| `SkinPlate_DTO_Domain.md` | Entity · DTO · Rule Engine 스켈레톤 (파일 단위 코드) |

- **문서 원본은 저장소의 이 파일들이다.** 작업 전 **버전 헤더를 확인**하고, 손에 든 사본이 더 최신이면 저장소를 먼저 교체한다 — 오래된 사본으로 리뷰하면 이미 고친 것이 계속 지적된다.
- **두 문서가 어긋나면 설계서(`_DTO_Domain`)를 따른다.**
- 설계서에 이미 있는 클래스는 새로 쓰지 말고 **그대로 옮긴다.** 개선하지 마라.
- 코드가 문서와 어긋나면 그 자리에서 문서를 고친다. 어긋난 채로 두지 않는다.

## 시작 전

- 요청이 모호하면 먼저 질문한다
- 수정할 파일은 반드시 읽고 기존 패턴(폴더 구조, 네이밍, 어노테이션·import 순서)을 파악한 뒤 작업한다
- 솔루션 로직을 스스로 검토한 후 제시한다

## 코드 작성

- 요청된 작업 범위만 수정한다 (불필요한 리팩토링 금지)
- 완전히 실행 가능한 코드만 제공한다 (의사코드 금지)
- 변수명은 역할이 드러나도록 작성한다 — `dto` `r` `e` `data` `res` 같은 모호한 축약 금지
- 시크릿/환경변수 하드코딩 절대 금지 — `.env*` 또는 CI Secret 으로만 주입

## 이 프로젝트에서만 지켜야 할 것

**스키마** — Flyway가 소유한다. `ddl-auto: validate`라 Entity 필드 하나만 어긋나도 기동이 실패한다.
- 기존 마이그레이션 수정 금지. 새 `V{n}__*.sql` 추가만
- 컬럼을 바꾸면 **DDL · Entity · PRD §12 ERD 셋을 같이** 고친다

**트랜잭션** — 이 둘을 어기면 각각 커넥션 고갈과 `LazyInitializationException`이다.
- OpenAI 호출은 **트랜잭션 밖**. 저장만 `@Transactional`
- `*Response.from()` 은 **`@Transactional(readOnly = true)` 안에서만** (`open-in-view: false`)

**인가** — 조회는 예외 없이 `findByIdAndUserId`. 타인 리소스는 403이 아니라 **404**.
- 사용자 식별은 `@CurrentUser Long userId` 하나뿐. 요청 본문의 userId는 무시한다

**Rule Engine** — 룰 추가 = `PlateRule` 구현 `@Component` 1개. 엔진도 기존 룰도 건드리지 않는다.
- **델타·계수는 `RuleConstants`, 영양 임계값은 `Nutrition`** — 두 파일뿐이다
- `PlateRuleEngineTest`(예시 A=60 · B=87)가 깨지면 **문서 예시도 같이 고친다**
- 시뮬레이션은 **detached 복사본**으로만 계산한다. 관리 엔티티를 만지면 `orphanRemoval`이 재료를 DELETE 한다

**AI 경계** — AI는 인식, 점수는 Backend.
- 음식 스키마의 원본은 파일이 아니라 **`FoodAnalysisPrompt.SCHEMA_JSON`** 이다. `IngredientTag` / `CookingMethod` / `FoodGroup` / `PortionSize` / `Spiciness` / `Oiliness` / `ProcessingLevel`을 바꾸면 그 스키마도 같이 바꾼다 — 값 일치는 `FoodAnalysisPromptTest`가 깨져서 알려준다
- Mock 스위치는 **`app.ai.mock` 프로퍼티 하나**. `@Profile("mock")` 금지
- 타임아웃은 재시도 없는 단발. 음식·문장 25초 / **피부 28초**(상한 — 429 재시도가 끼면 최악 30.1초라 클라이언트 32초 안에 들어와야 한다) / **조회 문장 12초**(상한 15 — 주간 코멘트·인사이트. GET 이 동기로 기다리므로 그 시간이 화면이 멎는 시간이다). 429만 1회 재시도, `TimeoutException`은 `AI_TIMEOUT`으로 분기
- 피부는 **정면·좌·우 3장을 한 번의 Vision 호출**로. 장당 호출 후 평균 금지 — 각도마다 점수가 달라진다

## 프로젝트 컨벤션

- 모든 DTO는 `record`. 변환은 `Response.from(entity)` / `Request.toCommand()`
- Entity는 `@NoArgsConstructor(PROTECTED)` + 정적 팩토리. setter 금지
- 모든 연관관계 `FetchType.LAZY`
- `@Valid` + 한국어 검증 메시지 (그대로 사용자에게 노출된다)
- HTTP: POST → 201, GET → 200, DELETE → 204. **PATCH 는 갱신 결과를 돌려주면 200** (PRD §14.3 ④-b)
- 예외는 `BusinessException(ErrorCode)` 하나로. 새 상황이면 `ErrorCode`에 추가

## 명령어

```bash
docker compose up -d postgres
set -a && source .env && set +a      # JWT_SECRET 은 한 번 만들어 고정
./gradlew bootRun                    # "테스트 계정 생성: test@skinplate.app" 이 뜨면 정상
./gradlew test --tests '*PlateRuleEngineTest'   # 60점 / 87점 재현

# 스키마 불변식 확인 — validate 는 테이블·컬럼·타입만 본다. 인덱스·CHECK·UNIQUE 는 안 본다.
# idx_app_user_email(lower(email)) = 대소문자 중복 가입 차단 / food_analysis_id UNIQUE = 음식 1장 = Plate 1건
docker exec -i skinplate-db psql -U skinplate -d skinplate -tAc \
  "select indexname from pg_indexes where schemaname='public' order by 1;
   select conname, contype::text from pg_constraint
    where connamespace='public'::regnamespace and contype in ('c','u') order by conname;"
```

**배포 서버** — 가비아 VM. `docker compose` 를 쓰지 않는다(위 compose 는 로컬 Postgres 전용).

```bash
ssh gabia '~/skinplate/deploy.sh'             # main 을 받아 빌드·교체·헬스체크까지
ssh gabia 'docker logs -f skinplate'          # 앱 로그. 에러만 보려면 2>&1 을 꼭 붙인다
ssh gabia 'sudo tail -f /var/log/caddy/api.log'   # 요청이 서버까지 왔는지 (413·502 는 여기만 남는다)
curl https://1-201-116-157.sslip.io/api/v1/health
```

`.env` 는 서버의 `~/skinplate/.env` 에만 있다 — 저장소에도 이미지에도 들어가지 않는다.
OpenAI 한도에 막히면 그 파일의 `AI_MOCK=true` 주석을 풀고 `docker restart skinplate` 가 시연 백업이다.

## Git / PR

- 브랜치명: `{type}/{설명}` (예: `feat/skin-analysis-api`). 이슈가 있으면 `{type}/{이슈번호}-{설명}`
- 커밋 메시지: Conventional Commits + 한국어 — `{type}({scope}): 작업 내용`
  - 예: `feat(skin): 피부 분석 API 구현`, `fix(plate): 나트륨 룰 임계값 누락 수정`
  - type: `feat fix refactor docs test chore ci perf`
  - scope: `auth user skin food plate recommendation insight report infra global deploy spec ci` — 여러 파트에 걸치면 생략 (`refactor: …`)
- `[#이슈번호] 작업 내용` 형식은 쓰지 않는다
- PR 제목도 커밋과 같은 형식으로 쓴다 — `develop` 은 squash 머지라 PR 제목이 그대로 `develop` 커밋 메시지가 된다
- PR 본문: 🚀 작업 내용 / 🤔 고민했던 내용 / 💬 리뷰 중점사항 — 파일·클래스명 나열 금지, 자연스러운 문장으로
- **1개 단위 = 1 브랜치 = 1 PR 원칙** (백엔드: API 단위)
- 모든 작업 브랜치는 `develop` 에서 분기, `develop` 으로 PR

**브랜치 역할이 둘로 나뉜다 — `develop` 은 개발, `main` 은 배포다.**

| 브랜치 | 역할 |
|---|---|
| `develop` | 기본 브랜치. 모든 PR 이 여기로 온다. **기본 브랜치는 계속 `develop` 이다** — `main` 으로 바꾸면 새 PR 이 배포 브랜치로 꽂혀 리뷰 전 코드가 배포본이 된다 |
| `main` | **가비아 VM 이 실제로 배포하는 브랜치.** `develop` 을 fast-forward 로만 받는다 |

```bash
git checkout main && git merge --ff-only develop && git push   # 릴리스
ssh gabia '~/skinplate/deploy.sh'                              # 배포
```

`--ff-only` 는 편의가 아니라 **`main` 을 `develop` 의 한 지점으로 묶어두는 장치**다. `main` 에 직접 커밋하면 이게 실패하면서 알려준다 — "배포된 것 = `main`" 이 거짓이 되는 순간을 조용히 넘기지 않는다.

## 절대 금지

- 시크릿을 코드 · `application*.yml` · 커밋에 기재 (`JWT_SECRET` `OPENAI_API_KEY` `DB_PASSWORD`)
- Flyway 기존 마이그레이션 파일 수정
- Controller에서 Entity 반환
- 점수 계산을 LLM에 위임 (재현성이 이 제품의 주장이다)
  - **예외는 `estimatedSkinAge` 하나다.** 8개 축을 종합한 인상이라 기계적 공식을 두지 않기로 결정했다(PRD §17.2). Skin Score·level·highlights·`skinTypeGap.observed` 는 전부 Backend 가 지표에서 다시 만든다 — 피부 나이만 재계산이 불가능하고, 그래서 흔들림도 여기서 가장 크게 보인다
- `declaredSkinType`을 `PlateContext`에 넣기 — 자가 신고값은 표시·비교 전용
- **`main` 에 직접 커밋.** 배포 브랜치라 리뷰를 건너뛴 코드가 그대로 배포본이 된다. `develop` 에서 `--ff-only` 로만 올린다
- **배포 서버에서 브랜치 바꾸기** (`deploy.sh` 의 `BRANCH` 수정 포함). 급할 때 `develop` 을 바로 올리고 싶어지는데, 그 순간부터 `main` 은 배포본이 아니고 아무도 그걸 모른다
- **08-21 이후 커밋.** 그날 업로드가 닫힌다 — 기능·QA·배포·촬영이 전부 그 안에 들어간다
- 기능 동결은 날짜가 아니라 **G5(배포본 E2E 1회 완주)** 로 판단한다. PRD 의 "Day 8 동결"은 08-17 발표 전제였고 무효다
