package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.DailyScoreDto;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.report.dto.WeeklyCommentDto;
import com.skinplate.api.domain.report.dto.WeeklyReportResponse;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.global.common.DateRange;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.prompt.WeeklyReportPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 주간 리포트. <b>일일 리포트만 보고 만든다</b> — 원본 기록을 다시 훑지 않는다.
 *
 * <p>이 규칙 하나가 "화면마다 숫자가 다르다"를 구조적으로 막는다. 하루 점수의 정의는
 * {@link DailyReportAssembler} 한 곳에만 있고, 주간은 그 결과를 평균·최대·최소할 뿐이다.
 * 월간도 범위만 넓혀 같은 코드를 타면 된다.
 *
 * <p><b>기록이 없는 날은 애초에 목록에 없다.</b> 0 점으로 채워 평균을 끌어내리지 않는다 —
 * {@link DailyReportService#getRange} 가 기록이 있는 날만 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    /** 기본 조회 폭. 오늘을 포함한 7일이다(리포트 토글의 "이번 주"와 같은 정의). */
    private static final int DEFAULT_DAYS = 7;

    /** AI 에게 넘길 반복 항목 수. 늘리면 프롬프트가 목록이 되고 문장이 산만해진다. */
    private static final int TOP_COUNT = 3;

    /**
     * 문장 캐시 크기. 한 사용자가 오늘·지난주·지난달을 오가도 몇 벌이면 충분하고,
     * 프롬프트가 1KB 남짓이라 64벌이어도 메모리는 무시할 수준이다.
     */
    private static final int COMMENT_CACHE_SIZE = 64;

    private final DailyReportService dailyReportService;
    private final VisionClient visionClient;

    /**
     * <b>키가 프롬프트 자체다.</b> 기록이 하나라도 늘거나 기간이 달라지면 키가 달라져
     * 새로 생성되고, 같은 집계를 다시 열면 호출이 없다 — 무효화 조건이 "데이터가
     * 바뀌었을 때"와 정확히 일치하므로 시간 기반 TTL 이 필요 없다.
     *
     * <p>userId 를 키에 함께 둔다. 프롬프트에는 개인 식별 정보가 없어서 두 사용자의
     * 집계가 우연히 같아질 수 있는데, 그때 남의 문장을 돌려주면 안 된다.
     *
     * <p>ponytail: 프로세스 안의 LRU 다(JDK LinkedHashMap 의 accessOrder). 재기동하면
     * 비고 인스턴스가 여러 대면 각자 갖는다 — 현재 배포는 VM 1대라 그것으로 충분하다.
     * 여러 대로 늘어나면 그때 공용 저장소를 넣는다.
     */
    private final Map<CommentKey, WeeklyCommentDto> commentCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CommentKey, WeeklyCommentDto> eldest) {
                    return size() > COMMENT_CACHE_SIZE;
                }
            });

    private record CommentKey(Long userId, String userContext) {}

    /**
     * from·to 는 KST 달력일이고 to 도 포함한다. 둘 다 생략하면 오늘 포함 최근 7일이다.
     * 하나만 주면 {@link DateRange#of} 가 400 으로 막는다 — 반쪽 기간을 임의로 메우면
     * 사용자가 보고 있는 기간과 응답의 기간이 달라진다.
     *
     * <p><b>트랜잭션을 열지 않는다.</b> 조회는 dailyReportService 안에서 끝나고, 그 뒤의
     * AI 호출은 트랜잭션 밖이어야 한다 — 응답을 기다리는 내내 커넥션을 쥐고 있게 된다.
     */
    public WeeklyReportResponse get(Long userId, LocalDate from, LocalDate to) {
        DateRange range = (from == null && to == null)
                ? DateRange.lastDays(DEFAULT_DAYS)
                : DateRange.of(from, to);

        List<DailyReportResponse> days = dailyReportService.getRange(userId, range);
        WeeklyReportResponse report = aggregate(range, days);

        // 기록이 하나도 없으면 문장을 만들 재료가 없다. 부르지 않는 편이 낫다 —
        // 빈 표를 넘기면 모델이 없는 한 주를 지어낸다.
        return days.isEmpty() ? report : report.withAiComment(comment(userId, report, days));
    }

    // ---- 집계 ----

    private static WeeklyReportResponse aggregate(DateRange range, List<DailyReportResponse> days) {
        int totalDays = (int) ChronoUnit.DAYS.between(range.fromDate(), range.toDate()) + 1;

        if (days.isEmpty()) {
            return new WeeklyReportResponse(range.fromDate(), range.toDate(), null, null,
                    totalDays, 0, 0, List.of(), List.of(), List.of(), null, null, null);
        }

        List<DailyScoreDto> dailyScores = days.stream()
                .map(day -> DailyScoreDto.of(day.date(), day.dailyScore()))
                .toList();

        // 분모는 totalDays 가 아니라 기록이 있는 날 수다. 이 한 줄이 §6 의 규칙이다.
        int average = (int) Math.round(dailyScores.stream()
                .mapToInt(DailyScoreDto::dailyScore).average().orElseThrow());

        return new WeeklyReportResponse(
                range.fromDate(), range.toDate(),
                average, SkinLevel.of(average),
                totalDays, days.size(),
                days.stream().mapToInt(DailyReportResponse::recordCount).sum(),
                dailyScores,
                nutrition(days),
                concerns(days),
                bestDay(dailyScores), worstDay(dailyScores),
                null);
    }

    /**
     * 동점이면 <b>이른 날짜</b>가 이긴다. best 와 worst 가 같은 규칙을 쓴다 —
     * 한쪽만 늦은 날짜로 두면 하루만 기록한 주에서 두 카드가 같은 날을 가리키는데도
     * 규칙을 설명할 수 없게 된다. (ReportService 의 결정적 정렬과 같은 원칙)
     */
    private static DailyScoreDto bestDay(List<DailyScoreDto> scores) {
        return scores.stream()
                .max(Comparator.comparingInt(DailyScoreDto::dailyScore)
                        .thenComparing(DailyScoreDto::date, Comparator.reverseOrder()))
                .orElseThrow();
    }

    private static DailyScoreDto worstDay(List<DailyScoreDto> scores) {
        return scores.stream()
                .min(Comparator.comparingInt(DailyScoreDto::dailyScore)
                        .thenComparing(DailyScoreDto::date))
                .orElseThrow();
    }

    /**
     * 기록이 있는 날의 <b>하루 평균</b>이다. 주간 합계가 아니다 — 항목마다 하루 기준값이
     * 붙어 있어서, 합계를 넣으면 5일치 나트륨이 기준의 500% 로 뜬다.
     */
    private static List<NutritionItemDto> nutrition(List<DailyReportResponse> days) {
        return Arrays.stream(NutrientType.values())
                .map(type -> NutritionItemDto.of(type, average(days.stream()
                        .map(day -> amountOf(day, type))
                        .toList())))
                .toList();
    }

    /** 기록이 있는 날은 6개 항목을 모두 갖는다. 못 찾으면 0 으로 두어 평균만 낮춘다. */
    private static BigDecimal amountOf(DailyReportResponse day, NutrientType type) {
        return day.nutrition().stream()
                .filter(item -> item.nutrient() == type)
                .map(NutritionItemDto::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 고민별 기간 평균과 변화. 목록은 사용자의 고민이라 모든 날이 같은 구성이므로
     * 첫날의 목록을 기준으로 돈다.
     *
     * <p>변화는 <b>기록이 있는 첫날 대비 마지막 날</b>이다. 지난주를 한 번 더 조회하지
     * 않는다 — 쿼리 한 번을 아끼려는 게 아니라, 지난주에 기록이 없으면 변화가 통째로
     * 사라져서 "이번 주 안에서의 흐름"조차 못 보여주기 때문이다. 기록일이 하루뿐이면 null 이다.
     */
    private static List<ConcernScoreDto> concerns(List<DailyReportResponse> days) {
        List<ConcernScoreDto> first = days.get(0).concerns();
        List<ConcernScoreDto> last = days.get(days.size() - 1).concerns();

        return first.stream()
                .map(base -> {
                    List<Integer> scores = days.stream()
                            .flatMap(day -> day.concerns().stream())
                            .filter(item -> item.concern() == base.concern())
                            .map(ConcernScoreDto::score)
                            .toList();

                    int average = (int) Math.round(
                            scores.stream().mapToInt(Integer::intValue).average().orElseThrow());

                    Integer changeFromFirstDay = days.size() < 2 ? null
                            : scoreOf(last, base.concern()) - base.score();

                    return ConcernScoreDto.of(base.concern(), average, changeFromFirstDay);
                })
                .toList();
    }

    private static int scoreOf(List<ConcernScoreDto> concerns, SkinConcern concern) {
        return concerns.stream()
                .filter(item -> item.concern() == concern)
                .mapToInt(ConcernScoreDto::score)
                .findFirst()
                .orElse(0);
    }

    // ---- AI 문장 ----

    /**
     * 집계된 표만 넘긴다. 한 주치 음식 기록 원문은 보내지 않는다(PRD §18.9 의 경계 —
     * 판단은 규칙, 문장만 AI).
     *
     * <p>어떤 예외든 null 로 삼킨다. 문장 생성이 리포트를 막는 순간 "AI 없이도 도는
     * 제품"이라는 전제가 무너진다. (SkinPlateService.generateCommentsSafely 와 같은 방식)
     *
     * <p>같은 집계를 다시 열면 호출하지 않는다 — 리포트는 하루에도 여러 번 열리는
     * 화면이고, 그때마다 부르면 무료 한도(하루 50)가 QA 중에 사라진다.
     * <b>실패는 캐시하지 않는다</b> — 캐시하면 일시적인 장애가 그 집계에 영구히 박힌다.
     */
    private WeeklyCommentDto comment(Long userId, WeeklyReportResponse report,
                                  List<DailyReportResponse> days) {
        String userContext = WeeklyReportPrompt.user(report,
                top(days.stream().flatMap(day -> day.goodPoints().stream())),
                top(days.stream().flatMap(day -> day.improvePoints().stream())),
                top(days.stream().flatMap(day -> day.meals().stream()
                        .map(PlateHistoryItemDto::foodName))));

        CommentKey key = new CommentKey(userId, userContext);
        WeeklyCommentDto cached = commentCache.get(key);
        if (cached != null) return cached;

        try {
            WeeklyCommentDto generated =
                    WeeklyCommentDto.from(visionClient.generateWeeklyComment(userContext));

            // null 은 캐시하지 않는다. WebClient 의 block() 은 빈 응답에서 예외 없이
            // null 을 주는데, 그걸 넣어 두면 LRU 한 칸을 먹고 살아 있는 문장을 밀어내면서
            // 정작 다음 조회는 캐시 미스라 또 부른다 — 값은 0 인데 비용만 든다.
            if (generated != null) commentCache.put(key, generated);
            return generated;
        } catch (Exception e) {
            log.warn("AI 주간 코멘트 생성 실패 — 문장 없이 응답한다", e);
            return null;
        }
    }

    /** 일일이 "자주 붙은 문구"를 세는 것과 같은 규칙이다. 정렬을 두 벌 두지 않는다. */
    private static List<Map.Entry<String, Long>> top(Stream<String> values) {
        return DailyReportAssembler.topCounts(values, TOP_COUNT);
    }
}
