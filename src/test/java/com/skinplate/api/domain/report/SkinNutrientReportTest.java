package com.skinplate.api.domain.report;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.report.service.DailyReportService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.repository.AppUserRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 피부 영양 포인트({@code skinNutrients}) · 고민 문장/태그 · 기록 카드 태그.
 *
 * <p>세 기능이 한 파일에 있는 이유는 전부 <b>같은 하루치 조립</b>({@code DailyReportAssembler})
 * 에서 나오고, 같은 픽스처가 필요하기 때문이다.
 */
class SkinNutrientReportTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private SkinPlateRepository skinPlateRepository;
    private AppUserRepository userRepository;
    private DailyReportService service;

    @BeforeEach
    void setUp() {
        skinPlateRepository = mock(SkinPlateRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new DailyReportService(skinPlateRepository, userRepository);
        givenConcerns(SkinConcern.ACNE);
    }

    // ─────────────────────────── ① 피부 영양 포인트 ───────────────────────────

    @Test
    @DisplayName("영양 밸런스는 6종 그대로고, 피부 영양 포인트가 3종으로 따로 온다")
    void twoArrays() {
        // 표준표의 정식 이름을 쓴다 — 저장되는 standardFoodName 이 그것이고,
        // 결측 판정은 그 이름으로 표를 되짚어 본다(시금치나물은 비타민C·아연 둘 다 있다).
        givenPlates(standardMatched(1L, "시금치나물", 92));

        DailyReportResponse report = service.get(USER_ID, DATE);

        assertThat(report.nutrition()).hasSize(6);
        assertThat(report.nutrition()).extracting(NutritionItemDto::nutrient)
                .containsExactly(NutrientType.CALORIES, NutrientType.CARB, NutrientType.PROTEIN,
                        NutrientType.FAT, NutrientType.SODIUM, NutrientType.SUGAR);

        assertThat(report.skinNutrients()).hasSize(3);
        assertThat(report.skinNutrients()).extracting(NutritionItemDto::nutrient)
                .containsExactly(NutrientType.VITAMIN_C, NutrientType.OMEGA3, NutrientType.ZINC);
    }

    @Test
    @DisplayName("표준 음식표에 매칭된 끼니만 미량영양소를 센다 — 실측값이 그대로 합계가 된다")
    void measuredFromStandardMatchedOnly() {
        // 표준표의 정식 이름을 쓴다 — 저장되는 standardFoodName 이 그것이고,
        // 결측 판정은 그 이름으로 표를 되짚어 본다(시금치나물은 비타민C·아연 둘 다 있다).
        givenPlates(standardMatched(1L, "시금치나물", 92));

        List<NutritionItemDto> skin = service.get(USER_ID, DATE).skinNutrients();

        // 픽스처: 비타민C 45mg · 아연 6mg · portionSize MEDIUM(1.0)
        assertThat(itemOf(skin, NutrientType.VITAMIN_C).amount()).isEqualByComparingTo("45.0");
        assertThat(itemOf(skin, NutrientType.VITAMIN_C).status())
                .isEqualTo(NutrientType.Status.LOW);   // 45 / 100 = 45%
        assertThat(itemOf(skin, NutrientType.ZINC).amount()).isEqualByComparingTo("6.0");
    }

    @Test
    @DisplayName("매칭 실패 끼니만 있는 날은 0 이 아니라 status=null 이다 — 모르는 것을 부족이라 하지 않는다")
    void unmeasuredWhenNoStandardMatch() {
        // AI 추정만 있는 음식이다. V9 컬럼이 전부 0 이라, 합계에 넣으면 화면이
        // "비타민C 부족"이라고 단정한다 — 사실은 재지 못한 것이다.
        givenPlates(aiOnly(1L, "집밥", 70));

        List<NutritionItemDto> skin = service.get(USER_ID, DATE).skinNutrients();

        assertThat(itemOf(skin, NutrientType.VITAMIN_C).status()).isNull();
        assertThat(itemOf(skin, NutrientType.ZINC).status()).isNull();
        // 항목 자체는 빠지지 않는다 — 날마다 타일 수가 달라지면 화면이 흔들린다.
        assertThat(skin).hasSize(3);
    }

    @Test
    @DisplayName("오메가3 는 양이 아니라 OMEGA3 태그가 붙은 끼니 수다 — 매칭 실패 끼니도 센다")
    void omega3CountsMeals() {
        givenPlates(aiOnly(1L, "고등어구이", 80, IngredientTag.OMEGA3),
                    aiOnly(2L, "김밥", 70));

        NutritionItemDto omega3 =
                itemOf(service.get(USER_ID, DATE).skinNutrients(), NutrientType.OMEGA3);

        assertThat(omega3.unit()).isEqualTo("회");
        assertThat(omega3.amount()).isEqualByComparingTo("1.0");
        // 기준 1회를 채웠으므로 부족이 아니다. 매칭 실패 끼니여도 태그는 실제 관찰값이다.
        assertThat(omega3.status()).isEqualTo(NutrientType.Status.NORMAL);
    }

    @Test
    @DisplayName("피부 영양 포인트는 셋 다 '적을 때' 문제다 — higherIsWorse 가 false 여야 한다")
    void skinNutrientsAreLowIsWorse() {
        assertThat(NutrientType.of(NutrientType.Group.SKIN))
                .allSatisfy(type -> assertThat(type.isHigherIsWorse()).isFalse());
    }

    // ───────── ①-b 영양소별 측정 여부 (표준표 되짚기) ─────────
    //
    // "매칭됐다"는 그 음식의 행이 있다는 뜻이지 그 행에 이 영양소가 있다는 뜻이 아니다.
    // 원본(요리 계열 4,268행)에서 비타민C 는 8%, 아연은 29% 가 빈칸이고, 그 빈칸은
    // 값 0 과 별개로 존재한다. DB 는 NOT NULL DEFAULT 0 이라 둘이 합쳐지므로,
    // 판단 근거는 표준표(JSON 이 빈칸을 키 없음으로 보존한다)를 되짚는 쪽이다.

    @Test
    @DisplayName("Case A — 두 영양소가 표준표에 다 있으면 합산하고 status 를 만든다")
    void caseA_allMeasured() {
        // 시금치나물: 표준표에 vitaminCMg=35.7 · zincMg=0.95 둘 다 존재한다.
        givenPlates(standardMatched(1L, "시금치나물", 88));

        List<NutritionItemDto> skin = service.get(USER_ID, DATE).skinNutrients();

        assertThat(itemOf(skin, NutrientType.VITAMIN_C).status()).isNotNull();
        assertThat(itemOf(skin, NutrientType.ZINC).status()).isNotNull();
        // 합계는 저장된 스냅샷 값이다(픽스처 45mg · 6mg). 표준표는 '쟀는가'만 판단한다.
        assertThat(itemOf(skin, NutrientType.VITAMIN_C).amount()).isEqualByComparingTo("45.0");
        assertThat(itemOf(skin, NutrientType.ZINC).amount()).isEqualByComparingTo("6.0");
    }

    @Test
    @DisplayName("Case B — 매칭은 됐지만 두 영양소가 표준표에 없으면 status 키가 빠진다")
    void caseB_matchedButNotMeasured() {
        // 돼지고기 김치찌개: MANUAL_FOODS 라 비타민·아연을 일부러 비워 뒀다(키 없음).
        givenPlates(standardMatched(1L, "돼지고기 김치찌개", 58));

        List<NutritionItemDto> skin = service.get(USER_ID, DATE).skinNutrients();

        assertThat(itemOf(skin, NutrientType.VITAMIN_C).status()).isNull();
        assertThat(itemOf(skin, NutrientType.ZINC).status()).isNull();
        // 항목은 빠지지 않는다 — 타일 수가 날마다 달라지면 화면이 흔들린다.
        assertThat(skin).hasSize(3);
    }

    @Test
    @DisplayName("Case C — 한쪽만 표준표에 있으면 그쪽만 잰다")
    void caseC_partiallyMeasured() {
        // 고등어구이: 표준표에 vitaminCMg=2.5 는 있고 zincMg 는 빈칸이다.
        givenPlates(standardMatched(1L, "고등어구이", 80));

        List<NutritionItemDto> skin = service.get(USER_ID, DATE).skinNutrients();

        assertThat(itemOf(skin, NutrientType.VITAMIN_C).status()).isNotNull();
        assertThat(itemOf(skin, NutrientType.ZINC).status()).isNull();
    }

    @Test
    @DisplayName("잰 끼니만 합산한다 — 못 잰 끼니의 0 이 평균을 끌어내리지 않는다")
    void unmeasuredMealIsExcludedFromSum() {
        givenPlates(standardMatched(1L, "시금치나물", 88),          // vitC 측정 (45)
                    standardMatched(2L, "돼지고기 김치찌개", 58));   // vitC 미측정

        NutritionItemDto vitaminC =
                itemOf(service.get(USER_ID, DATE).skinNutrients(), NutrientType.VITAMIN_C);

        // 두 끼를 다 더해도 45 다 — 못 잰 끼니는 0 을 보태지 않고 아예 빠진다.
        assertThat(vitaminC.amount()).isEqualByComparingTo("45.0");
        assertThat(vitaminC.status()).isNotNull();
    }

    /**
     * 시연 3종. <b>기대값은 원본 데이터를 먼저 확인하고 정한 것</b>이다 —
     * `20251229_음식DB.xlsx` 요리 계열에서
     * <pre>
     *   라면(550g·분석)        비타민 C = 0.00  (빈칸이 아니라 실측 0)  · 아연 = 0.08
     *   김치찌개(400g·분석)     비타민 C = 0.00  · 아연 = 0
     *   연어구이               실측행 중량이 100g 이하라 버려져 MANUAL_FOODS 로 떨어진다
     * </pre>
     * 그래서 <b>라면의 비타민C 는 "모른다"가 아니라 "정말 0mg"</b> 이고 status 가 붙어야 한다.
     * "0 이면 미측정" 이라는 규칙을 만들지 않았다는 것이 이 테스트로 드러난다.
     */
    @Test
    @DisplayName("시연 음식 — 김치찌개·연어구이는 status 없음, 라면은 실측 0 이라 status 가 붙는다")
    void demoFoods() {
        givenPlates(standardMatched(1L, "돼지고기 김치찌개", 58));
        List<NutritionItemDto> stew = service.get(USER_ID, DATE).skinNutrients();
        assertThat(itemOf(stew, NutrientType.VITAMIN_C).status()).isNull();
        assertThat(itemOf(stew, NutrientType.ZINC).status()).isNull();

        givenPlates(standardMatched(1L, "연어구이", 82));
        List<NutritionItemDto> salmon = service.get(USER_ID, DATE).skinNutrients();
        assertThat(itemOf(salmon, NutrientType.VITAMIN_C).status()).isNull();
        assertThat(itemOf(salmon, NutrientType.ZINC).status()).isNull();

        // 라면은 원본이 0.00 을 실측으로 적어 둔 음식이다 — 못 잰 것이 아니므로 LOW 가 맞다.
        givenPlates(standardMatched(1L, "라면", 42));
        List<NutritionItemDto> ramen = service.get(USER_ID, DATE).skinNutrients();
        assertThat(itemOf(ramen, NutrientType.VITAMIN_C).status())
                .isEqualTo(NutrientType.Status.LOW);
        assertThat(itemOf(ramen, NutrientType.ZINC).status()).isNotNull();
    }

    // ─────────────────────────── ② 고민 문장 · 태그 ───────────────────────────

    @Test
    @DisplayName("고민 문장은 저장된 룰의 reason 중 가장 크게 움직인 것이다 — 새로 쓰지 않는다")
    void concernMessageComesFromStoredReason() {
        SkinPlate plate = aiOnly(1L, "떡볶이", 58);
        // ACNE 에 매달린 룰: R03(당류) · R07(튀김) · R09 · R12 · R15
        plate.addFeedback(SkinPlateFeedback.caution("R03", "당류 과다",
                "당류가 높은 편이라 트러블에 부담이 될 수 있어요.", -8, 0));
        plate.addFeedback(SkinPlateFeedback.caution("R07", "튀김 조리",
                "튀김 조리라 유분에 부담이 될 수 있어요.", -4, 1));
        // 다른 고민의 룰이다. 섞이면 안 된다.
        plate.addFeedback(SkinPlateFeedback.caution("R04", "나트륨 과다",
                "나트륨이 높은 편이에요.", -12, 2));
        givenPlates(plate);

        ConcernScoreDto acne = service.get(USER_ID, DATE).concerns().get(0);

        assertThat(acne.concern()).isEqualTo(SkinConcern.ACNE);
        // |delta| 가 가장 큰 R03 의 reason 이다. R04 는 ACNE 룰이 아니라 뽑히지 않는다.
        assertThat(acne.message()).isEqualTo("당류가 높은 편이라 트러블에 부담이 될 수 있어요.");
        assertThat(acne.tags()).containsExactly("당류 과다", "튀김 조리");
    }

    @Test
    @DisplayName("V8 이전 기록(reason 없음)은 문장이 null 이고 태그만 남는다")
    void legacyFeedbackHasNoReason() {
        SkinPlate plate = aiOnly(1L, "떡볶이", 58);
        plate.addFeedback(SkinPlateFeedback.caution("R03", "당류 과다", -8, 0));  // reason 없음
        givenPlates(plate);

        ConcernScoreDto acne = service.get(USER_ID, DATE).concerns().get(0);

        assertThat(acne.message()).isNull();
        assertThat(acne.tags()).containsExactly("당류 과다");
    }

    @Test
    @DisplayName("그 고민에 걸린 룰이 하나도 없으면 문장은 null, 태그는 빈 배열이다")
    void concernWithoutFeedback() {
        givenPlates(aiOnly(1L, "흰쌀밥", 70));

        ConcernScoreDto acne = service.get(USER_ID, DATE).concerns().get(0);

        assertThat(acne.message()).isNull();
        assertThat(acne.tags()).isEmpty();
    }

    // ─────────────────────────── ③ 기록 카드 태그 ───────────────────────────

    @Test
    @DisplayName("기록 카드 태그는 기존 임계값으로 서버가 고른다 — 부담이 되는 항목이 앞이다")
    void highlightTagsFromExistingThresholds() {
        // 나트륨 1800(>1150) · 당류 20(>15) · 단백질 25(>=20)
        givenPlates(aiOnly(1L, "떡볶이", 58));

        PlateHistoryItemDto meal = service.get(USER_ID, DATE).meals().get(0);

        assertThat(meal.highlightTags()).containsExactly("나트륨", "당류", "단백질");
    }

    @Test
    @DisplayName("걸리는 항목이 없는 끼니는 빈 배열이다 — 억지로 채우지 않는다")
    void highlightTagsCanBeEmpty() {
        Nutrition plain = Nutrition.of(300, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
                200, BigDecimal.ONE);
        givenPlates(plate(1L, "미역국", 70, plain, null));

        assertThat(service.get(USER_ID, DATE).meals().get(0).highlightTags()).isEmpty();
    }

    @Test
    @DisplayName("재료 태그도 근거가 된다 — 오메가3·발효식품은 태그에서만 알 수 있다")
    void highlightTagsUseIngredientTags() {
        givenPlates(plate(1L, "고등어구이", 80,
                Nutrition.of(300, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 200,
                        BigDecimal.ONE),
                null, IngredientTag.OMEGA3, IngredientTag.PROBIOTIC));

        assertThat(service.get(USER_ID, DATE).meals().get(0).highlightTags())
                .containsExactly("오메가3", "발효식품");
    }

    /**
     * 칩은 <b>룰 발동 여부가 아니라 관찰 요약</b>이다. 룰의 {@code hasTag} 는 점수의
     * 재현성 때문에 표준표 태그만 보지만, 칩은 저장된 태그를 그대로 읽는다 —
     * 좁히면 표준표에 없는 음식(집밥·외국 음식)의 카드가 통째로 빈다.
     *
     * <p>재현성은 그대로다: 태그는 분석 시점에 저장된 값이라 같은 기록을 몇 번 열어도
     * 같은 칩이다. 그 불변식을 아래에서 함께 잠근다.
     */
    @Test
    @DisplayName("미매칭 끼니도 저장된 재료 태그로 칩이 뜬다 — 룰 발동 여부와 다른 기준이다")
    void highlightTagsIgnoreStandardMatch() {
        // standardFoodName 이 null = 표준표 미매칭. 룰의 hasTag 는 여기서 전부 꺼진다.
        SkinPlate unmatched = plate(1L, "집밥 연어구이", 72,
                Nutrition.of(300, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 200,
                        BigDecimal.ONE),
                null, IngredientTag.OMEGA3);
        givenPlates(unmatched);

        assertThat(unmatched.getFoodAnalysis().isStandardMatched()).isFalse();
        assertThat(unmatched.getFoodAnalysis().hasTag(IngredientTag.OMEGA3)).isFalse();
        assertThat(service.get(USER_ID, DATE).meals().get(0).highlightTags())
                .containsExactly("오메가3");
    }

    @Test
    @DisplayName("같은 기록을 여러 번 조회해도 칩이 같다 — 순서까지 결정적이다")
    void highlightTagsAreDeterministic() {
        givenPlates(aiOnly(1L, "떡볶이", 58));

        List<String> first = service.get(USER_ID, DATE).meals().get(0).highlightTags();
        List<String> second = service.get(USER_ID, DATE).meals().get(0).highlightTags();
        List<String> third = service.get(USER_ID, DATE).meals().get(0).highlightTags();

        assertThat(second).containsExactlyElementsOf(first);
        assertThat(third).containsExactlyElementsOf(first);
    }

    @Test
    @DisplayName("걸리는 항목이 넷 이상이어도 최대 3개다 — 부담이 되는 쪽이 먼저 잘린다")
    void highlightTagsCapAtThree() {
        // 나트륨·당류·열량(부담) + 단백질·오메가3·발효식품(챙긴 쪽) = 후보 6개
        Nutrition heavy = Nutrition.of(1200, BigDecimal.valueOf(40), BigDecimal.valueOf(30),
                BigDecimal.valueOf(120), 3000, BigDecimal.valueOf(60));
        givenPlates(plate(1L, "치킨+콜라", 32, heavy, null,
                IngredientTag.OMEGA3, IngredientTag.PROBIOTIC));

        List<String> tags = service.get(USER_ID, DATE).meals().get(0).highlightTags();

        assertThat(tags).hasSize(3);
        // 선언 순서가 곧 우선순위다 — 부담이 되는 항목이 앞이라 챙긴 쪽이 잘린다.
        assertThat(tags).doesNotContain("오메가3", "발효식품");
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    private void givenConcerns(SkinConcern... concerns) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        for (SkinConcern concern : concerns) user.getSkinConcerns().add(concern);
        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
    }

    private void givenPlates(SkinPlate... plates) {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plates));
    }

    private static NutritionItemDto itemOf(List<NutritionItemDto> items, NutrientType type) {
        return items.stream().filter(item -> item.nutrient() == type).findFirst().orElseThrow();
    }

    /** 표준 음식표에 매칭된 끼니. 미량영양소 실측값이 들어 있다. */
    private static SkinPlate standardMatched(Long id, String foodName, int score) {
        Nutrition nutrition = Nutrition
                .of(400, BigDecimal.valueOf(25), BigDecimal.TEN, BigDecimal.TEN, 500,
                        BigDecimal.ONE)
                .withMicronutrients(BigDecimal.ONE, BigDecimal.valueOf(4),
                        300, BigDecimal.valueOf(45), BigDecimal.valueOf(6));

        return plate(id, foodName, score, nutrition, foodName);
    }

    /** AI 추정만 있는 끼니. V9 컬럼이 전부 0 이고 standardFoodName 이 없다. */
    private static SkinPlate aiOnly(Long id, String foodName, int score, IngredientTag... tags) {
        Nutrition nutrition = Nutrition.of(500, BigDecimal.valueOf(25), BigDecimal.TEN,
                BigDecimal.TEN, 1800, BigDecimal.valueOf(20));

        return plate(id, foodName, score, nutrition, null, tags);
    }

    private static SkinPlate plate(Long id, String foodName, int score, Nutrition nutrition,
                                  String standardFoodName, IngredientTag... tags) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식", nutrition,
                CookingMethod.BOILED, false, "{}");
        if (standardFoodName != null) food.assignStandardFoodName(standardFoodName);
        for (IngredientTag tag : tags) {
            food.getIngredients().add(FoodIngredient.of(foodName, tag));
        }

        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        SkinPlate plate = SkinPlate.create(user, analysis, food, score, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", DATE.atTime(12, 0));
        return plate;
    }
}
