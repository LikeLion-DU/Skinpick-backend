package com.skinplate.api.domain.report;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.entity.MealType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.DailyScoreDto;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.report.dto.WeeklyReportResponse;
import com.skinplate.api.domain.report.service.DailyReportService;
import com.skinplate.api.domain.report.service.WeeklyReportService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.common.DateRange;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.WeeklyComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 주간 리포트는 <b>일일 리포트만</b> 보고 만든다. 원본 기록을 다시 훑지 않고,
 * 기록이 없는 날을 0 점으로 세지 않는다.
 */
class WeeklyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    private DailyReportService dailyReportService;
    private VisionClient visionClient;
    private WeeklyReportService weeklyReportService;

    @BeforeEach
    void setUp() {
        dailyReportService = mock(DailyReportService.class);
        visionClient = mock(VisionClient.class);
        weeklyReportService = new WeeklyReportService(dailyReportService, visionClient);

        given(visionClient.generateWeeklyComment(anyString()))
                .willReturn(new WeeklyComment("잘한 점", "개선할 점", "습관", "다음 주"));
    }

    @Test
    @DisplayName("7일 모두 기록하면 7일 평균이다")
    void allSevenDays() {
        givenDays(day(MONDAY, 72), day(MONDAY.plusDays(1), 81), day(MONDAY.plusDays(2), 76),
                day(MONDAY.plusDays(3), 88), day(MONDAY.plusDays(4), 85),
                day(MONDAY.plusDays(5), 70), day(MONDAY.plusDays(6), 82));

        WeeklyReportResponse report = weekly();

        // (72+81+76+88+85+70+82) / 7 = 79.14 → 79
        assertThat(report.averageDailyScore()).isEqualTo(79);
        assertThat(report.grade()).isEqualTo(SkinLevel.GOOD);
        assertThat(report.totalDays()).isEqualTo(7);
        assertThat(report.recordedDays()).isEqualTo(7);
        assertThat(report.dailyScores()).hasSize(7);
    }

    @Test
    @DisplayName("기록이 없는 날은 0 점으로 세지 않는다 — 분모는 기록한 날 수다")
    void missingDaysAreNotZero() {
        givenDays(day(MONDAY, 80), day(MONDAY.plusDays(2), 90), day(MONDAY.plusDays(4), 70));

        WeeklyReportResponse report = weekly();

        // (80 + 90 + 70) / 3 = 80. 7 로 나눴다면 34 다.
        assertThat(report.averageDailyScore()).isEqualTo(80);
        assertThat(report.recordedDays()).isEqualTo(3);
        assertThat(report.totalDays()).isEqualTo(7);
        assertThat(report.dailyScores()).extracting(DailyScoreDto::date)
                .containsExactly(MONDAY, MONDAY.plusDays(2), MONDAY.plusDays(4));
    }

    @Test
    @DisplayName("기록이 전혀 없으면 평균은 null 이고 AI 도 부르지 않는다")
    void emptyPeriod() {
        givenDays();

        WeeklyReportResponse report = weekly();

        assertThat(report.averageDailyScore()).isNull();
        assertThat(report.grade()).isNull();
        assertThat(report.recordedDays()).isZero();
        assertThat(report.recordCount()).isZero();
        assertThat(report.dailyScores()).isEmpty();
        assertThat(report.nutrition()).isEmpty();
        assertThat(report.bestDay()).isNull();
        assertThat(report.worstDay()).isNull();
        assertThat(report.aiComment()).isNull();
        verify(visionClient, never()).generateWeeklyComment(anyString());
    }

    @Test
    @DisplayName("BEST DAY 와 WORST DAY 를 뽑는다")
    void bestAndWorstDay() {
        givenDays(day(MONDAY, 72), day(MONDAY.plusDays(1), 88), day(MONDAY.plusDays(2), 60));

        WeeklyReportResponse report = weekly();

        assertThat(report.bestDay().date()).isEqualTo(MONDAY.plusDays(1));
        assertThat(report.bestDay().dailyScore()).isEqualTo(88);
        assertThat(report.bestDay().grade()).isEqualTo(SkinLevel.EXCELLENT);
        assertThat(report.worstDay().date()).isEqualTo(MONDAY.plusDays(2));
        assertThat(report.worstDay().dailyScore()).isEqualTo(60);
    }

    @Test
    @DisplayName("동점이면 이른 날짜가 이긴다 — best 와 worst 가 같은 규칙을 쓴다")
    void tieGoesToEarlierDay() {
        givenDays(day(MONDAY, 80), day(MONDAY.plusDays(1), 60),
                day(MONDAY.plusDays(2), 80), day(MONDAY.plusDays(3), 60));

        WeeklyReportResponse report = weekly();

        assertThat(report.bestDay().date()).isEqualTo(MONDAY);
        assertThat(report.worstDay().date()).isEqualTo(MONDAY.plusDays(1));
    }

    @Test
    @DisplayName("하루만 기록해도 그날이 best 이자 worst 다")
    void singleDay() {
        givenDays(day(MONDAY, 72));

        WeeklyReportResponse report = weekly();

        assertThat(report.averageDailyScore()).isEqualTo(72);
        assertThat(report.bestDay().date()).isEqualTo(MONDAY);
        assertThat(report.worstDay().date()).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("주간 영양은 기록한 날의 하루 평균이다 — 기록 없는 날이 분모에 들어가지 않는다")
    void nutritionAverageExcludesEmptyDays() {
        // 7일 기간인데 기록은 이틀뿐이다. 1000 + 2000 → 1500 이어야 한다.
        // 합계(3000)도, 기간 전체로 나눈 값(429)도 아니다 — 후자면 "칼로리 21%" 라는 거짓을 그린다.
        givenDays(dayWithCalories(MONDAY, 72, 1000),
                dayWithCalories(MONDAY.plusDays(4), 72, 2000));

        WeeklyReportResponse report = weekly();

        assertThat(report.totalDays()).isEqualTo(7);
        assertThat(report.recordedDays()).isEqualTo(2);
        assertThat(report.nutrition()).hasSize(6);
        NutritionItemDto calories = report.nutrition().get(0);
        assertThat(calories.nutrient()).isEqualTo(NutrientType.CALORIES);
        assertThat(calories.amount()).isEqualByComparingTo("1500.0");
        assertThat(calories.percent()).isEqualTo(75);
        assertThat(calories.status()).isEqualTo(NutrientType.Status.NORMAL);
    }

    @Test
    @DisplayName("같은 집계를 다시 조회하면 AI 를 다시 부르지 않는다")
    void sameAggregateReusesComment() {
        givenDays(day(MONDAY, 72), day(MONDAY.plusDays(1), 88));

        WeeklyReportResponse first = weekly();
        WeeklyReportResponse second = weekly();

        assertThat(second.aiComment()).isEqualTo(first.aiComment());
        verify(visionClient, times(1)).generateWeeklyComment(anyString());
    }

    @Test
    @DisplayName("기록이 하나 늘면 문장을 다시 만든다 — 캐시가 옛 한 주를 붙들지 않는다")
    void newRecordRegeneratesComment() {
        givenDays(day(MONDAY, 72));
        weekly();

        givenDays(day(MONDAY, 72), day(MONDAY.plusDays(1), 88));
        weekly();

        verify(visionClient, times(2)).generateWeeklyComment(anyString());
    }

    @Test
    @DisplayName("집계가 같아도 사용자가 다르면 문장을 공유하지 않는다")
    void cacheIsNotSharedAcrossUsers() {
        givenDays(day(MONDAY, 72));

        weeklyReportService.get(USER_ID, MONDAY, MONDAY.plusDays(6));
        weeklyReportService.get(2L, MONDAY, MONDAY.plusDays(6));

        verify(visionClient, times(2)).generateWeeklyComment(anyString());
    }

    @Test
    @DisplayName("AI 실패는 삼키고 캐시하지도 않는다 — 숫자는 그대로 나오고 다음 조회에서 다시 시도한다")
    void failureIsSwallowedAndNotCached() {
        givenDays(day(MONDAY, 72));
        given(visionClient.generateWeeklyComment(anyString()))
                .willThrow(new IllegalStateException("OpenAI 장애"))
                .willReturn(new WeeklyComment("잘한 점", "개선할 점", "습관", "다음 주"));

        WeeklyReportResponse failed = weekly();
        assertThat(failed.aiComment()).isNull();
        assertThat(failed.averageDailyScore()).isEqualTo(72);   // 문장이 없어도 리포트는 온다

        assertThat(weekly().aiComment()).isNotNull();
        verify(visionClient, times(2)).generateWeeklyComment(anyString());
    }

    @Test
    @DisplayName("고민별 주간 점수는 일일 점수의 평균이고, 변화는 첫 기록일 대비다")
    void concernAverageAndChange() {
        givenDays(dayWithConcern(MONDAY, 72, SkinConcern.PUFFINESS, 54),
                dayWithConcern(MONDAY.plusDays(2), 80, SkinConcern.PUFFINESS, 62),
                dayWithConcern(MONDAY.plusDays(4), 84, SkinConcern.PUFFINESS, 70));

        List<ConcernScoreDto> concerns = weekly().concerns();

        assertThat(concerns).hasSize(1);
        assertThat(concerns.get(0).concern()).isEqualTo(SkinConcern.PUFFINESS);
        assertThat(concerns.get(0).score()).isEqualTo(62);          // (54+62+70)/3
        assertThat(concerns.get(0).status()).isEqualTo(SkinLevel.GOOD);
        assertThat(concerns.get(0).change()).isEqualTo(16);         // 70 - 54
    }

    @Test
    @DisplayName("기록일이 하루뿐이면 고민 변화는 null 이다 — 비교할 상대가 없다")
    void noChangeWithSingleDay() {
        givenDays(dayWithConcern(MONDAY, 72, SkinConcern.PUFFINESS, 54));

        assertThat(weekly().concerns().get(0).change()).isNull();
    }

    @Test
    @DisplayName("총 기록 수는 날마다의 기록 수를 더한 값이다")
    void recordCountIsSummed() {
        givenDays(day(MONDAY, 72, 3), day(MONDAY.plusDays(1), 80, 2));

        assertThat(weekly().recordCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("from·to 를 생략하면 오늘 포함 최근 7일을 조회한다")
    void defaultRangeIsLastSevenDays() {
        given(dailyReportService.getRange(anyLong(), any())).willReturn(List.of());

        WeeklyReportResponse report = weeklyReportService.get(USER_ID, null, null);

        LocalDate today = LocalDate.now(DateRange.KST);
        assertThat(report.from()).isEqualTo(today.minusDays(6));
        assertThat(report.to()).isEqualTo(today);
        assertThat(report.totalDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("과거 기간도 그대로 조회된다 — 응답의 from·to 가 요청한 기간이다")
    void pastRange() {
        givenDays(day(MONDAY, 72));

        WeeklyReportResponse report = weekly();

        assertThat(report.from()).isEqualTo(MONDAY);
        assertThat(report.to()).isEqualTo(MONDAY.plusDays(6));
    }

    @Test
    @DisplayName("from 만 주면 400 이다 — 반쪽 기간을 서버가 임의로 메우지 않는다")
    void halfRangeIsRejected() {
        assertThatThrownBy(() -> weeklyReportService.get(USER_ID, MONDAY, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("AI 입력은 서버가 집계한 표다 — 평균·BEST DAY·영양이 프롬프트에 실린다")
    void aiSeesAggregatedTable() {
        givenDays(day(MONDAY, 72), day(MONDAY.plusDays(1), 88));

        weekly();

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(visionClient).generateWeeklyComment(context.capture());

        assertThat(context.getValue())
                .contains("평균 80")
                .contains("가장 높은 날 " + MONDAY.plusDays(1))
                .contains("[하루 평균 영양]");
    }

    /**
     * 실제 {@link DailyReportService} 를 붙여 두 서비스의 이음매를 본다.
     *
     * <p>나머지 케이스는 일일을 목으로 세워 집계 규칙만 검증한다. 그러면 주간이 기대는
     * 두 전제 — 영양 금액이 소수 첫째 자리 BigDecimal 이라는 것, 모든 날의 고민 목록
     * 구성이 같아서 {@code scoreOf} 의 폴백이 도달 불가라는 것 — 을 아무도 확인하지 않는다.
     */
    @Test
    @DisplayName("실제 일일 리포트를 그대로 집계한다 — 목이 가린 전제를 여기서 본다")
    void aggregatesRealDailyReports() {
        SkinPlateRepository plateRepository = mock(SkinPlateRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        user.updateSkinConcerns(Set.of(SkinConcern.PUFFINESS));
        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));

        // 월 1끼(60점) · 수 2끼(80·70점). 끼니마다 나트륨 1800mg 에 R04 −8 이 붙어 있다.
        given(plateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, 60, MONDAY.atTime(12, 0)),
                plate(2L, 80, MONDAY.plusDays(2).atTime(12, 0)),
                plate(3L, 70, MONDAY.plusDays(2).atTime(19, 0))));

        WeeklyReportResponse report = new WeeklyReportService(
                new DailyReportService(plateRepository, userRepository), visionClient)
                .get(USER_ID, MONDAY, MONDAY.plusDays(6));

        // 하루 점수 60 과 75(=round(80·70 평균)) 의 평균 → round(67.5) = 68
        assertThat(report.averageDailyScore()).isEqualTo(68);
        assertThat(report.recordedDays()).isEqualTo(2);
        assertThat(report.recordCount()).isEqualTo(3);
        assertThat(report.bestDay().date()).isEqualTo(MONDAY.plusDays(2));
        assertThat(report.worstDay().date()).isEqualTo(MONDAY);

        // 나트륨 하루 합계는 월 1800 · 수 3600 이고, 그 평균이 2700 이다(기준 2000 의 135%).
        NutritionItemDto sodium = report.nutrition().stream()
                .filter(item -> item.nutrient() == NutrientType.SODIUM).findFirst().orElseThrow();
        assertThat(sodium.amount()).isEqualByComparingTo("2700.0");
        assertThat(sodium.percent()).isEqualTo(135);
        assertThat(sodium.status()).isEqualTo(NutrientType.Status.HIGH);

        assertThat(report.concerns()).hasSize(1);
        assertThat(report.concerns().get(0).concern()).isEqualTo(SkinConcern.PUFFINESS);
        assertThat(report.concerns().get(0).score()).isEqualTo(62);   // 70 − 8, 이틀 모두
        assertThat(report.concerns().get(0).change()).isZero();
    }

    // ---- 픽스처 ----

    /** 나트륨 1800mg · R04 −8 이 붙은 한 끼. 이음매 테스트에서만 쓴다. */
    private static SkinPlate plate(Long id, int score, LocalDateTime createdAt) {
        AppUser owner = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(owner, "김치찌개", "한식",
                Nutrition.of(500, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1800, BigDecimal.ONE),
                CookingMethod.BOILED, false, "{}");
        SkinAnalysis analysis = SkinAnalysis.create(
                owner, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");

        SkinPlate plate = SkinPlate.create(owner, analysis, food, score, "요약", "[]");
        plate.addFeedback(SkinPlateFeedback.caution("R04", "나트륨 과다", -8, 0));
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", createdAt);
        return plate;
    }

    private WeeklyReportResponse weekly() {
        return weeklyReportService.get(USER_ID, MONDAY, MONDAY.plusDays(6));
    }

    private void givenDays(DailyReportResponse... days) {
        given(dailyReportService.getRange(anyLong(), any())).willReturn(List.of(days));
    }

    private static DailyReportResponse day(LocalDate date, int score) {
        return day(date, score, 1);
    }

    private static DailyReportResponse day(LocalDate date, int score, int recordCount) {
        return build(date, score, recordCount, 1500, List.of());
    }

    private static DailyReportResponse dayWithCalories(LocalDate date, int score, int calories) {
        return build(date, score, 1, calories, List.of());
    }

    private static DailyReportResponse dayWithConcern(LocalDate date, int score,
                                                      SkinConcern concern, int concernScore) {
        return build(date, score, 1, 1500,
                List.of(ConcernScoreDto.of(concern, concernScore, null)));
    }

    private static DailyReportResponse build(LocalDate date, int score, int recordCount,
                                             int calories, List<ConcernScoreDto> concerns) {
        List<NutritionItemDto> nutrition = new ArrayList<>();
        for (NutrientType type : NutrientType.values()) {
            nutrition.add(NutritionItemDto.of(type, type == NutrientType.CALORIES
                    ? BigDecimal.valueOf(calories)
                    : BigDecimal.ZERO));
        }

        return new DailyReportResponse(date, score, SkinLevel.of(score), recordCount,
                nutrition, concerns,
                List.of(new PlateHistoryItemDto(1L, "김치찌개", score,
                        MealType.LUNCH, date.atTime(12, 0))),
                "오늘의 코멘트", List.of("발효식품 포함"), List.of("나트륨 과다"));
    }
}
