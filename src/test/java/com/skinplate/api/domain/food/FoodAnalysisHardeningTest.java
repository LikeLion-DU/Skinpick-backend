package com.skinplate.api.domain.food;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodGroup;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.entity.Oiliness;
import com.skinplate.api.domain.food.entity.PortionSize;
import com.skinplate.api.domain.food.entity.ProcessingLevel;
import com.skinplate.api.domain.food.entity.Spiciness;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.food.service.StandardFoodTable;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * AI 응답을 믿지 않는 세 지점. 셋 다 <b>유료 호출이 끝난 뒤에</b> 터지는 자리라
 * 실패해도 공짜로 다시 부를 수 없다.
 */
class FoodAnalysisHardeningTest {

    // ---- 표준 영양값 매칭 ----

    @Test
    @DisplayName("시연 음식은 재료가 앞에 붙어도 표준값을 찾는다 — 여기가 60점의 근거다")
    void demoDishes_matchWithPrefixedIngredients() {
        // 이름까지 본다 — isPresent() 만 보면 돼지고기(고기구이 값)로 잡혀도 통과한다.
        assertThat(StandardFoodTable.find("돼지고기 김치찌개").orElseThrow().name())
                .contains("김치찌개");
        assertThat(StandardFoodTable.find("김치찌개")).isPresent();
        assertThat(StandardFoodTable.find("연어구이")).isPresent();
        assertThat(StandardFoodTable.find("간장 연어구이").orElseThrow().name())
                .contains("연어구이");
        assertThat(StandardFoodTable.find("신라면").orElseThrow().name())
                .contains("라면");
    }

    /**
     * AI 는 접시 전체를 나열해 답하기도 한다. 그때 뒤 낱말부터 보는 규칙만 있으면
     * 곁들임이 본체를 이긴다 — 실사진 E2E 에서 돈가스 한 장이 회차에 따라 704kcal(돈가스)과
     * 293kcal(샐러드)을 오갔고, 그게 59점과 68점의 차이였다.
     */
    @Test
    @DisplayName("곁들임을 나열한 이름은 본체를 찾는다 — 돈가스가 샐러드 값을 받으면 안 된다")
    void garnishPhrases_resolveToTheMainDish() {
        assertThat(StandardFoodTable.find("소스가 뿌려진 돼지고기 돈가스와 양배추 샐러드")
                .orElseThrow().name()).contains("돈가스");
        assertThat(StandardFoodTable.find("김치찌개와 공기밥").orElseThrow().name())
                .contains("김치찌개");
        // 나열이 없으면 규칙은 그대로다 — 핵심 낱말은 여전히 뒤에 온다.
        assertThat(StandardFoodTable.find("돼지고기 김치찌개").orElseThrow().name())
                .contains("김치찌개");
    }

    /**
     * 나열("A와 B")과 수식절("A와 B가 들어간 C")은 정반대다 — 앞이 본체인 쪽과 뒤가 본체인
     * 쪽. 수식절에서 첫 조각을 믿으면 김치찌개가 돼지고기(650kcal) 영양값을 받고,
     * 그 뒤로는 표준 매칭이 확정돼 AI 태그까지 눌려 틀린 답이 결정론적으로 굳는다.
     */
    @Test
    @DisplayName("수식절은 나열이 아니다 — 재료가 앞에 와도 뒤의 본체를 찾는다")
    void modifierClauses_resolveToTheTrailingDish() {
        assertThat(StandardFoodTable.find("돼지고기와 채소가 들어간 김치찌개")
                .orElseThrow().name()).contains("김치찌개");
        assertThat(StandardFoodTable.find("두부와 돼지고기를 넣은 김치찌개")
                .orElseThrow().name()).contains("김치찌개");
        assertThat(StandardFoodTable.find("양배추와 소스를 곁들인 돈가스")
                .orElseThrow().name()).contains("돈가스");

        // 나열은 그대로 첫 조각이 본체다.
        assertThat(StandardFoodTable.find("돈가스와 양배추 샐러드")
                .orElseThrow().name()).contains("돈가스");

        // **수식 표지는 첫 조각 뒤에서만 찾는다.** 이름 전체에서 찾으면 앞을 꾸미는 말까지
        // 지름길을 꺼서, 곁들임인 샐러드(293kcal)가 다시 본체 돈가스(704kcal)를 이긴다.
        assertThat(StandardFoodTable.find("소스가 올라간 돈가스와 양배추 샐러드")
                .orElseThrow().name()).contains("돈가스");
        assertThat(StandardFoodTable.find("치즈가 얹은 돈가스와 샐러드")
                .orElseThrow().name()).contains("돈가스");
    }

    /**
     * AI 는 "돈까스"라고 답하는데 공공데이터에는 "돈가스"만 있다. 그러면 조회가 뒤 낱말로
     * 밀려 재료 이름에 걸린다 — 돈가스가 고기구이 영양값(650kcal)을 받는다.
     * 실사진 E2E 에서 실제로 그렇게 됐고, 같은 사진이 59점과 66점을 오갔다.
     */
    @Test
    @DisplayName("표기가 달라도 같은 음식을 찾고, 못 찾을 때 재료 이름으로 떨어지지 않는다")
    void spellingVariants_resolveWithoutFallingBackToIngredients() {
        assertThat(StandardFoodTable.find("돈까스").orElseThrow().name()).contains("돈가스");
        assertThat(StandardFoodTable.find("소스가 뿌려진 돼지고기 돈까스와 양배추 샐러드")
                .orElseThrow().name()).contains("돈가스");

        // 첫 조각의 마지막 낱말만 본다 — 앞 낱말(재료)로 떨어지면 완전히 다른 음식이 된다.
        assertThat(StandardFoodTable.find("돼지고기 정체불명요리와 샐러드")
                .orElseThrow().name()).doesNotContain("돼지고기");
    }

    /**
     * 실사진 E2E 에서 돈가스 사진 한 장에 AI 가 붙인 이름 전부다. 다섯 가지로 불렸고
     * 그중 둘이 다른 음식(샐러드 293kcal · 돼지고기 650kcal)으로 잡혀 같은 사진이
     * 59 · 66 · 68 점을 오갔다. 이름이 흔들려도 같은 표준 음식에 닿아야 점수가 하나로 모인다.
     */
    @Test
    @DisplayName("같은 사진에 붙은 이름이 다섯 가지여도 전부 같은 표준 음식에 닿는다")
    void observedNameVariants_allResolveToTheSameDish() {
        List<String> observed = List.of(
                "돈가스 정식",
                "소스 돈가스",
                "소스가 뿌려진 돼지고기 돈가스",
                "소스 돈가스와 양배추 샐러드",
                "소스가 뿌려진 돼지고기 돈까스와 양배추 샐러드");

        assertThat(observed)
                .allSatisfy(name -> assertThat(StandardFoodTable.find(name).orElseThrow().name())
                        .as(name)
                        .isEqualTo("돈가스"));
    }

    @Test
    @DisplayName("낱말 가운데에 키가 들어간 다른 음식은 잡지 않는다 — 부대찌개가 라면 값을 받으면 안 된다")
    void otherDishes_areNotSubstituted() {
        // 구 3종 표에서는 이 이름들이 비어 있는지 봤다. 공공데이터로 넓어진 지금은
        // 제 항목이 생겼으므로, "제 값을 받는가"로 같은 위험(엉뚱한 치환)을 잡는다.
        assertThat(StandardFoodTable.find("라면사리 부대찌개").orElseThrow().name())
                .contains("부대찌개");
        assertThat(StandardFoodTable.find("된장찌개").orElseThrow().name())
                .contains("된장찌개");
    }

    @Test
    @DisplayName("이름이 없으면 표준값도 없다")
    void nullName_isEmpty() {
        assertThat(StandardFoodTable.find(null)).isEmpty();
    }

    // ---- 표준값이 AI 를 덮어쓰는 규칙 ----
    //
    // 셋의 확신도가 다르다. 영양값은 표준 DB 가 항상 이기고, 조리법·매운맛은
    // 이름에서 뽑은 값이라 "확실할 때만" 이긴다. 이 구분이 무너지면 사진을 본 AI 의
    // 정답이 문자열 규칙에 지워진다.

    @Test
    @DisplayName("영양값은 표준 DB 가 이긴다 — 룰이 비교하는 숫자가 그것뿐이다")
    void nutrition_comesFromStandardTable() {
        FoodAnalysis food = foodAnalysisService.toEntity(null,
                aiResult("돼지고기 김치찌개", "BOILED", true));

        assertThat(food.getNutrition().getSodiumMg()).isEqualTo(1850);
        assertThat(food.getNutrition().getProteinG()).isEqualByComparingTo("28.5");
    }

    @Test
    @DisplayName("조리법은 표준 DB 가 확실할 때만 이긴다 — 김치찌개는 AI 가 튀김이라 해도 국물이다")
    void cookingMethod_standardWinsWhenKnown() {
        FoodAnalysis food = foodAnalysisService.toEntity(null,
                aiResult("돼지고기 김치찌개", "FRIED", true));

        assertThat(food.getCookingMethod()).isEqualTo(CookingMethod.BOILED);
    }

    @Test
    @DisplayName("표준 DB 에서 찾으면 조리법도 표준값이 이긴다 — ETC 여도 AI 답을 쓰지 않는다")
    void cookingMethod_standardWinsEvenWhenEtc() {
        // 가지나물은 이름 규칙으로 조리법이 안 잡혀 ETC 다.
        // 예전에는 ETC 를 "모르겠다"로 읽어 AI 답(RAW)을 남겼는데, 그 틈으로 같은 사진이
        // 회차마다 다른 조리법을 얻어 R07 이 켜졌다 꺼졌다. 결정론이 먼저다 —
        // 이름에 안 드러나는 튀김은 실측 포화지방을 보는 R11 이 메운다.
        FoodAnalysis food = foodAnalysisService.toEntity(null,
                aiResult("가지나물", "RAW", false));

        assertThat(StandardFoodTable.find("가지나물").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.ETC);
        assertThat(food.getCookingMethod()).isEqualTo(CookingMethod.ETC);
    }

    @Test
    @DisplayName("표준 DB 에서 찾으면 매운맛도 표준값이 이긴다 — AI 가 뭐라 하든 같은 값이다")
    void spicy_standardWinsWhenMatched() {
        // 부대찌개는 이름에 매운맛 낱말이 없어 표준값이 false 다.
        // 예전에는 AI 와 OR 로 묶어 AI 가 true 를 주는 회차에만 R02 가 켜졌다 —
        // 같은 사진이 다른 점수를 내는 통로였다. 이제 AI 답과 무관하게 같은 값이 나온다.
        assertThat(StandardFoodTable.find("부대찌개").orElseThrow().spicy()).isFalse();

        assertThat(foodAnalysisService.toEntity(null, aiResult("부대찌개", "BOILED", true))
                .isSpicy()).isFalse();
        assertThat(foodAnalysisService.toEntity(null, aiResult("부대찌개", "BOILED", false))
                .isSpicy()).isFalse();

        // 표준 DB 에서 못 찾으면 다른 단서가 없으므로 AI 답을 그대로 쓴다.
        assertThat(foodAnalysisService.toEntity(null, aiResult("정체불명의 새 음식", "BOILED", true))
                .isSpicy()).isTrue();
    }

    // ---- 관찰 특성 (스키마 v2) ----
    //
    // 특성은 표준 테이블 밖이라 AI 답이 그대로 실린다. 세 경로 — 정상값·null(구 토큰)·
    // 모르는 값 — 이 전부 UNKNOWN 흡수를 지나야 유료 호출이 안 버려진다.

    @Test
    @DisplayName("특성 5종이 enum 으로 변환돼 엔티티에 실린다")
    void traits_validValues_persist() {
        FoodAnalysis food = foodAnalysisService.toEntity(null, aiResultWithTraits(
                "SOUP_STEW", "LARGE", "HOT", "HIGH", "PROCESSED"));

        assertThat(food.getTraits().getFoodGroup()).isEqualTo(FoodGroup.SOUP_STEW);
        assertThat(food.getTraits().getPortionSize()).isEqualTo(PortionSize.LARGE);
        assertThat(food.getTraits().getSpiciness()).isEqualTo(Spiciness.HOT);
        assertThat(food.getTraits().getOiliness()).isEqualTo(Oiliness.HIGH);
        assertThat(food.getTraits().getProcessingLevel()).isEqualTo(ProcessingLevel.PROCESSED);
    }

    @Test
    @DisplayName("구 토큰처럼 특성이 전부 null 이어도 UNKNOWN 으로 저장된다 — 500 이 아니다")
    void traits_nullFieldsFromOldToken_fallBackToUnknown() {
        // aiResult(이름, 조리법, spicy) 헬퍼가 특성 null — 배포 경계 30분 창의 구 토큰 모양이다.
        FoodAnalysis food = foodAnalysisService.toEntity(null, aiResult("부대찌개", "BOILED", true));

        assertThat(food.getTraits().getFoodGroup()).isEqualTo(FoodGroup.ETC);
        assertThat(food.getTraits().getPortionSize()).isEqualTo(PortionSize.UNKNOWN);
        assertThat(food.getTraits().getSpiciness()).isEqualTo(Spiciness.UNKNOWN);
        assertThat(food.getTraits().getOiliness()).isEqualTo(Oiliness.UNKNOWN);
        assertThat(food.getTraits().getProcessingLevel()).isEqualTo(ProcessingLevel.UNKNOWN);
    }

    @Test
    @DisplayName("모르는 특성 값은 UNKNOWN 으로 흘린다 — 스키마의 enum 강제는 상대편 약속이다")
    void traits_unknownValues_fallBackToUnknown() {
        FoodAnalysis food = foodAnalysisService.toEntity(null, aiResultWithTraits(
                "KOREAN_FOOD", "EXTRA_LARGE", "MEGA_SPICY", "OILY", "RAW_FOOD"));

        assertThat(food.getTraits().getFoodGroup()).isEqualTo(FoodGroup.ETC);
        assertThat(food.getTraits().getPortionSize()).isEqualTo(PortionSize.UNKNOWN);
        assertThat(food.getTraits().getSpiciness()).isEqualTo(Spiciness.UNKNOWN);
        assertThat(food.getTraits().getOiliness()).isEqualTo(Oiliness.UNKNOWN);
        assertThat(food.getTraits().getProcessingLevel()).isEqualTo(ProcessingLevel.UNKNOWN);
    }

    private static OpenAiFoodResult aiResultWithTraits(String foodGroup, String portionSize,
                                                       String spiciness, String oiliness,
                                                       String processingLevel) {
        return new OpenAiFoodResult(true, "부대찌개", "한식", "BOILED", true,
                List.of(), new OpenAiFoodResult.Nutrition(111, new BigDecimal("1.1"),
                new BigDecimal("1.1"), new BigDecimal("1.1"), 111, new BigDecimal("1.1")),
                foodGroup, portionSize, spiciness, oiliness, processingLevel);
    }

    // ---- 영양값 범위 ----

    @Test
    @DisplayName("AI 가 말도 안 되는 숫자를 줘도 컬럼 상한에서 잘린다 — NUMERIC(6,2) 는 9999.99 까지다")
    void absurdDecimals_areClampedToColumnBound() {
        Nutrition nutrition = Nutrition.of(500, new BigDecimal("12000.5"), BigDecimal.TEN,
                new BigDecimal("99999.99"), 1800, BigDecimal.ONE);

        assertThat(nutrition.getProteinG()).isEqualByComparingTo("9999.99");
        assertThat(nutrition.getCarbG()).isEqualByComparingTo("9999.99");
    }

    @Test
    @DisplayName("음수는 0 으로 — 정수는 원래 막고 있었고 소수만 새고 있었다")
    void negativeDecimals_becomeZero() {
        Nutrition nutrition = Nutrition.of(-10, new BigDecimal("-5.5"), BigDecimal.ZERO,
                BigDecimal.ZERO, -20, BigDecimal.ZERO);

        assertThat(nutrition.getCaloriesKcal()).isZero();
        assertThat(nutrition.getSodiumMg()).isZero();
        assertThat(nutrition.getProteinG()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("소수 자리를 컬럼에 맞춘다 — 저장 전후로 숫자가 달라지지 않게")
    void scaleMatchesColumn() {
        Nutrition nutrition = Nutrition.of(500, new BigDecimal("28.456"), BigDecimal.ZERO,
                BigDecimal.ZERO, 1800, BigDecimal.ZERO);

        assertThat(nutrition.getProteinG().scale()).isEqualTo(2);
        assertThat(nutrition.getProteinG()).isEqualByComparingTo("28.46");
    }

    @Test
    @DisplayName("시연 값은 그대로 통과한다 — 방어를 넣었다고 60점이 바뀌면 안 된다")
    void demoValues_passThroughUnchanged() {
        Nutrition nutrition = Nutrition.of(520, new BigDecimal("28.5"), new BigDecimal("24.0"),
                new BigDecimal("32.0"), 1850, new BigDecimal("6.2"));

        assertThat(nutrition.getCaloriesKcal()).isEqualTo(520);
        assertThat(nutrition.getSodiumMg()).isEqualTo(1850);
        assertThat(nutrition.getProteinG()).isEqualByComparingTo("28.5");
        assertThat(nutrition.getSugarG()).isEqualByComparingTo("6.2");
    }

    // ---- 음식명 누락 ----

    private final VisionClient visionClient = mock(VisionClient.class);
    private final FoodAnalysisService foodAnalysisService =
            new FoodAnalysisService(visionClient, new ObjectMapper());

    private static final MultipartFile JPEG = new MockMultipartFile(
            "image", "food.jpg", "image/jpeg",
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

    @Test
    @DisplayName("음식이라는데 이름이 없으면 422 다 — NOT NULL 컬럼까지 내려가 500 이 나면 안 된다")
    void detectedButNamelessFood_is422() {
        given(visionClient.analyzeFood(anyString(), anyString()))
                .willReturn(aiResult(true, null));

        assertThatThrownBy(() -> foodAnalysisService.recognize(JPEG))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOOD_NOT_DETECTED);
    }

    @Test
    @DisplayName("이름이 공백뿐인 경우도 같다 — 스키마가 required 여도 그건 상대편 약속이다")
    void blankName_is422() {
        given(visionClient.analyzeFood(anyString(), anyString()))
                .willReturn(aiResult(true, "   "));

        assertThatThrownBy(() -> foodAnalysisService.recognize(JPEG))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOOD_NOT_DETECTED);
    }

    @Test
    @DisplayName("음식이 아니라고 하면 기존대로 422 — 이름 검사를 넣었다고 이 경로가 바뀌지 않는다")
    void notFood_is422() {
        given(visionClient.analyzeFood(anyString(), anyString()))
                .willReturn(aiResult(false, null));

        assertThatThrownBy(() -> foodAnalysisService.recognize(JPEG))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FOOD_NOT_DETECTED);
    }

    @Test
    @DisplayName("이름이 멀쩡하면 그대로 통과한다")
    void validName_passes() {
        given(visionClient.analyzeFood(anyString(), anyString()))
                .willReturn(aiResult(true, "돼지고기 김치찌개"));

        assertThat(foodAnalysisService.recognize(JPEG).foodName()).isEqualTo("돼지고기 김치찌개");
    }

    private static OpenAiFoodResult aiResult(boolean detected, String foodName) {
        return new OpenAiFoodResult(detected, foodName, "한식/찌개", "BOILED", true,
                List.of(), new OpenAiFoodResult.Nutrition(520, new BigDecimal("28.5"),
                new BigDecimal("24.0"), new BigDecimal("32.0"), 1850, new BigDecimal("6.2")),
                "SOUP_STEW", "MEDIUM", "MEDIUM", "MEDIUM", "MINIMALLY_PROCESSED");
    }

    /**
     * 영양값은 표준 DB 와 겹치지 않는 숫자로 둔다 — 어느 쪽이 이겼는지 보이게.
     * 특성 5종은 일부러 null 이다 — 구 토큰(30분 창)이 정확히 이 모양으로 오고,
     * 이 경로들이 전부 UNKNOWN 흡수를 지나가야 한다.
     */
    private static OpenAiFoodResult aiResult(String foodName, String cookingMethod, boolean spicy) {
        return new OpenAiFoodResult(true, foodName, "한식", cookingMethod, spicy,
                List.of(), new OpenAiFoodResult.Nutrition(111, new BigDecimal("1.1"),
                new BigDecimal("1.1"), new BigDecimal("1.1"), 111, new BigDecimal("1.1")),
                null, null, null, null, null);
    }
}
