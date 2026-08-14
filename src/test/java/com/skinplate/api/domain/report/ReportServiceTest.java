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
    @DisplayName("반복 횟수가 같으면 감점 합계가 더 큰 쪽이 앞선다")
    void penaltiesTieBreakByTotalDelta() {
        // 두 룰 다 count=2 로 묶어 1차 비교(count)를 무력화한다 —
        // 그래야 totalDelta 비교가 실제로 동작해야만 순서가 맞는다.
        SkinPlate first = plate(1L, "떡볶이", 60, LocalDateTime.now());
        addFeedback(first, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(first, FeedbackType.CAUTION, "R02", -12, "매운맛 자극");
        SkinPlate second = plate(2L, "라면", 55, LocalDateTime.now());
        addFeedback(second, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(second, FeedbackType.CAUTION, "R02", -12, "매운맛 자극");
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(first, second));

        // R04 합계 -16, R02 합계 -24 — 더 큰 감점(R02)이 앞서야 한다.
        assertThat(reportService.get(USER_ID, ReportPeriod.WEEK).penalties())
                .extracting(PenaltyDto::ruleCode)
                .containsExactly("R02", "R04");
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
        // analyses 를 비워두면 TODAY 가 week 분기를 잘못 타도 추이가 어차피 비어 통과한다.
        // 채워서 넣어야 "추이는 비운다"는 주장이 실제로 분기를 검증한다.
        given(skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        anyLong(), any(), any()))
                .willReturn(List.of(analysis(72, LocalDateTime.now())));

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
        // plates 를 비워두면 WEEK 가 today 분기를 잘못 타도 먹은 목록이 어차피 비어 통과한다.
        // 채워서 넣어야 "먹은 목록은 비운다"는 주장이 실제로 분기를 검증한다.
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "떡볶이", 60, LocalDateTime.now())));

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
