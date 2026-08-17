# 히스토리 · 리포트 백엔드 구현 계획

> ### ⚠️ 이 계획은 완료됐고, 리포트 부분은 그 뒤 삭제됐다 (2026-08-17)
>
> **다시 실행하지 마라.** 이 문서에 통째로 실린 `ReportService` · `ReportResponse` · `ReportPeriod` · `PenaltyDto` · `MealDto` · `TrendPointDto` · `ReportServiceTest` 와 `DateRange.today()` 는 **전부 삭제된 코드**다. 앱이 `GET /reports/daily` · `GET /reports/weekly` 로 옮겨 가면서 `GET /reports?period=` 를 읽는 곳이 없어졌고, 두 응답의 "이번 주 평균"이 정의가 달라(끼니 평균 vs 일 평균의 평균) 함께 두면 화면마다 다른 숫자가 뜬다.
>
> 이 문서의 `Create:` 단계를 그대로 따르면 지운 500줄이 되살아난다. **살아 있는 계약은 `SkinPlate_DTO_Domain.md` 계약 대조표**이고, 현재 구조는 PRD §18.11 에 있다. 히스토리(`GET /plates?from=&to=`) 부분은 그대로 유효하다.
>
> 본문은 그때의 기록으로 남긴다 — 지우면 왜 그렇게 만들었다가 왜 접었는지가 사라진다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이미 저장된 데이터만으로 히스토리·오늘의 기록·리포트를 서빙하는 조회 전용 API 두 개를 만든다.

**Architecture:** 기존 저장·분석·점수 로직을 하나도 건드리지 않는다. `SkinPlate` 를 사용자·기간으로 좁혀 한 번 읽고, 그 안의 `feedbacks` 로 감점을 집계한다. 날짜 경계는 KST 로 고정하되 근본 원인인 **저장 시각 쪽**을 먼저 잡는다.

**Tech Stack:** Spring Boot 3.3.5 · Java 21 · Spring Data JPA · PostgreSQL 16 · JUnit 5 + AssertJ + Mockito

**Spec:** `docs/superpowers/specs/2026-08-14-history-and-reports-design.md`

## Global Constraints

- **DB migration 금지.** 새 테이블·컬럼·`V4__*.sql` 없음
- **기존 분석/점수 계산/저장 로직 변경 금지.** `POST /plates` 동작 무변경
- **모든 조회는 인증된 `@CurrentUser Long userId` 의 데이터만.** 요청의 userId 는 무시
- **`skin_plate_feedback` 을 직접 조회하지 않는다.** `user_id` 컬럼이 없어 전체 사용자가 섞인다
- 날짜는 전부 **KST(`ZoneId.of("Asia/Seoul")`)** 기준 달력일. 조회는 half-open `>= from AND < toExclusive`
- 모든 DTO 는 `record`. 응답은 `ApiResponse.ok(...)` 로 감싼다
- `spring.jackson.default-property-inclusion: non_null` — **null 필드는 키가 통째로 빠진다**
- 커밋: Conventional Commits + 한국어. 브랜치는 `develop` 에서 분기

## File Structure

| 파일 | 책임 |
|---|---|
| `global/config/JpaConfig.java` (수정) | 저장 시각을 KST 로 고정 |
| `Dockerfile` (수정) | 로그 타임스탬프 KST |
| `domain/plate/repository/SkinPlateRepository.java` (수정) | 기간 조회 1개 추가 · 사문 1개 삭제 |
| `domain/skin/repository/SkinAnalysisRepository.java` (수정) | 기간 조회 1개 추가 |
| `global/common/DateRange.java` (신규) | KST 달력일 → half-open 구간. 검증 포함 |
| `domain/report/dto/*.java` (신규 4) | 리포트 응답 |
| `domain/report/service/ReportService.java` (신규) | 기간 집계 |
| `domain/report/controller/ReportController.java` (신규) | `GET /reports` |
| `domain/plate/dto/PlateHistory*.java` (신규 3) | 히스토리 응답 |
| `domain/plate/service/PlateHistoryService.java` (신규) | 날짜별 그룹핑 |
| `domain/plate/controller/SkinPlateController.java` (수정) | `GET /plates` 추가 |

---

## Task 1: 저장 시각을 KST 로 고정한다

`created_at` 은 `LocalDateTime.now()` 를 JVM 기본 시간대로 쓴다. 배포 이미지는 UTC, 개발 맥은 KST 다. 읽기만 KST 로 맞추면 쓰기가 UTC 인 환경에서 9시간 밀린다 — 그래서 쓰기부터 잡는다.

**Files:**
- Modify: `src/main/java/com/skinplate/api/global/config/JpaConfig.java`
- Modify: `Dockerfile`
- Test: `src/test/java/com/skinplate/api/global/config/JpaConfigTest.java`

**Interfaces:**
- Produces: `JpaConfig.kstDateTimeProvider()` — `DateTimeProvider` 빈. 이후 모든 `@CreatedDate` 가 KST 벽시계로 기록된다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 저장 시각이 실행 환경의 시간대를 따라가면, 같은 컬럼이 개발 맥에서는 KST 로
 * 배포 컨테이너에서는 UTC 로 쓰인다. 그러면 자정부터 아침 아홉시까지의 기록이
 * 전날로 잡히는데 예외도 로그도 남지 않는다.
 */
class JpaConfigTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("저장 시각은 실행 환경이 아니라 KST 를 따른다")
    void providerReturnsKst() {
        DateTimeProvider provider = new JpaConfig().kstDateTimeProvider();

        LocalDateTime actual = LocalDateTime.from(provider.getNow().orElseThrow());

        assertThat(actual)
                .isCloseTo(LocalDateTime.now(KST), within(5, ChronoUnit.SECONDS));
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*JpaConfigTest'`
Expected: FAIL — `cannot find symbol: method kstDateTimeProvider()`

- [ ] **Step 3: 최소 구현**

`JpaConfig.java` 전체를 아래로 교체한다.

```java
package com.skinplate.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaConfig {

    /**
     * 저장 시각은 실행 환경이 아니라 서비스 기준(KST)을 따른다.
     *
     * created_at 은 timestamp without time zone 이라 값 자체에 시간대가 없다.
     * 기본 제공자는 LocalDateTime.now() 를 JVM 기본 시간대로 부르는데,
     * 배포 이미지는 UTC 이고 개발 맥은 KST 다. 그대로 두면 같은 컬럼이
     * 환경마다 다른 뜻이 되고, 리포트의 "오늘" 경계가 아홉 시간 어긋난다.
     */
    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*JpaConfigTest'`
Expected: PASS

- [ ] **Step 5: 기존 테스트가 안 깨졌는지 본다**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 83건 + 1건

- [ ] **Step 6: Dockerfile 에 TZ 를 넣는다**

`ENV SPRING_PROFILES_ACTIVE=prod` 바로 아래에 추가한다. **멀티스테이지이므로 반드시 최종 스테이지다** — build 스테이지에 넣으면 아무 일도 하지 않는다.

```dockerfile
# 저장 시각은 JpaConfig 가 KST 로 고정한다. 이건 로그 타임스탬프용이다 —
# 시연 중 로그를 보면서 아홉 시간을 암산하지 않으려고 맞춰 둔다.
ENV TZ=Asia/Seoul
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/skinplate/api/global/config/JpaConfig.java \
        src/test/java/com/skinplate/api/global/config/JpaConfigTest.java Dockerfile
git commit -m "fix(global): 저장 시각을 실행 환경이 아니라 KST 로 고정"
```

---

## Task 2: KST 달력일 → 조회 구간

날짜 계산이 세 곳(리포트 TODAY·WEEK, 히스토리)에 필요하다. 한 곳에 모아 두지 않으면 경계 규칙이 조금씩 달라진다.

**Files:**
- Create: `src/main/java/com/skinplate/api/global/common/DateRange.java`
- Test: `src/test/java/com/skinplate/api/global/common/DateRangeTest.java`

**Interfaces:**
- Produces:
  - `DateRange.of(LocalDate from, LocalDate to)` → `DateRange`
  - `DateRange.today()` → `DateRange`
  - `DateRange.lastDays(int days)` → `DateRange` (오늘 포함)
  - `record DateRange(LocalDate fromDate, LocalDate toDate)` with `from()` → `LocalDateTime`, `toExclusive()` → `LocalDateTime`
  - `DateRange.KST` → `ZoneId`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    @DisplayName("to 날짜도 포함한다 — 끝 경계는 다음 날 자정 직전까지")
    void toDateIsInclusive() {
        DateRange range = DateRange.of(LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 14));

        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 8, 8, 0, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
    }

    @Test
    @DisplayName("하루짜리 구간도 다음 날 자정까지다 — BETWEEN 이면 자정 기록이 두 날에 겹친다")
    void singleDay() {
        DateRange range = DateRange.of(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14));

        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 8, 14, 0, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
    }

    @Test
    @DisplayName("최근 7일은 오늘을 포함해 7일이다")
    void lastDaysIncludesToday() {
        DateRange range = DateRange.lastDays(7);

        assertThat(range.toDate()).isEqualTo(LocalDate.now(DateRange.KST));
        assertThat(range.fromDate()).isEqualTo(LocalDate.now(DateRange.KST).minusDays(6));
    }

    @Test
    @DisplayName("오늘은 KST 기준이다 — 시스템 시간대를 따르지 않는다")
    void todayIsKst() {
        assertThat(DateRange.today().fromDate()).isEqualTo(LocalDate.now(DateRange.KST));
    }

    @Test
    @DisplayName("from 이 to 보다 뒤면 400 이다 — 조용히 빈 배열을 주지 않는다")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 8)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("90일을 넘으면 400 이다 — 한 번의 호출이 전체 기록을 메모리로 끌어올린다")
    void tooWideRangeIsRejected() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> DateRange.of(from, from.plusDays(90)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("정확히 90일은 통과한다")
    void exactlyMaxRangeIsAllowed() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(DateRange.of(from, from.plusDays(89)).toDate()).isEqualTo(from.plusDays(89));
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*DateRangeTest'`
Expected: FAIL — `package com.skinplate.api.global.common does not contain DateRange`

- [ ] **Step 3: 최소 구현**

```java
package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * KST 달력일 구간. 조회는 half-open 이다 — from 이상, toExclusive 미만.
 *
 * BETWEEN 을 쓰면 끝 경계 자정에 찍힌 기록이 두 날에 겹쳐 잡힌다.
 * 리포트와 히스토리가 같은 날을 다르게 세는 순간 숫자를 설명할 수 없게 된다.
 */
public record DateRange(LocalDate fromDate, LocalDate toDate) {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한 번의 호출이 사용자의 전체 기록을 메모리로 끌어올리지 않게 막는다. */
    private static final int MAX_DAYS = 90;

    public static DateRange of(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간을 지정해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "조회 기간은 " + MAX_DAYS + "일을 넘을 수 없습니다.");
        }
        return new DateRange(fromDate, toDate);
    }

    public static DateRange today() {
        LocalDate today = LocalDate.now(KST);
        return new DateRange(today, today);
    }

    /** 오늘을 포함해 days 일. days=7 이면 6일 전부터 오늘까지다. */
    public static DateRange lastDays(int days) {
        LocalDate today = LocalDate.now(KST);
        return new DateRange(today.minusDays(days - 1L), today);
    }

    public LocalDateTime from() {
        return fromDate.atStartOfDay();
    }

    public LocalDateTime toExclusive() {
        return toDate.plusDays(1).atStartOfDay();
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*DateRangeTest'`
Expected: PASS (7건)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/skinplate/api/global/common/DateRange.java \
        src/test/java/com/skinplate/api/global/common/DateRangeTest.java
git commit -m "feat(global): KST 달력일 조회 구간 · 범위 검증"
```

---

## Task 3: 기간 조회 리포지토리

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/plate/repository/SkinPlateRepository.java`
- Modify: `src/main/java/com/skinplate/api/domain/skin/repository/SkinAnalysisRepository.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `SkinPlateRepository.findInRange(Long userId, LocalDateTime from, LocalDateTime toExclusive)` → `List<SkinPlate>` (createdAt 내림차순, `feedbacks`·`foodAnalysis`·`skinAnalysis` 로딩됨)
  - `SkinAnalysisRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Long userId, LocalDateTime from, LocalDateTime toExclusive)` → `List<SkinAnalysis>`

- [ ] **Step 1: `SkinPlateRepository` 를 고친다**

`findByUserIdOrderByCreatedAtDesc` 를 **지우고**(호출자 0) 아래를 넣는다. import 4개(`EntityGraph`, `Query`, `Param`, `LocalDateTime`)도 함께.

```java
    /**
     * 리포트·히스토리용 기간 조회. userId 조건이 빠지면 남의 기록이 섞이는데,
     * 단일 사용자 개발 DB 에서는 그대로 통과한다. 조건을 쿼리에 박아 둔다.
     *
     * feedbacks 는 감점 집계에 바로 쓰이므로 같이 읽는다. batch_fetch_size 가
     * 이미 N+1 을 한 번의 추가 쿼리로 접지만, 그 왕복 하나를 더 아낀다.
     */
    @EntityGraph(attributePaths = {"feedbacks", "foodAnalysis", "skinAnalysis"})
    @Query("select p from SkinPlate p "
         + "where p.user.id = :userId and p.createdAt >= :from and p.createdAt < :toExclusive "
         + "order by p.createdAt desc")
    List<SkinPlate> findInRange(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from,
                                @Param("toExclusive") LocalDateTime toExclusive);
```

- [ ] **Step 2: `SkinAnalysisRepository` 에 파생 쿼리를 넣는다**

메서드 이름에 `UserId` 가 박혀 있어 조건을 빠뜨릴 수 없다.

```java
    /** 리포트 추이용. 이름에 UserId 가 들어가 조건을 빠뜨릴 수 없다. */
    List<SkinAnalysis> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long userId, LocalDateTime from, LocalDateTime toExclusive);
```

- [ ] **Step 3: 컴파일과 기존 테스트를 확인한다**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — 사문 메서드를 지웠으므로 컴파일 오류가 나면 호출자가 있다는 뜻이다

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/plate/repository/SkinPlateRepository.java \
        src/main/java/com/skinplate/api/domain/skin/repository/SkinAnalysisRepository.java
git commit -m "feat(plate): 사용자·기간으로 좁힌 조회 추가 · 사문 메서드 제거"
```

---

## Task 4: 리포트 DTO

**Files:**
- Create: `src/main/java/com/skinplate/api/domain/report/dto/ReportResponse.java`
- Create: `src/main/java/com/skinplate/api/domain/report/dto/PenaltyDto.java`
- Create: `src/main/java/com/skinplate/api/domain/report/dto/MealDto.java`
- Create: `src/main/java/com/skinplate/api/domain/report/dto/TrendPointDto.java`
- Create: `src/main/java/com/skinplate/api/domain/report/dto/ReportPeriod.java`

**Interfaces:**
- Produces:
  - `enum ReportPeriod { TODAY, WEEK }` with `DateRange range()`
  - `record TrendPointDto(LocalDate date, int score)`
  - `record MealDto(Long plateId, String foodName, int plateScore, LocalDateTime recordedAt)`
  - `record PenaltyDto(String ruleCode, String label, int count, int totalDelta, List<String> topFoods)`
  - `record ReportResponse(ReportPeriod period, LocalDate from, LocalDate to, Integer latestSkinScore, List<TrendPointDto> skinScoreTrend, int recordCount, Integer averagePlateScore, List<PenaltyDto> penalties, List<MealDto> meals)`

- [ ] **Step 1: 다섯 파일을 만든다**

```java
// domain/report/dto/ReportPeriod.java
package com.skinplate.api.domain.report.dto;

import com.skinplate.api.global.common.DateRange;

/** 리포트 화면의 기간 토글. 기준일은 서버의 오늘(KST) 이다. */
public enum ReportPeriod {

    TODAY { @Override public DateRange range() { return DateRange.today(); } },
    WEEK  { @Override public DateRange range() { return DateRange.lastDays(7); } };

    public abstract DateRange range();
}
```

```java
// domain/report/dto/TrendPointDto.java
package com.skinplate.api.domain.report.dto;

import java.time.LocalDate;

/** 추이 그래프의 점 하나. 기록이 없는 날은 아예 배열에 넣지 않는다. */
public record TrendPointDto(LocalDate date, int score) {}
```

```java
// domain/report/dto/MealDto.java
package com.skinplate.api.domain.report.dto;

import java.time.LocalDateTime;

/** 오늘 먹은 것 한 줄. TODAY 에서만 채운다. */
public record MealDto(Long plateId, String foodName, int plateScore, LocalDateTime recordedAt) {}
```

```java
// domain/report/dto/PenaltyDto.java
package com.skinplate.api.domain.report.dto;

import java.util.List;

/**
 * 반복된 감점 하나.
 *
 * label 은 새로 만든 이름이 아니라 저장된 피드백의 message 다 — 9개 룰이 모두
 * 짧은 라벨을 리터럴로 돌려주고 그 값이 그대로 기록돼 있다.
 *
 * @param totalDelta 음수. 합산 감점
 * @param topFoods   그 룰이 적용된 Plate 의 음식명, 빈도 상위 3
 */
public record PenaltyDto(String ruleCode, String label, int count, int totalDelta,
                         List<String> topFoods) {}
```

```java
// domain/report/dto/ReportResponse.java
package com.skinplate.api.domain.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 오늘과 이번 주가 같은 형태를 쓴다. 화면이 하나이기 때문이다.
 *
 * null 인 필드는 응답에서 키가 통째로 빠진다(default-property-inclusion: non_null).
 * 앱은 "키가 없음"을 "값 없음"으로 읽어야 한다.
 *
 * @param latestSkinScore  기간 내 가장 최근 분석 1건의 점수. <b>주간 평균이 아니다.</b>
 *                         얼굴을 매일 찍지 않으므로 TODAY 에서는 대개 비어 있다
 * @param skinScoreTrend   WEEK 전용. TODAY 는 빈 배열
 * @param meals            TODAY 전용. WEEK 는 빈 배열
 */
public record ReportResponse(
        ReportPeriod period,
        LocalDate from,
        LocalDate to,
        Integer latestSkinScore,
        List<TrendPointDto> skinScoreTrend,
        int recordCount,
        Integer averagePlateScore,
        List<PenaltyDto> penalties,
        List<MealDto> meals
) {}
```

- [ ] **Step 2: 컴파일을 확인한다**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/report/dto/
git commit -m "feat(report): 리포트 응답 DTO"
```

---

## Task 5: 리포트 집계

**Files:**
- Create: `src/main/java/com/skinplate/api/domain/report/service/ReportService.java`
- Test: `src/test/java/com/skinplate/api/domain/report/ReportServiceTest.java`

**Interfaces:**
- Consumes: `SkinPlateRepository.findInRange`, `SkinAnalysisRepository.findByUserIdAndCreatedAt...`, `DateRange`, Task 4 의 DTO 전부
- Produces: `ReportService.get(Long userId, ReportPeriod period)` → `ReportResponse`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.domain.report;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.PenaltyDto;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.service.ReportService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 리포트는 저장된 것만 센다. 새 계산 규칙을 만들지 않는다.
 * 감점 라벨도 새로 만들지 않고 기록된 피드백의 message 를 그대로 쓴다.
 */
class ReportServiceTest {

    private static final Long USER_ID = 1L;

    private SkinPlateRepository skinPlateRepository;
    private SkinAnalysisRepository skinAnalysisRepository;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        skinPlateRepository = mock(SkinPlateRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        reportService = new ReportService(skinPlateRepository, skinAnalysisRepository);

        given(skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        anyLong(), any(), any()))
                .willReturn(List.of());
    }

    @Test
    @DisplayName("기록이 하나도 없으면 평균과 최근 점수는 null 이고 목록은 빈 배열이다")
    void emptyPeriod() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        ReportResponse response = reportService.get(USER_ID, ReportPeriod.TODAY);

        assertThat(response.recordCount()).isZero();
        assertThat(response.averagePlateScore()).isNull();
        assertThat(response.latestSkinScore()).isNull();
        assertThat(response.penalties()).isEmpty();
        assertThat(response.meals()).isEmpty();
        assertThat(response.skinScoreTrend()).isEmpty();
    }

    @Test
    @DisplayName("평균은 반올림한다 — 60·65 면 63 이다")
    void averageIsRounded() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "떡볶이", 60, LocalDateTime.now()),
                                    plate(2L, "치킨", 65, LocalDateTime.now())));

        assertThat(reportService.get(USER_ID, ReportPeriod.TODAY).averagePlateScore()).isEqualTo(63);
    }

    @Test
    @DisplayName("감점만 센다 — GOOD 과 ACTION 은 집계에 들어가지 않는다")
    void countsOnlyPenalties() {
        SkinPlate plate = plate(1L, "떡볶이", 60, LocalDateTime.now());
        addFeedback(plate, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(plate, FeedbackType.GOOD, "R05", 6, "단백질 충분");
        addFeedback(plate, FeedbackType.ACTION, "R04", 0, "국물을 절반만 남기세요.");
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(plate));

        List<PenaltyDto> penalties = reportService.get(USER_ID, ReportPeriod.WEEK).penalties();

        assertThat(penalties).hasSize(1);
        assertThat(penalties.get(0).ruleCode()).isEqualTo("R04");
        assertThat(penalties.get(0).label()).isEqualTo("나트륨 과다");
        assertThat(penalties.get(0).totalDelta()).isEqualTo(-8);
    }

    @Test
    @DisplayName("반복 횟수 내림차순, 같으면 감점이 큰 쪽 · 그다음 코드순")
    void penaltiesAreDeterministic() {
        SkinPlate first = plate(1L, "떡볶이", 60, LocalDateTime.now());
        addFeedback(first, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(first, FeedbackType.CAUTION, "R02", -12, "매운맛 자극");
        SkinPlate second = plate(2L, "라면", 55, LocalDateTime.now());
        addFeedback(second, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(first, second));

        assertThat(reportService.get(USER_ID, ReportPeriod.WEEK).penalties())
                .extracting(PenaltyDto::ruleCode)
                .containsExactly("R04", "R02");
    }

    @Test
    @DisplayName("관련 음식은 그 룰이 적용된 끼니 수로 센다 — 같은 음식을 두 번 먹으면 2 다")
    void topFoodsCountMeals() {
        SkinPlate first = plate(1L, "라면", 55, LocalDateTime.now());
        addFeedback(first, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        SkinPlate second = plate(2L, "라면", 55, LocalDateTime.now());
        addFeedback(second, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        SkinPlate third = plate(3L, "떡볶이", 60, LocalDateTime.now());
        addFeedback(third, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(first, second, third));

        assertThat(reportService.get(USER_ID, ReportPeriod.WEEK).penalties().get(0).topFoods())
                .containsExactly("라면", "떡볶이");
    }

    @Test
    @DisplayName("TODAY 는 먹은 목록을 채우고 추이는 비운다")
    void todayFillsMealsNotTrend() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "떡볶이", 60, LocalDateTime.now())));

        ReportResponse response = reportService.get(USER_ID, ReportPeriod.TODAY);

        assertThat(response.meals()).hasSize(1);
        assertThat(response.skinScoreTrend()).isEmpty();
    }

    @Test
    @DisplayName("WEEK 는 날짜별 최신 분석으로 추이를 만들고 먹은 목록은 비운다")
    void weekFillsTrendNotMeals() {
        LocalDateTime day = LocalDateTime.of(2026, 8, 12, 9, 0);
        given(skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        anyLong(), any(), any()))
                .willReturn(List.of(analysis(72, day.withHour(21)),   // 같은 날 늦은 것이 이긴다
                                    analysis(60, day)));
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        ReportResponse response = reportService.get(USER_ID, ReportPeriod.WEEK);

        assertThat(response.skinScoreTrend()).hasSize(1);
        assertThat(response.skinScoreTrend().get(0).score()).isEqualTo(72);
        assertThat(response.latestSkinScore()).isEqualTo(72);
        assertThat(response.meals()).isEmpty();
    }

    // ---- 픽스처 ----

    private static SkinPlate plate(Long id, String foodName, int score, LocalDateTime createdAt) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식",
                Nutrition.of(500, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1800, BigDecimal.ONE),
                CookingMethod.BOILED, false, "{}");
        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");

        SkinPlate plate = SkinPlate.create(user, analysis, food, score, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", createdAt);
        return plate;
    }

    private static SkinAnalysis analysis(int score, LocalDateTime createdAt) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), score, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "createdAt", createdAt);
        return analysis;
    }

    /** 인자 순서는 (ruleCode, message, scoreDelta, order) 다. */
    private static void addFeedback(SkinPlate plate, FeedbackType type, String ruleCode,
                                    int delta, String message) {
        plate.addFeedback(switch (type) {
            case GOOD    -> SkinPlateFeedback.good(ruleCode, message, delta, 0);
            case CAUTION -> SkinPlateFeedback.caution(ruleCode, message, delta, 0);
            case ACTION  -> SkinPlateFeedback.action(ruleCode, message, 0, 0);
        });
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*ReportServiceTest'`
Expected: FAIL — `ReportService` 없음

> `SkinPlateFeedback.good/caution/action` 은 **public** 이다(`of` 만 private). 인자 순서는
> `(ruleCode, message, scoreDelta, order)` — `message` 가 두 번째다. `SkinPlate.addFeedback` 은 단수형이 있다.

- [ ] **Step 3: 최소 구현**

```java
package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.MealDto;
import com.skinplate.api.domain.report.dto.PenaltyDto;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.dto.TrendPointDto;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.global.common.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 저장된 것만 센다. 새 점수도 새 규칙도 만들지 않는다.
 *
 * 집계 입력은 사용자·기간으로 이미 좁혀진 Plate 목록이다.
 * skin_plate_feedback 을 직접 조회하지 않는다 — 그 테이블에는 user_id 가 없어서
 * 직접 집계하면 모든 사용자의 행이 섞인다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 화면이 감당하는 줄 수. 늘리면 반복 감점이 목록으로 보이기 시작한다. */
    private static final int TOP_PENALTY_COUNT = 3;
    private static final int TOP_FOOD_COUNT = 3;

    private final SkinPlateRepository skinPlateRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;

    @Transactional(readOnly = true)
    public ReportResponse get(Long userId, ReportPeriod period) {
        DateRange range = period.range();

        List<SkinPlate> plates =
                skinPlateRepository.findInRange(userId, range.from(), range.toExclusive());
        List<SkinAnalysis> analyses = skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, range.from(), range.toExclusive());

        boolean week = period == ReportPeriod.WEEK;

        return new ReportResponse(
                period, range.fromDate(), range.toDate(),
                analyses.isEmpty() ? null : analyses.get(0).getSkinScore(),
                week ? trend(analyses) : List.of(),
                plates.size(),
                averageScore(plates),
                penalties(plates),
                week ? List.of() : meals(plates));
    }

    /** 0건이면 null 이다. 0 을 내려보내면 화면이 "0점"으로 그린다. */
    private Integer averageScore(List<SkinPlate> plates) {
        if (plates.isEmpty()) return null;

        return (int) Math.round(plates.stream()
                .mapToInt(SkinPlate::getPlateScore).average().orElseThrow());
    }

    /** 기록이 없는 날은 배열에 넣지 않는다. 있는 날만 그린다. */
    private List<TrendPointDto> trend(List<SkinAnalysis> analyses) {
        Map<LocalDate, SkinAnalysis> latestPerDay = new LinkedHashMap<>();

        // 입력이 createdAt 내림차순이므로 각 날짜의 첫 등장이 그날의 최신이다.
        for (SkinAnalysis analysis : analyses) {
            latestPerDay.putIfAbsent(analysis.getCreatedAt().toLocalDate(), analysis);
        }

        return latestPerDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TrendPointDto(entry.getKey(), entry.getValue().getSkinScore()))
                .toList();
    }

    private List<MealDto> meals(List<SkinPlate> plates) {
        return plates.stream()
                .sorted(Comparator.comparing(SkinPlate::getCreatedAt).reversed()
                                  .thenComparing(Comparator.comparing(SkinPlate::getId).reversed()))
                .map(plate -> new MealDto(plate.getId(), plate.getFoodAnalysis().getFoodName(),
                        plate.getPlateScore(), plate.getCreatedAt()))
                .toList();
    }

    /**
     * type 만으로 거르지 않는다. ACTION 행은 delta 가 0 이고 message 가 긴 문장이라,
     * 섞이면 5글자 칩 자리에 안내문이 들어간다.
     */
    private List<PenaltyDto> penalties(List<SkinPlate> plates) {
        record Hit(String ruleCode, String label, int delta, String foodName) {}

        List<Hit> hits = plates.stream()
                .flatMap(plate -> plate.getFeedbacks().stream()
                        .filter(feedback -> feedback.getType() == FeedbackType.CAUTION)
                        .filter(feedback -> feedback.getScoreDelta() < 0)
                        .map(feedback -> new Hit(feedback.getRuleCode(), feedback.getMessage(),
                                feedback.getScoreDelta(), plate.getFoodAnalysis().getFoodName())))
                .toList();

        return hits.stream()
                .collect(Collectors.groupingBy(Hit::ruleCode))
                .values().stream()
                .map(group -> new PenaltyDto(
                        group.get(0).ruleCode(),
                        // 같은 룰의 라벨은 하나다. 아무거나 집으면 전제가 깨졌을 때
                        // 호출마다 라벨이 달라지므로 사전순 최솟값으로 고정한다.
                        group.stream().map(Hit::label).min(Comparator.naturalOrder()).orElseThrow(),
                        group.size(),
                        group.stream().mapToInt(Hit::delta).sum(),
                        topFoods(group.stream().map(Hit::foodName).toList())))
                .sorted(Comparator.comparingInt(PenaltyDto::count).reversed()
                                  .thenComparingInt(PenaltyDto::totalDelta)
                                  .thenComparing(PenaltyDto::ruleCode))
                .limit(TOP_PENALTY_COUNT)
                .toList();
    }

    /** 음식 종류가 아니라 그 룰이 걸린 끼니 수를 센다. 같은 음식을 두 번 먹으면 2 다. */
    private List<String> topFoods(List<String> foodNames) {
        return foodNames.stream()
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                 .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_FOOD_COUNT)
                .map(Map.Entry::getKey)
                .toList();
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*ReportServiceTest'`
Expected: PASS (7건)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/report/service/ReportService.java \
        src/test/java/com/skinplate/api/domain/report/ReportServiceTest.java
git commit -m "feat(report): 기간 집계 · 반복 감점과 관련 음식"
```

---

## Task 6: `GET /api/v1/reports`

**Files:**
- Create: `src/main/java/com/skinplate/api/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/skinplate/api/domain/report/ReportControllerTest.java`

**Interfaces:**
- Consumes: `ReportService.get(Long, ReportPeriod)`
- Produces: `GET /api/v1/reports?period=TODAY|WEEK` → `ApiResponse<ReportResponse>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.domain.report;

import com.skinplate.api.domain.report.controller.ReportController;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.service.ReportService;
import com.skinplate.api.global.exception.GlobalExceptionHandler;
import com.skinplate.api.global.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final ReportService reportService = mock(ReportService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ReportController(reportService))
            .setCustomArgumentResolvers(new StubCurrentUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("period=WEEK 이면 주간 집계를 부르고 봉투에 담아 돌려준다")
    void weekReport() throws Exception {
        given(reportService.get(eq(1L), eq(ReportPeriod.WEEK))).willReturn(
                new ReportResponse(ReportPeriod.WEEK, LocalDate.of(2026, 8, 8),
                        LocalDate.of(2026, 8, 14), 72, List.of(), 12, 68, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports").param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recordCount").value(12))
                .andExpect(jsonPath("$.data.averagePlateScore").value(68));
    }

    @Test
    @DisplayName("period 가 없으면 400 이다")
    void missingPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("모르는 period 는 400 이다 — 500 으로 새지 않는다")
    void unknownPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reports").param("period", "MONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    /** 인증은 이 테스트의 관심사가 아니다. 필터가 넣어 주는 값만 흉내 낸다. */
    private static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }
        @Override public Object resolveArgument(MethodParameter parameter,
                ModelAndViewContainer container, NativeWebRequest request,
                WebDataBinderFactory factory) {
            return 1L;
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*ReportControllerTest'`
Expected: FAIL — `ReportController` 없음

- [ ] **Step 3: 최소 구현**

```java
package com.skinplate.api.domain.report.controller;

import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.service.ReportService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 기준일은 서버의 오늘(KST) 이다. 과거 임의 날짜는 히스토리가 맡는다.
     *
     * 모르는 period 는 Spring 이 MethodArgumentTypeMismatchException 을 던지고
     * GlobalExceptionHandler 가 400 으로 받는다.
     */
    @GetMapping
    public ApiResponse<ReportResponse> get(@CurrentUser Long userId,
                                           @RequestParam ReportPeriod period) {
        return ApiResponse.ok(reportService.get(userId, period));
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*ReportControllerTest'`
Expected: PASS (3건)

> 400 두 건은 이미 처리된다 — `GlobalExceptionHandler` 가 `MissingServletRequestParameterException`(파라미터 누락)과 `MethodArgumentTypeMismatchException`(모르는 enum·잘못된 날짜)을 함께 `INVALID_INPUT` 으로 잡는다. 핸들러를 새로 만들지 않는다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/report/controller/ReportController.java \
        src/test/java/com/skinplate/api/domain/report/ReportControllerTest.java
git commit -m "feat(report): GET /reports 조회 API"
```

---

## Task 7: 히스토리 DTO 와 그룹핑

**Files:**
- Create: `src/main/java/com/skinplate/api/domain/plate/dto/PlateHistoryResponse.java`
- Create: `src/main/java/com/skinplate/api/domain/plate/dto/PlateHistoryDayDto.java`
- Create: `src/main/java/com/skinplate/api/domain/plate/dto/PlateHistoryItemDto.java`
- Create: `src/main/java/com/skinplate/api/domain/plate/service/PlateHistoryService.java`
- Test: `src/test/java/com/skinplate/api/domain/plate/PlateHistoryServiceTest.java`

**Interfaces:**
- Consumes: `SkinPlateRepository.findInRange`, `SkinAnalysisRepository.findByUserIdAndCreatedAt...`, `DateRange`
- Produces:
  - `record PlateHistoryItemDto(Long plateId, String foodName, int plateScore, LocalDateTime recordedAt)`
  - `record PlateHistoryDayDto(LocalDate date, Integer skinScore, List<PlateHistoryItemDto> plates)`
  - `record PlateHistoryResponse(List<PlateHistoryDayDto> days)`
  - `PlateHistoryService.get(Long userId, LocalDate from, LocalDate to)` → `PlateHistoryResponse`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.domain.plate;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.dto.PlateHistoryDayDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PlateHistoryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 8, 13);
    private static final LocalDate TO = LocalDate.of(2026, 8, 14);

    private SkinPlateRepository skinPlateRepository;
    private SkinAnalysisRepository skinAnalysisRepository;
    private PlateHistoryService plateHistoryService;

    @BeforeEach
    void setUp() {
        skinPlateRepository = mock(SkinPlateRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        plateHistoryService = new PlateHistoryService(skinPlateRepository, skinAnalysisRepository);

        given(skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        anyLong(), any(), any()))
                .willReturn(List.of());
    }

    @Test
    @DisplayName("날짜 내림차순으로 묶고, 같은 날 안에서는 시각 내림차순이다")
    void groupsByDateDescending() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(3L, "샐러드", 82, LocalDateTime.of(2026, 8, 14, 19, 4)),
                plate(2L, "치킨", 71, LocalDateTime.of(2026, 8, 14, 15, 21)),
                plate(1L, "떡볶이", 65, LocalDateTime.of(2026, 8, 13, 12, 32))));

        List<PlateHistoryDayDto> days = plateHistoryService.get(USER_ID, FROM, TO).days();

        assertThat(days).extracting(PlateHistoryDayDto::date)
                .containsExactly(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 13));
        assertThat(days.get(0).plates()).extracting(PlateHistoryItemDto::foodName)
                .containsExactly("샐러드", "치킨");
    }

    @Test
    @DisplayName("그날 얼굴을 안 찍었어도 점수가 빈칸이 아니다 — 기록의 채점 기준을 쓴다")
    void fallsBackToPlateBaseline() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "떡볶이", 65, LocalDateTime.of(2026, 8, 13, 12, 32))));

        assertThat(plateHistoryService.get(USER_ID, FROM, TO).days().get(0).skinScore())
                .isEqualTo(55);
    }

    @Test
    @DisplayName("Plate 가 없는 날은 아예 나오지 않는다 — 히스토리는 식단 기록이다")
    void daysWithoutPlatesAreOmitted() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        assertThat(plateHistoryService.get(USER_ID, FROM, TO).days()).isEmpty();
    }

    @Test
    @DisplayName("from 이 to 보다 뒤면 400 이다")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> plateHistoryService.get(USER_ID, TO, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static SkinPlate plate(Long id, String foodName, int score, LocalDateTime createdAt) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식",
                Nutrition.of(500, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1800, BigDecimal.ONE),
                CookingMethod.BOILED, false, "{}");
        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");

        SkinPlate plate = SkinPlate.create(user, analysis, food, score, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", createdAt);
        return plate;
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*PlateHistoryServiceTest'`
Expected: FAIL — `PlateHistoryService` 없음

- [ ] **Step 3: DTO 세 개를 만든다**

```java
// domain/plate/dto/PlateHistoryItemDto.java
package com.skinplate.api.domain.plate.dto;

import java.time.LocalDateTime;

public record PlateHistoryItemDto(Long plateId, String foodName, int plateScore,
                                  LocalDateTime recordedAt) {}
```

```java
// domain/plate/dto/PlateHistoryDayDto.java
package com.skinplate.api.domain.plate.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param skinScore 그날 최신 분석. 없으면 그날 첫 기록이 채점 기준으로 쓴 분석의 점수.
 *                  skin_plate.skin_analysis_id 가 NOT NULL 이라 기록이 있으면 반드시 있다
 */
public record PlateHistoryDayDto(LocalDate date, Integer skinScore,
                                 List<PlateHistoryItemDto> plates) {}
```

```java
// domain/plate/dto/PlateHistoryResponse.java
package com.skinplate.api.domain.plate.dto;

import java.util.List;

/** Plate 가 하나도 없는 날은 days 에 넣지 않는다. 히스토리는 식단 기록이다. */
public record PlateHistoryResponse(List<PlateHistoryDayDto> days) {}
```

- [ ] **Step 4: 서비스를 만든다**

```java
package com.skinplate.api.domain.plate.service;

import com.skinplate.api.domain.plate.dto.PlateHistoryDayDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.global.common.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 날짜별 묶기를 서버가 한다. 앱은 받은 것을 그리기만 한다. */
@Service
@RequiredArgsConstructor
public class PlateHistoryService {

    private final SkinPlateRepository skinPlateRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;

    @Transactional(readOnly = true)
    public PlateHistoryResponse get(Long userId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.of(from, to);

        List<SkinPlate> plates =
                skinPlateRepository.findInRange(userId, range.from(), range.toExclusive());
        Map<LocalDate, Integer> skinScorePerDay = skinScorePerDay(userId, range);

        return new PlateHistoryResponse(plates.stream()
                .collect(Collectors.groupingBy(plate -> plate.getCreatedAt().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<SkinPlate>>comparingByKey().reversed())
                .map(entry -> toDay(entry.getKey(), entry.getValue(), skinScorePerDay))
                .toList());
    }

    private PlateHistoryDayDto toDay(LocalDate date, List<SkinPlate> plates,
                                     Map<LocalDate, Integer> skinScorePerDay) {
        List<SkinPlate> sorted = plates.stream()
                .sorted(Comparator.comparing(SkinPlate::getCreatedAt).reversed()
                                  .thenComparing(Comparator.comparing(SkinPlate::getId).reversed()))
                .toList();

        // 그날 얼굴을 안 찍었어도 빈칸을 두지 않는다. 모든 기록에는 채점 기준이 된
        // 분석이 반드시 있고(skin_analysis_id NOT NULL), 화면이 "-" 를 띄우면
        // 같은 기록을 상세로 열었을 때 점수가 나오는 모순이 생긴다.
        Integer skinScore = skinScorePerDay.getOrDefault(date,
                sorted.get(sorted.size() - 1).getSkinAnalysis().getSkinScore());

        return new PlateHistoryDayDto(date, skinScore, sorted.stream()
                .map(plate -> new PlateHistoryItemDto(plate.getId(),
                        plate.getFoodAnalysis().getFoodName(),
                        plate.getPlateScore(), plate.getCreatedAt()))
                .toList());
    }

    /** 그날 찍은 분석 중 가장 늦은 것. 입력이 내림차순이라 첫 등장이 최신이다. */
    private Map<LocalDate, Integer> skinScorePerDay(Long userId, DateRange range) {
        Map<LocalDate, Integer> perDay = new LinkedHashMap<>();

        for (SkinAnalysis analysis : skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, range.from(), range.toExclusive())) {
            perDay.putIfAbsent(analysis.getCreatedAt().toLocalDate(), analysis.getSkinScore());
        }
        return perDay;
    }
}
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests '*PlateHistoryServiceTest'`
Expected: PASS (4건)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/plate/dto/PlateHistory*.java \
        src/main/java/com/skinplate/api/domain/plate/service/PlateHistoryService.java \
        src/test/java/com/skinplate/api/domain/plate/PlateHistoryServiceTest.java
git commit -m "feat(plate): 날짜별 히스토리 그룹핑"
```

---

## Task 8: `GET /api/v1/plates?from=&to=`

**Files:**
- Modify: `src/main/java/com/skinplate/api/domain/plate/controller/SkinPlateController.java`
- Test: `src/test/java/com/skinplate/api/domain/plate/PlateHistoryControllerTest.java`

**Interfaces:**
- Consumes: `PlateHistoryService.get(Long, LocalDate, LocalDate)`
- Produces: `GET /api/v1/plates?from=YYYY-MM-DD&to=YYYY-MM-DD` → `ApiResponse<PlateHistoryResponse>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.skinplate.api.domain.plate;

import com.skinplate.api.domain.plate.controller.SkinPlateController;
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import com.skinplate.api.domain.plate.service.SkinPlateService;
import com.skinplate.api.global.exception.GlobalExceptionHandler;
import com.skinplate.api.global.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlateHistoryControllerTest {

    private final SkinPlateService skinPlateService = mock(SkinPlateService.class);
    private final PlateHistoryService plateHistoryService = mock(PlateHistoryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SkinPlateController(skinPlateService, plateHistoryService))
            .setCustomArgumentResolvers(new StubCurrentUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("from·to 를 날짜로 받아 히스토리를 봉투에 담아 돌려준다")
    void history() throws Exception {
        given(plateHistoryService.get(eq(1L), eq(LocalDate.of(2026, 8, 8)),
                eq(LocalDate.of(2026, 8, 14))))
                .willReturn(new PlateHistoryResponse(List.of()));

        mockMvc.perform(get("/api/v1/plates").param("from", "2026-08-08").param("to", "2026-08-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.days").isArray());
    }

    @Test
    @DisplayName("from 이 없으면 400 이다 — 전체 기록을 통째로 읽는 호출을 만들지 않는다")
    void missingFrom() throws Exception {
        mockMvc.perform(get("/api/v1/plates").param("to", "2026-08-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("날짜 형식이 아니면 400 이다")
    void malformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/plates").param("from", "2026/08/08").param("to", "2026-08-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }
        @Override public Object resolveArgument(MethodParameter parameter,
                ModelAndViewContainer container, NativeWebRequest request,
                WebDataBinderFactory factory) {
            return 1L;
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*PlateHistoryControllerTest'`
Expected: FAIL — `SkinPlateController` 생성자 인자가 하나다

- [ ] **Step 3: 컨트롤러에 엔드포인트를 더한다**

`SkinPlateController` 에 필드와 메서드를 추가한다. `@RequiredArgsConstructor` 는 **필드 선언 순서대로** 생성자 인자를 만든다 — 그래서 **기존 `skinPlateService` 아래에** 둔다. 위에 두면 테스트의 `new SkinPlateController(skinPlateService, plateHistoryService)` 가 인자 순서와 어긋난다.

```java
    private final SkinPlateService skinPlateService;      // 기존
    private final PlateHistoryService plateHistoryService; // ← 아래에 추가
```

```java
    /**
     * from·to 는 KST 달력일이고 to 도 포함한다.
     * 2026-08-08 ~ 2026-08-14 는 8월 15일 자정 직전까지다.
     *
     * 둘 다 필수다 — 없으면 한 번의 호출이 사용자의 전체 기록을 메모리로 끌어올린다.
     */
    @GetMapping
    public ApiResponse<PlateHistoryResponse> history(
            @CurrentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ApiResponse.ok(plateHistoryService.get(userId, from, to));
    }
```

import 를 더한다:

```java
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*PlateHistoryControllerTest'`
Expected: PASS (3건)

- [ ] **Step 5: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/skinplate/api/domain/plate/controller/SkinPlateController.java \
        src/test/java/com/skinplate/api/domain/plate/PlateHistoryControllerTest.java
git commit -m "feat(plate): GET /plates 날짜별 히스토리 조회"
```

---

## Task 9: 실제 HTTP 로 관통 확인

단위 테스트는 대역을 쓴다. 두 API 가 실제 DB 를 거쳐 도는지는 따로 본다.

**Files:** 없음 (수동 검증)

- [ ] **Step 1: 서버를 띄운다**

```bash
docker compose up -d postgres
set -a && source .env && set +a
./gradlew bootJar -q
PORT=18080 SPRING_PROFILES_ACTIVE=local TEST_ACCOUNT_ENABLED=true \
  JWT_SECRET="$JWT_SECRET" OPENAI_API_KEY=unused APP_AI_MOCK=true \
  java -jar build/libs/skinplate-api-0.0.1-SNAPSHOT.jar &
sleep 20 && curl -s localhost:18080/api/v1/health
```

- [ ] **Step 2: 기준선을 적어 두고 기록을 두 건 만든다**

개발 DB 에는 이미 기록이 쌓여 있다. **절대값이 아니라 증분으로 확인한다.**

```bash
docker exec -i skinplate-db psql -U skinplate -d skinplate -tAc \
  "select count(*) from skin_plate where user_id=1;"   # ← 이 값을 적어 둔다
```

```bash
T=$(curl -s -X POST localhost:18080/api/v1/auth/test-login \
      -H 'Content-Type: application/json' -d '{}' \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
printf '\xff\xd8\xff\xe0' > /tmp/e2e.jpg && head -c 2048 /dev/zero >> /tmp/e2e.jpg

curl -s -X POST localhost:18080/api/v1/skin/analyses -H "Authorization: Bearer $T" \
  -F "front=@/tmp/e2e.jpg" -F "left=@/tmp/e2e.jpg" -F "right=@/tmp/e2e.jpg" > /dev/null
curl -s -X POST localhost:18080/api/v1/plates -H "Authorization: Bearer $T" \
  -F "image=@/tmp/e2e.jpg" > /dev/null
curl -s -X POST localhost:18080/api/v1/plates -H "Authorization: Bearer $T" \
  -F "image=@/tmp/e2e.jpg" > /dev/null
```

- [ ] **Step 3: 리포트를 확인한다**

```bash
curl -s "localhost:18080/api/v1/reports?period=TODAY" -H "Authorization: Bearer $T" | python3 -m json.tool
curl -s "localhost:18080/api/v1/reports?period=WEEK"  -H "Authorization: Bearer $T" | python3 -m json.tool
```

Expected — TODAY: `recordCount` 가 **Step 2 기준선보다 2 늘어 있다**. `meals` 에 방금 만든 두 건이 있고 각 `plateScore` 는 **60**, `skinScoreTrend` 는 `[]`.
WEEK: `skinScoreTrend` 에 점이 있고, `meals` 는 `[]`, `penalties` 라벨이 `나트륨 과다`·`매운맛 자극` 로 보인다.
**두 응답 모두 `latestSkinScore` 가 55 다** (Mock 지표 38/52/64/25/78).

절대값(평균·총 개수)은 기존 데이터에 좌우되므로 보지 않는다. **건별 값(60점·55점·라벨)이 검증 대상이다.**

- [ ] **Step 4: 히스토리를 확인한다**

```bash
TODAY=$(TZ=Asia/Seoul date +%F)
curl -s "localhost:18080/api/v1/plates?from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $T" | python3 -m json.tool
```

Expected: `days` 에 오늘이 있고, 그 안 `plates` 맨 앞 두 건이 방금 만든 것(점수 60), `skinScore` 55, 시각 내림차순.

- [ ] **Step 5: 경계와 오류를 확인한다**

```bash
curl -s -o /dev/null -w "reversed=%{http_code}\n" \
  "localhost:18080/api/v1/plates?from=2026-08-14&to=2026-08-08" -H "Authorization: Bearer $T"
curl -s -o /dev/null -w "toowide=%{http_code}\n" \
  "localhost:18080/api/v1/plates?from=2026-01-01&to=2026-08-14" -H "Authorization: Bearer $T"
curl -s -o /dev/null -w "badperiod=%{http_code}\n" \
  "localhost:18080/api/v1/reports?period=MONTH" -H "Authorization: Bearer $T"
curl -s -o /dev/null -w "noauth=%{http_code}\n" "localhost:18080/api/v1/reports?period=TODAY"
```

Expected: `reversed=400` · `toowide=400` · `badperiod=400` · `noauth=401`

- [ ] **Step 6: 저장 시각이 KST 인지 확인한다**

```bash
docker exec -i skinplate-db psql -U skinplate -d skinplate -tAc \
  "select created_at from skin_plate order by id desc limit 1;"
TZ=Asia/Seoul date "+지금(KST) %F %T"
```

Expected: 두 값이 몇 초 안쪽으로 같다. UTC 로 저장됐다면 9시간 차이가 난다.

- [ ] **Step 7: 서버를 정리한다**

```bash
pkill -f "skinplate-api-0.0.1-SNAPSHOT.jar"; rm -f /tmp/e2e.jpg
```

---

## Task 10: 문서 동기화

`PRD` 와 설계서가 이 기능을 범위 밖으로 적어 두고 있다. 코드가 문서와 어긋나면 그 자리에서 문서를 고친다(CLAUDE.md).

**Files:**
- Modify: `SkinPlate_PRD.md`
- Modify: `SkinPlate_DTO_Domain.md`

- [ ] **Step 1: PRD §14.2 엔드포인트 표에 두 줄을 넣는다**

```markdown
| 13 | GET | `/plates?from=&to=` | ✅ | 날짜별 식단 기록 (히스토리) | P1 |
| 14 | GET | `/reports?period=` | ✅ | 오늘·이번 주 리포트 | P1 |
```

- [ ] **Step 2: 축소 목록에서 히스토리를 뺀다**

`GET /plates?date=` · `S09 히스토리` 가 제외 목록에 남아 있는 곳을 찾아 고친다. **계획은 `?date=` 였지만 구현은 `?from=&to=` 범위 조회다.**

```bash
grep -n "S09\|히스토리\|plates?date=" SkinPlate_PRD.md SkinPlate_DTO_Domain.md
```

**여러 곳에 흩어져 있다** — PRD 의 화면 정의(S09 P2)·축소 목록·P2 표·G3 컷 목록, 설계서의 제외 목록. 각 줄에서 히스토리 항목만 빼고, 남은 제외 항목(결과 공유·지표 추이 차트 등)은 그대로 둔다.

- [ ] **Step 3: 설계서 계약 대조표에 두 줄을 넣는다**

```bash
grep -n "GET /skin/analyses/latest" SkinPlate_DTO_Domain.md
```

그 표에 아래를 더한다.

```markdown
| `GET /plates?from=&to=` | `PlateHistoryResponse` | `days[{date, skinScore, plates[{plateId, foodName, plateScore, recordedAt}]}]` | `PlateHistoryDto` |
| `GET /reports?period=` | `ReportResponse` | `period` · `from` · `to` · `latestSkinScore` · `skinScoreTrend[]` · `recordCount` · `averagePlateScore` · `penalties[]` · `meals[]` | `ReportDto` |
```

- [ ] **Step 4: 스펙의 `attributePaths` 를 코드에 맞춘다**

`docs/superpowers/specs/2026-08-14-history-and-reports-design.md` §8 의 스니펫이 두 개(`feedbacks`, `foodAnalysis`)로 적혀 있는데 구현은 세 개다 — `PlateHistoryService` 가 `getSkinAnalysis().getSkinScore()` 를 읽는다. `"skinAnalysis"` 를 더한다.

- [ ] **Step 5: 커밋**

```bash
git add SkinPlate_PRD.md SkinPlate_DTO_Domain.md docs/superpowers/specs/
git commit -m "docs(spec): 히스토리·리포트 엔드포인트를 명세에 반영"
```

---

## Task 11: PR

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — 총 108건 (기존 83 + 신규 25: JpaConfig 1 · DateRange 7 · ReportService 7 · ReportController 3 · PlateHistoryService 4 · PlateHistoryController 3)

- [ ] **Step 2: 푸시하고 PR 을 연다**

```bash
git push -u origin feat/history-and-reports
gh pr create --base develop --title "feat: 히스토리 · 오늘의 기록 · 리포트 조회 API"
```

PR 본문은 `🚀 작업 내용 / 🤔 고민했던 내용 / 💬 리뷰 중점사항` 세 절로, 파일·클래스명 나열 없이 자연스러운 문장으로 쓴다. 저장 시각을 KST 로 고정한 이유(환경마다 다른 시간대로 쓰이던 문제)를 반드시 담는다.

---

## 실행 순서와 중단 기준

Task 1~2 는 나머지 전부의 토대다. **Task 1 을 건너뛰고 3 이후로 가지 않는다** — 배포 환경에서 날짜가 아홉 시간 어긋난 채로 전부 통과한다.

| 순서 | 누적 시간 | 여기서 멈춰도 되는가 |
|---|---|---|
| 1 → 2 → 3 | ~1h | 아직 쓸 수 없다 |
| 4 → 5 → 6 | ~2.5h | **된다.** 홈 카드와 리포트가 돈다 |
| 7 → 8 | ~3.5h | 된다. 히스토리까지 |
| 9 → 10 → 11 | ~4h | 완료 |

**P0 가 밀리면 Task 6 뒤에서 멈춘다.** 홈 "오늘의 기록" 카드가 리포트 API 하나로 돌아가므로, 거기까지만 있어도 화면 하나는 산다.
