# 히스토리 · 오늘의 기록 · 리포트 — 최종 구현 스펙

| 항목 | 내용 |
|---|---|
| 확정일 | 2026-08-14 (Day 7) |
| 전제 | **분석 완료 = 기록 생성.** 분석/기록 분리는 Phase 2 |
| 금지 | DB migration · 저장 구조 변경 · 분석/점수 로직 변경 · `POST /plates` 동작 변경 |
| 데이터 | 기존 저장 데이터 + `skin_plate_feedback` 만 사용 |

---

## 1. 유저 플로우

```
음식 촬영 → 분석 → 결과(S07) → [기록됨]
                                   ↓
홈 ─ 오늘의 기록 카드 ──tap──▶ 리포트 [오늘 | 이번 주]
   └ 기록 보기 ─────────────▶ 히스토리 (날짜별)
```

## 2. 화면

| 화면 | 신규 | 비고 |
|---|---|---|
| 홈 오늘의 기록 카드 | 기존 홈에 블록 추가 | 화면 아님 |
| 리포트 | 신규 1개 | `[오늘 | 이번 주]` 토글 |
| 히스토리 | 신규 1개 | 날짜별 그룹 |

## 3. 시간대 — 모든 날짜 계산의 기준

`created_at` 은 `timestamp without time zone` 이고 `@CreatedDate LocalDateTime` 은 **JVM 기본 시간대**로 기록한다. 배포 이미지(`eclipse-temurin:21-jre`)의 기본값은 **UTC**, 개발 맥은 **KST** 다. 그대로 두면 같은 컬럼이 환경마다 다른 뜻이 되고, **00:00~09:00 KST 기록이 전날로 잡힌다.**

**두 가지를 모두 적용한다.**

1. `Dockerfile` 에 `ENV TZ=Asia/Seoul` 추가 — 기록 시점을 KST 벽시계로 고정한다. 플랫폼 환경변수로 넣으면 빠뜨렸을 때 조용히 9시간 밀리므로 이미지에 박는다(`SPRING_PROFILES_ACTIVE=prod` 와 같은 이유).
2. 서비스의 날짜 계산은 **시스템 시간대에 의존하지 않는다.**

```java
private static final ZoneId KST = ZoneId.of("Asia/Seoul");

LocalDate today = LocalDate.now(KST);                       // ← now() 금지
LocalDateTime from        = date.atStartOfDay();            // KST 달력일 00:00
LocalDateTime toExclusive = date.plusDays(1).atStartOfDay();
```

**DB 조회는 half-open interval 로 한다.**

```
created_at >= from  AND  created_at < toExclusive
```

`BETWEEN` 을 쓰지 않는다 — 경계 자정의 기록이 두 날에 겹쳐 잡힌다.

> DB 컬럼 타입은 바꾸지 않는다. migration 없음.

## 4. API 계약

### 4.0 공통 규약 — 인가

**두 API 모두 인증된 현재 사용자의 데이터만 조회한다.** 사용자 식별은 `@CurrentUser Long userId` 하나뿐이고, 요청 본문·쿼리의 userId 는 존재하더라도 무시한다.

모든 조회 쿼리는 `user_id` 조건을 포함한다 — `skin_plate` · `skin_analysis` 둘 다. 집계 대상이 되는 `skin_plate_feedback` 은 `skin_plate` 를 거쳐서만 접근하므로 별도 조건이 필요 없다(부모가 이미 사용자로 좁혀져 있다).

기간에 남의 기록이 섞일 여지를 없애는 것이 목적이다. 리포트는 숫자만 돌려주기 때문에 새어도 눈에 띄지 않는다 — 조회 단계에서 막는다.

### 4.1 `GET /api/v1/reports?period=TODAY|WEEK`

| 파라미터 | 필수 | 의미 |
|---|---|---|
| `period` | ✅ | `TODAY` = 오늘(KST) 하루 · `WEEK` = 오늘 포함 최근 7일 |

기준일은 **서버의 오늘(KST)** 이다. 과거 임의 날짜 조회는 범위 밖 — 히스토리가 그 역할을 한다.

```jsonc
{
  "period": "WEEK",
  "from": "2026-08-08",              // KST 달력일 (포함)
  "to":   "2026-08-14",              // KST 달력일 (포함)

  // 기간 내 가장 최근 분석 1건의 점수. 주간 평균이 아니다.
  // WEEK 화면에서는 "최근 Skin Score" 로 표기하고 크게 쓰지 않는다.
  "latestSkinScore": 72,             // 없으면 null

  "skinScoreTrend": [                // WEEK 전용. TODAY 는 []
    {"date": "2026-08-08", "score": 55},
    {"date": "2026-08-10", "score": 61}
  ],

  "recordCount": 12,
  "averagePlateScore": 68,           // round(avg). 0건이면 null

  "penalties": [
    {"ruleCode": "R04", "label": "나트륨 과다", "count": 5,
     "totalDelta": -38, "topFoods": ["떡볶이", "라면", "치킨"]}
  ],

  "meals": [                         // TODAY 전용. WEEK 는 []
    {"plateId": 12, "foodName": "떡볶이", "plateScore": 65,
     "recordedAt": "2026-08-14T12:32:00"}
  ]
}
```

**`latestSkinScore` 는 주간 평균이 아니다.** WEEK 의 주인공은 `skinScoreTrend` 이고, 이 값을 화면에 쓸 때는 반드시 **"최근 Skin Score"** 로 표기한다. 주간 리포트의 핵심은 현재 점수 하나가 아니라 7일간의 변화와 패턴이다.

### 4.2 `GET /api/v1/plates?from=YYYY-MM-DD&to=YYYY-MM-DD`

`from` · `to` 는 **KST 달력일이며 `to` 도 포함**한다.

```
from=2026-08-08 & to=2026-08-14
  → 2026-08-08 00:00 KST 이상, 2026-08-15 00:00 KST 미만
```

서버가 날짜별로 묶어서 내려준다. 날짜 내림차순, 같은 날 안에서는 시각 내림차순.

```jsonc
{
  "days": [
    {"date": "2026-08-14",
     "skinScore": 72,                // 그날 최신 분석. 없으면 null
     "plates": [
       {"plateId": 12, "foodName": "떡볶이", "plateScore": 65,
        "recordedAt": "2026-08-14T12:32:00"}
     ]}
  ]
}
```

`recordedAt` 은 `skin_plate.created_at` 을 그대로 내보낸다 — 기존 응답들이 쓰는 `LocalDateTime` 직렬화 형식과 동일하게 유지한다.

## 5. 지표 계산 규칙

| 지표 | 출처 | 계산 | 빈 데이터 |
|---|---|---|---|
| `latestSkinScore` | `skin_analysis` | 기간 내 `created_at` 최신 1건의 `skin_score` | `null` |
| `skinScoreTrend` | `skin_analysis` | **KST 달력일별 최신** `skin_analysis` 1건의 `skin_score`. 하루에 여러 번 찍었으면 `created_at` 이 가장 늦은 것. **있는 날만** 배열에 담는다(날짜 오름차순) | `[]` |
| `recordCount` | `skin_plate` | 행 수 | `0` |
| `averagePlateScore` | `skin_plate` | `round(avg(plate_score))` | `null` |
| `penalties` | `skin_plate_feedback` | 아래 §5.1 | `[]` |
| `meals` | `skin_plate` + `food_analysis` | `plateId` · `foodName` · `plateScore` · `recordedAt` | `[]` |

기록이 하루 이틀뿐이어도 "데이터가 부족합니다"로 끝내지 않는다. **있는 날만 그린다.**

### 5.1 감점 집계

**대상 행**

```
type = 'CAUTION'  AND  score_delta < 0
```

`type` 만으로 거르지 않는다. `applied_rules`(JSONB)는 가점·감점이 섞여 있어 **주간 감점 집계에 사용하지 않는다.**

**집계**

```
rule_code 로 그룹
  count       = 행 수
  totalDelta  = sum(score_delta)          (음수)
  label       = 그룹 내 message            (§5.2)
  topFoods    = 그 룰이 적용된 Plate 의 food_name 빈도 상위 3
```

`topFoods` 는 **그 룰이 적용된 Plate 의 개수**를 센다. 같은 음식을 두 번 먹어 두 번 다 나트륨 감점을 받았으면 2 다 — 음식 종류가 아니라 반복된 끼니를 세는 것이 "무엇이 반복되나"라는 질문에 맞는 답이다.

**정렬 (deterministic)**

| 대상 | 1순위 | 2순위 | 3순위 |
|---|---|---|---|
| `penalties` | `count` 내림차순 | `totalDelta` 오름차순(감점 큰 것 먼저) | `ruleCode` 오름차순 |
| `topFoods` | 빈도 내림차순 | `foodName` 사전순 오름차순 | — |

동률 tie-breaker 를 명시하지 않으면 같은 데이터에 다른 화면이 나온다. 재현성이 이 제품의 주장이다.

### 5.2 룰 표시명 — 새로 만들지 않는다

9개 룰 전부 짧은 라벨을 **리터럴로** 반환하고, 그 값이 이미 `skin_plate_feedback.message` 에 저장돼 있다.

```
R04 나트륨 과다 · R02 매운맛 자극 · R03 당류 과다 · R07 튀김 조리
R01 수분 보충 재료 · R05 단백질 충분 · R06 비타민 풍부 · R08 오메가3 함유 · R09 발효식품 포함
```

따라서 **`PlateRule` 수정 없음 · Flutter 룰 매핑 하드코딩 없음 · 새 매핑 테이블 없음.**

**데이터 계약** — 같은 `rule_code` 그룹의 `message` 는 같은 라벨이다. 9개 룰이 전부 리터럴을 반환하므로 지금은 참이고, 집계는 이 전제 위에 선다. 그룹의 라벨은 **그룹 내 아무 행의 `message`** 를 쓴다(구현상 첫 행).

이 전제가 깨지는 경우는 하나뿐이다 — 누군가 룰의 짧은 라벨을 문자열 조합으로 바꾸는 것. 그러면 같은 룰이 여러 라벨로 갈리는 게 아니라 **그룹 라벨이 어느 행을 집었느냐에 따라 달라진다.** 짧은 라벨은 리터럴로 유지한다.

## 6. 오늘 vs 이번 주 — 같은 레이아웃, 다른 강조점

| | 오늘 | 이번 주 |
|---|---|---|
| 머리 | 오늘 Skin Score (큼) | **7일 추이 그래프 (큼)** |
| 중간 | **오늘 먹은 것 목록** | 평균 Plate Score · 총 기록 수 · 최근 Skin Score |
| 아래 | 오늘 주요 감점 1개 | **반복 감점 Top 3 + 관련 음식** |

오늘은 *"무엇을 먹었고 어떻게 됐나"*, 이번 주는 *"무엇이 반복되나"*.

## 7. 계산 책임

**전부 서버.** Flutter 는 받은 것을 그리기만 한다 — 그쪽이 병목이고 실기기 검증까지 담당한다. 히스토리 날짜 그룹핑도 서버가 한다.

## 8. 구현 범위

**Backend (신규)**

```
domain/report/controller/ReportController.java
domain/report/service/ReportService.java
domain/report/dto/ReportResponse.java · PenaltyDto · MealDto · TrendPointDto
domain/plate/dto/PlateHistoryResponse.java · PlateHistoryDayDto · PlateHistoryItemDto
```

**Backend (수정)**

```
domain/plate/controller/SkinPlateController.java        GET /plates 추가
domain/plate/repository/SkinPlateRepository.java        날짜 범위 조회 1개
domain/skin/repository/SkinAnalysisRepository.java      날짜 범위 조회 1개
Dockerfile                                              ENV TZ=Asia/Seoul
```

조회는 `@Query` + `@EntityGraph(attributePaths = {"feedbacks", "foodAnalysis"})` 로 N+1 을 막고, 집계는 Java 에서 한다. 주간 최대 ~21행이라 네이티브 쿼리가 필요 없다.

```java
@EntityGraph(attributePaths = {"feedbacks", "foodAnalysis"})
@Query("select p from SkinPlate p where p.user.id = :userId "
     + "and p.createdAt >= :from and p.createdAt < :toExclusive order by p.createdAt desc")
List<SkinPlate> findInRange(@Param("userId") Long userId,
                            @Param("from") LocalDateTime from,
                            @Param("toExclusive") LocalDateTime toExclusive);
```

**Flutter**

```
홈 오늘의 기록 카드 · 리포트 화면(토글) · 히스토리 화면 · DTO/datasource/repository/notifier
```

추이 그래프는 **의존성을 추가하지 않는다.** 막대 7개를 `Container` 높이로 그린다.

## 9. 제외 범위

기록 상세 · 기록 삭제 · 과거 임의 날짜 리포트 · 나트륨 누적(mg) · 지난주 대비 비교 · 서버 이미지 저장(R2 포함) · 분석/기록 분리 · DB migration · 기존 분석/점수 로직 변경 · `POST /plates` 동작 변경

## 10. 난이도 · 작업량

| | 난이도 | 작업량 |
|---|---|---|
| 백엔드 2 API + 테스트 | 낮음 | 3~4h |
| 히스토리 화면 | 낮음 | 1.5h |
| 리포트 화면 | 중간(그래프) | 2.5~3h |
| 홈 카드 | 낮음 | 0.5h |

**막히면 버리는 순서** — 추이 그래프 → 리포트 화면 → 히스토리. 홈 카드와 백엔드 API 는 남긴다.

## 11. 일정 판단

**CONDITIONAL GO.** 백엔드는 조회 전용이라 P0 를 건드리지 않으므로 지금 시작해도 된다. **Flutter 4~5시간이 조건이며, 실기기 ML Kit 3방향 게이트 검증이 끝난 뒤에 착수한다.** 히스토리·리포트 때문에 P0 가 밀리면 안 된다.
