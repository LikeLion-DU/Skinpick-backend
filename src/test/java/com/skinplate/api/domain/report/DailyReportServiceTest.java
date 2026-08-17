package com.skinplate.api.domain.report;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.MealType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.report.service.DailyReportService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.common.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 일일 리포트는 저장된 기록만 다시 센다. 새 점수 규칙도, 조회 시점 AI 호출도 없다.
 */
class DailyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private SkinPlateRepository skinPlateRepository;
    private AppUserRepository userRepository;
    private DailyReportService dailyReportService;

    @BeforeEach
    void setUp() {
        skinPlateRepository = mock(SkinPlateRepository.class);
        userRepository = mock(AppUserRepository.class);
        dailyReportService = new DailyReportService(skinPlateRepository, userRepository);

        givenConcerns();
    }

    @Test
    @DisplayName("기록이 없는 하루는 점수·등급이 null 이고 목록은 전부 빈 배열이다")
    void emptyDay() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        DailyReportResponse report = dailyReportService.get(USER_ID, DATE);

        assertThat(report.date()).isEqualTo(DATE);
        assertThat(report.dailyScore()).isNull();
        assertThat(report.grade()).isNull();
        assertThat(report.recordCount()).isZero();
        assertThat(report.nutrition()).isEmpty();
        assertThat(report.concerns()).isEmpty();
        assertThat(report.meals()).isEmpty();
        assertThat(report.aiComment()).isNull();
    }

    @Test
    @DisplayName("정상적인 하루 — 저장된 끼니 점수의 평균이 일일 종합 점수다")
    void normalDay() {
        SkinPlate plate = plate(1L, "그릭요거트", 78, DATE.atTime(8, 20));
        plate.attachAiComments("팁", "오늘은 발효식품을 잘 챙겼어요");
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(plate));

        DailyReportResponse report = dailyReportService.get(USER_ID, DATE);

        assertThat(report.dailyScore()).isEqualTo(78);
        assertThat(report.grade()).isEqualTo(SkinLevel.GOOD);
        assertThat(report.recordCount()).isEqualTo(1);
        assertThat(report.aiComment()).isEqualTo("오늘은 발효식품을 잘 챙겼어요");
        assertThat(report.meals()).hasSize(1);
        assertThat(report.meals().get(0).foodName()).isEqualTo("그릭요거트");
        assertThat(report.meals().get(0).mealType()).isEqualTo(MealType.BREAKFAST);
    }

    @Test
    @DisplayName("음식이 여러 개면 평균을 반올림하고 최신 기록이 목록 맨 앞에 온다")
    void multipleMeals() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, "그릭요거트", 78, DATE.atTime(8, 20)),
                plate(2L, "김치찌개", 60, DATE.atTime(12, 30)),
                plate(3L, "치킨", 55, DATE.atTime(19, 0))));

        DailyReportResponse report = dailyReportService.get(USER_ID, DATE);

        // (78 + 60 + 55) / 3 = 64.33 → 64
        assertThat(report.dailyScore()).isEqualTo(64);
        assertThat(report.recordCount()).isEqualTo(3);
        assertThat(report.meals()).extracting("foodName").containsExactly("치킨", "김치찌개", "그릭요거트");
    }

    @Test
    @DisplayName("점수 등급은 저장된 점수에서 다시 만든다 — SkinLevel 경계 그대로다")
    void grade() {
        assertThat(gradeOf(90)).isEqualTo(SkinLevel.EXCELLENT);
        assertThat(gradeOf(80)).isEqualTo(SkinLevel.GOOD);
        assertThat(gradeOf(60)).isEqualTo(SkinLevel.NORMAL);
        assertThat(gradeOf(40)).isEqualTo(SkinLevel.CAUTION);
        assertThat(gradeOf(20)).isEqualTo(SkinLevel.SEVERE);
    }

    @Test
    @DisplayName("영양은 기록된 끼니의 합계다 — 항목 순서가 고정이라 화면이 흔들리지 않는다")
    void nutritionIsSummed() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, "김치찌개", 60, DATE.atTime(12, 0),
                        Nutrition.of(520, decimal(28.5), decimal(24), decimal(32), 1850, decimal(6.2))),
                plate(2L, "라면", 55, DATE.atTime(19, 0),
                        Nutrition.of(500, decimal(11.5), decimal(16), decimal(68), 1750, decimal(3.8)))));

        List<NutritionItemDto> nutrition = dailyReportService.get(USER_ID, DATE).nutrition();

        assertThat(nutrition).extracting(NutritionItemDto::nutrient)
                .containsExactly(NutrientType.CALORIES, NutrientType.CARB, NutrientType.PROTEIN,
                        NutrientType.FAT, NutrientType.SODIUM, NutrientType.SUGAR);
        assertThat(amount(nutrition, NutrientType.CALORIES)).isEqualByComparingTo("1020.0");
        assertThat(amount(nutrition, NutrientType.SODIUM)).isEqualByComparingTo("3600.0");
        // 3600 / 2000 = 180% → 과다
        assertThat(item(nutrition, NutrientType.SODIUM).status()).isEqualTo(NutrientType.Status.HIGH);
        assertThat(item(nutrition, NutrientType.SODIUM).higherIsWorse()).isTrue();
    }

    @Test
    @DisplayName("기록이 있으면 영양값이 0 이어도 여섯 항목이 다 온다 — 막대 수가 날마다 달라지지 않는다")
    void nutritionWithZeroValues() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, "물", 70, DATE.atTime(12, 0),
                        Nutrition.of(0, BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.ZERO, 0, BigDecimal.ZERO))));

        List<NutritionItemDto> nutrition = dailyReportService.get(USER_ID, DATE).nutrition();

        assertThat(nutrition).hasSize(6);
        assertThat(amount(nutrition, NutrientType.PROTEIN)).isEqualByComparingTo("0.0");
        assertThat(item(nutrition, NutrientType.PROTEIN).percent()).isZero();
        // 단백질만 부족이 문제인 항목이다. 앱이 색을 반대로 칠하지 않게 방향을 함께 보낸다.
        assertThat(item(nutrition, NutrientType.PROTEIN).higherIsWorse()).isFalse();
    }

    @Test
    @DisplayName("고민을 고른 사용자는 고민별 점수를 받는다 — 관련 룰의 감점만 반영된다")
    void concernScores() {
        givenConcerns(SkinConcern.PUFFINESS, SkinConcern.ACNE);

        SkinPlate plate = plate(1L, "라면", 55, DATE.atTime(19, 0));
        addFeedback(plate, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");   // 부기
        addFeedback(plate, FeedbackType.GOOD, "R09", 4, "발효식품 포함");     // 여드름
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(plate));

        List<ConcernScoreDto> concerns = dailyReportService.get(USER_ID, DATE).concerns();

        assertThat(concerns).extracting(ConcernScoreDto::concern)
                .containsExactly(SkinConcern.ACNE, SkinConcern.PUFFINESS);   // enum 선언 순서
        assertThat(scoreOf(concerns, SkinConcern.PUFFINESS)).isEqualTo(62);  // 70 - 8
        assertThat(scoreOf(concerns, SkinConcern.ACNE)).isEqualTo(74);       // 70 + 4
    }

    @Test
    @DisplayName("고민을 고르지 않았으면 고민 목록은 빈 배열이다")
    void noConcerns() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "라면", 55, DATE.atTime(19, 0))));

        assertThat(dailyReportService.get(USER_ID, DATE).concerns()).isEmpty();
    }

    @Test
    @DisplayName("식단 룰이 없는 고민(다크서클)은 점수를 지어내지 않고 빠진다")
    void concernWithoutRulesIsSkipped() {
        givenConcerns(SkinConcern.DARK_CIRCLE);
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "라면", 55, DATE.atTime(19, 0))));

        assertThat(dailyReportService.get(USER_ID, DATE).concerns()).isEmpty();
    }

    @Test
    @DisplayName("고민 점수는 끼니마다 기준선에서 다시 세고 평균한다 — 많이 기록해도 불리하지 않다")
    void concernScoreIsPerMealAverage() {
        givenConcerns(SkinConcern.PUFFINESS);

        SkinPlate first = plate(1L, "라면", 55, DATE.atTime(12, 0));
        addFeedback(first, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        SkinPlate second = plate(2L, "김치찌개", 60, DATE.atTime(19, 0));
        addFeedback(second, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(first, second));

        // 62 와 62 의 평균은 62 다. 하루치로 한 번에 더했다면 54 가 나온다.
        assertThat(scoreOf(dailyReportService.get(USER_ID, DATE).concerns(), SkinConcern.PUFFINESS))
                .isEqualTo(62);
    }

    @Test
    @DisplayName("잘한 점·개선할 점은 저장된 피드백 문구를 빈도순으로 추린다")
    void goodAndImprovePoints() {
        SkinPlate first = plate(1L, "라면", 55, DATE.atTime(12, 0));
        addFeedback(first, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(first, FeedbackType.GOOD, "R09", 4, "발효식품 포함");
        SkinPlate second = plate(2L, "김치찌개", 60, DATE.atTime(19, 0));
        addFeedback(second, FeedbackType.CAUTION, "R04", -8, "나트륨 과다");
        addFeedback(second, FeedbackType.CAUTION, "R02", -12, "매운맛 자극");
        addFeedback(second, FeedbackType.ACTION, "R04", 0, "국물을 절반만 남기세요.");
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(first, second));

        DailyReportResponse report = dailyReportService.get(USER_ID, DATE);

        assertThat(report.goodPoints()).containsExactly("발효식품 포함");
        // 2회인 나트륨이 앞. ACTION 의 긴 안내문은 섞이지 않는다.
        assertThat(report.improvePoints()).containsExactly("나트륨 과다", "매운맛 자극");
    }

    @Test
    @DisplayName("그날 문장은 최신 기록의 것을 쓰고, 없으면 그 이전 기록으로 내려간다")
    void dailyCommentFallsBack() {
        SkinPlate earlier = plate(1L, "그릭요거트", 78, DATE.atTime(8, 20));
        earlier.attachAiComments("팁", "아침을 잘 챙겼어요");
        SkinPlate latest = plate(2L, "치킨", 55, DATE.atTime(19, 0));   // 문장 생성 실패
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(earlier, latest));

        assertThat(dailyReportService.get(USER_ID, DATE).aiComment()).isEqualTo("아침을 잘 챙겼어요");
    }

    @Test
    @DisplayName("date 를 생략하면 서버의 오늘(KST) 하루를 반개구간으로 조회한다")
    void defaultsToTodayInKst() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        LocalDate today = LocalDate.now(DateRange.KST);
        DailyReportResponse report = dailyReportService.get(USER_ID, null);

        assertThat(report.date()).isEqualTo(today);
        verify(skinPlateRepository).findInRange(
                eq(USER_ID), eq(today.atStartOfDay()), eq(today.plusDays(1).atStartOfDay()));
    }

    @Test
    @DisplayName("자정 직후와 자정 직전 기록은 각자의 날에 잡힌다 — 경계가 겹치지 않는다")
    void dayBoundary() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, "야식", 50, DATE.atTime(23, 59)),
                plate(2L, "해장국", 65, DATE.plusDays(1).atStartOfDay())));

        List<DailyReportResponse> days = dailyReportService.getRange(
                USER_ID, DateRange.of(DATE, DATE.plusDays(1)));

        assertThat(days).hasSize(2);
        assertThat(days.get(0).date()).isEqualTo(DATE);
        assertThat(days.get(0).dailyScore()).isEqualTo(50);
        assertThat(days.get(1).date()).isEqualTo(DATE.plusDays(1));
        assertThat(days.get(1).dailyScore()).isEqualTo(65);
    }

    @Test
    @DisplayName("기간 조회는 기록이 있는 날만, 날짜 오름차순으로 돌려준다")
    void rangeSkipsEmptyDays() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(1L, "치킨", 55, DATE.plusDays(2).atTime(19, 0)),
                plate(2L, "라면", 60, DATE.atTime(12, 0))));

        List<DailyReportResponse> days = dailyReportService.getRange(
                USER_ID, DateRange.of(DATE, DATE.plusDays(6)));

        assertThat(days).extracting(DailyReportResponse::date)
                .containsExactly(DATE, DATE.plusDays(2));
    }

    // ---- 픽스처 ----

    private SkinLevel gradeOf(int score) {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "음식", score, DATE.atTime(12, 0))));

        return dailyReportService.get(USER_ID, DATE).grade();
    }

    private void givenConcerns(SkinConcern... concerns) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        user.updateSkinConcerns(Set.of(concerns));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private static int scoreOf(List<ConcernScoreDto> concerns, SkinConcern concern) {
        return concerns.stream()
                .filter(item -> item.concern() == concern)
                .mapToInt(ConcernScoreDto::score)
                .findFirst()
                .orElseThrow();
    }

    private static NutritionItemDto item(List<NutritionItemDto> nutrition, NutrientType type) {
        return nutrition.stream()
                .filter(entry -> entry.nutrient() == type)
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal amount(List<NutritionItemDto> nutrition, NutrientType type) {
        return item(nutrition, type).amount();
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static SkinPlate plate(Long id, String foodName, int score, LocalDateTime createdAt) {
        return plate(id, foodName, score, createdAt,
                Nutrition.of(500, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1800, BigDecimal.ONE));
    }

    private static SkinPlate plate(Long id, String foodName, int score,
                                   LocalDateTime createdAt, Nutrition nutrition) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식", nutrition,
                CookingMethod.BOILED, false, "{}");
        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");

        SkinPlate plate = SkinPlate.create(user, analysis, food, score, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", createdAt);
        return plate;
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
