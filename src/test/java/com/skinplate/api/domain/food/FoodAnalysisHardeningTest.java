package com.skinplate.api.domain.food;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
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
    @DisplayName("표준 DB 의 ETC 는 '아니다'가 아니라 '모르겠다' — 사진을 본 AI 답을 지우지 않는다")
    void cookingMethod_etcDoesNotOverwriteAi() {
        // 가지나물은 이름 규칙으로 조리법이 안 잡혀 ETC 다.
        FoodAnalysis food = foodAnalysisService.toEntity(null,
                aiResult("가지나물", "RAW", false));

        assertThat(StandardFoodTable.find("가지나물").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.ETC);
        assertThat(food.getCookingMethod()).isEqualTo(CookingMethod.RAW);
    }

    @Test
    @DisplayName("표준 DB 의 spicy=false 는 AI 의 매운맛을 지우지 않는다 — 이름에 없을 뿐이다")
    void spicy_standardNeverClearsAi() {
        // 부대찌개는 이름에 매운맛 낱말이 없어 표준값이 false 다. 덮어쓰면 홍조 룰(R02)이
        // 진짜 매운 음식에서 조용히 빠진다.
        assertThat(StandardFoodTable.find("부대찌개").orElseThrow().spicy()).isFalse();

        assertThat(foodAnalysisService.toEntity(null, aiResult("부대찌개", "BOILED", true))
                .isSpicy()).isTrue();
        assertThat(foodAnalysisService.toEntity(null, aiResult("부대찌개", "BOILED", false))
                .isSpicy()).isFalse();
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
                new BigDecimal("24.0"), new BigDecimal("32.0"), 1850, new BigDecimal("6.2")));
    }

    /** 영양값은 표준 DB 와 겹치지 않는 숫자로 둔다 — 어느 쪽이 이겼는지 보이게. */
    private static OpenAiFoodResult aiResult(String foodName, String cookingMethod, boolean spicy) {
        return new OpenAiFoodResult(true, foodName, "한식", cookingMethod, spicy,
                List.of(), new OpenAiFoodResult.Nutrition(111, new BigDecimal("1.1"),
                new BigDecimal("1.1"), new BigDecimal("1.1"), 111, new BigDecimal("1.1")));
    }
}
