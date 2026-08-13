package com.skinplate.api.domain.food;

import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.service.StandardNutrition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 응답을 믿지 않는 세 지점. 셋 다 <b>유료 호출이 끝난 뒤에</b> 터지는 자리라
 * 실패해도 공짜로 다시 부를 수 없다.
 */
class FoodAnalysisHardeningTest {

    // ---- 표준 영양값 매칭 ----

    @Test
    @DisplayName("시연 음식은 재료가 앞에 붙어도 표준값을 찾는다 — 여기가 60점의 근거다")
    void demoDishes_matchWithPrefixedIngredients() {
        assertThat(StandardNutrition.find("돼지고기 김치찌개")).isPresent();
        assertThat(StandardNutrition.find("김치찌개")).isPresent();
        assertThat(StandardNutrition.find("연어구이")).isPresent();
        assertThat(StandardNutrition.find("간장 연어구이")).isPresent();
        assertThat(StandardNutrition.find("신라면")).isPresent();
    }

    @Test
    @DisplayName("낱말 가운데에 키가 들어간 다른 음식은 잡지 않는다 — 부대찌개가 라면 값을 받으면 안 된다")
    void otherDishes_areNotSubstituted() {
        // contains 였을 때 라면으로 잡혀 부대찌개에 라면 영양값이 들어갔다.
        assertThat(StandardNutrition.find("라면사리 부대찌개")).isEmpty();
        assertThat(StandardNutrition.find("김치볶음밥")).isEmpty();
        assertThat(StandardNutrition.find("된장찌개")).isEmpty();
    }

    @Test
    @DisplayName("이름이 없으면 표준값도 없다")
    void nullName_isEmpty() {
        assertThat(StandardNutrition.find(null)).isEmpty();
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
}
