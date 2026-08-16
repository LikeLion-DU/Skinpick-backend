package com.skinplate.api.domain.food;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.service.StandardFood;
import com.skinplate.api.domain.food.service.StandardFoodTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표준 음식 테이블은 재현성을 위해 만든 것이다. 그 재현성이 실제로 서는지 본다.
 */
class StandardFoodTableTest {

    @Test
    @DisplayName("공공데이터가 실제로 적재된다")
    void loads() {
        // 여유를 크게 두면 파일이 잘려도 통과한다 — 실제 1,531 종에 붙여 둔다.
        assertThat(StandardFoodTable.size()).isGreaterThan(1500);
    }

    @Test
    @DisplayName("AI 가 재료를 앞에 붙여 답하면 뒤 낱말이 이긴다 — 정체는 김치찌개지 돼지고기가 아니다")
    void headNounWins() {
        // isPresent() 만 보면 "돼지고기" 가 먼저 걸려 고기구이 값이 들어가도 통과한다.
        // 실제로 그렇게 새서 찌개가 650kcal·나트륨 334mg 으로 나온 적이 있다.
        assertThat(StandardFoodTable.find("돼지고기 김치찌개").orElseThrow().name())
                .contains("김치찌개");
        assertThat(StandardFoodTable.find("매콤한 제육볶음").orElseThrow().name())
                .contains("제육볶음");
        assertThat(StandardFoodTable.find("김치찌개").orElseThrow().name())
                .contains("김치찌개");
    }

    @Test
    @DisplayName("낱말 중간에 걸친 이름은 잡지 않는다 — 부대찌개에 라면 값이 들어가면 안 된다")
    void doesNotMatchMidWord() {
        // ifPresent 로 두면 테이블이 통째로 안 실려도 통과한다. 이 테스트의 목적은
        // "엉뚱한 치환이 없다"이지 "비어 있다"가 아니므로 값을 받는 것까지 본다.
        assertThat(StandardFoodTable.find("라면사리 부대찌개").orElseThrow().name())
                .doesNotContain("라면");
    }

    @Test
    @DisplayName("같은 이름은 항상 같은 값이다 — 이 테이블의 존재 이유다")
    void isDeterministic() {
        StandardFood first = StandardFoodTable.find("돼지고기 김치찌개").orElseThrow();
        for (int i = 0; i < 20; i++) {
            StandardFood again = StandardFoodTable.find("돼지고기 김치찌개").orElseThrow();
            assertThat(again.name()).isEqualTo(first.name());
            assertThat(again.sodiumMg()).isEqualTo(first.sodiumMg());
        }
    }

    @Test
    @DisplayName("1인분으로 환산돼 있다 — 100g 기준이 그대로 남아 있으면 룰이 안 걸린다")
    void isPerServing() {
        StandardFood ramen = StandardFoodTable.find("라면").orElseThrow();

        // 100g 기준값(라면 100g 당 나트륨 ~283mg)이 그대로면 1500 을 절대 못 넘는다.
        // 1인분 환산이 됐다면 실제 라면 한 그릇에 가까운 값이어야 한다.
        assertThat(ramen.sodiumMg()).isBetween(1000, 2600);
        assertThat(ramen.caloriesKcal()).isBetween(300, 800);
    }

    @Test
    @DisplayName("조리법과 매운맛이 이름에서 붙는다 — 룰 R02·R07 의 입력이다")
    void derivesAttributes() {
        assertThat(StandardFoodTable.find("돈가스").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.FRIED);
        assertThat(StandardFoodTable.find("라면").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.BOILED);

        StandardFood kimchiStew = StandardFoodTable.find("김치찌개").orElseThrow();
        assertThat(kimchiStew.spicy()).isTrue();
        assertThat(kimchiStew.tags()).contains(IngredientTag.PROBIOTIC);
    }

    @Test
    @DisplayName("낱자 하나로 다른 음식을 끌고 오지 않는다 — 전골은 튀김이 아니다")
    void singleSyllableDoesNotLeak() {
        // '전' 을 부분일치로 두면 전골·전복탕·전어구이가 전부 FRIED 가 되고,
        // AI 가 맞게 본 BOILED 를 덮어써 지성 피부에 없던 R07 감점이 붙는다.
        assertThat(StandardFoodTable.find("곱창전골").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.BOILED);
        assertThat(StandardFoodTable.find("전복탕").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.BOILED);
        assertThat(StandardFoodTable.find("전어구이").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.GRILLED);
        // 진짜 전은 그대로 기름이다.
        assertThat(StandardFoodTable.find("김치전").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.FRIED);
    }

    @Test
    @DisplayName("풋고추는 매운맛이 아니다 — 고명 이름으로 R02 가 확정 발동하면 안 된다")
    void garnishPepperIsNotSpicy() {
        StandardFood dish = StandardFoodTable.find("풋고추찜").orElseThrow();

        assertThat(dish.spicy()).isFalse();
        assertThat(dish.tags()).doesNotContain(IngredientTag.CAPSAICIN);
    }

    @Test
    @DisplayName("시연 김치찌개는 돼지고기 쪽 값을 받는다 — 기본형에 얹히면 60점이 54점이 된다")
    void demoStewKeepsItsProtein() {
        StandardFood demo = StandardFoodTable.find("돼지고기 김치찌개").orElseThrow();

        // 원본의 기본형 김치찌개는 단백질 15.1g 이라 R05(20g 이상 가점)가 안 걸린다.
        assertThat(demo.proteinG()).isGreaterThanOrEqualTo(new BigDecimal("20"));
        assertThat(demo.sodiumMg()).isEqualTo(1850);
    }

    @Test
    @DisplayName("모르는 음식과 null 은 조용히 비운다 — AI 추정치로 떨어진다")
    void missingIsEmpty() {
        assertThat(StandardFoodTable.find(null)).isEmpty();
        assertThat(StandardFoodTable.find("  ")).isEmpty();
        assertThat(StandardFoodTable.find("존재하지않는음식이름입니다")).isEmpty();
    }
}
