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
        // 여유를 크게 두면 파일이 잘려도 통과한다 — 실제 1,452 종에 붙여 둔다.
        assertThat(StandardFoodTable.size()).isGreaterThan(1400);
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
        // 낱말을 뒤에서부터 보고 첫 적중에서 멈추므로, "라면사리 부대찌개" 는 부대찌개에서
        // 끝나 앞 낱말까지 가지도 않는다. 그래서 위험한 쪽인 앞 낱말을 직접 본다 —
        // "라면사리" 가 라면으로 잡히면 찌개에 라면 영양값이 들어간다.
        assertThat(StandardFoodTable.find("라면사리")).isEmpty();
        assertThat(StandardFoodTable.find("라면사리 부대찌개").orElseThrow().name())
                .doesNotContain("라면");
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
    @DisplayName("국물인지 모르는 면류는 단정하지 않는다 — 비빔국수에 '국물을 남기세요'가 붙으면 안 된다")
    void ambiguousNoodlesStayUnknown() {
        // '면'·'국' 부분일치로 BOILED 가 되면 isSoup() 이 참이 되어, 국물 없는 음식에
        // 국물 조언과 HALVE_SOUP 시뮬레이션이 붙는다. ETC 면 국물 조언이 안 붙는다 —
        // 2026-08-18 부터는 표준 DB 에서 찾은 음식의 조리법을 AI 가 덮지 않으므로,
        // ETC 가 "모르겠으니 AI 에게" 가 아니라 "국물이라 단정하지 않는다" 가 됐다.
        assertThat(StandardFoodTable.find("비빔국수").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.ETC);
        assertThat(StandardFoodTable.find("막국수").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.ETC);
        // 국물이 확실한 쪽은 그대로 BOILED 다.
        assertThat(StandardFoodTable.find("라면").orElseThrow().cookingMethod())
                .isEqualTo(CookingMethod.BOILED);
    }

    @Test
    @DisplayName("1인분으로 볼 수 없는 값은 아예 싣지 않는다 — 밀키트 포장 무게가 한 끼로 둔갑했다")
    void mealKitPortionsAreExcluded() {
        // 라멘은 나트륨 7,127mg·1,519kcal 로 실려 있었다(2인분 포장 무게 환산).
        // 그 값이면 나트륨 감점이 사진과 무관하게 상한에 붙박인다 — AI 추정치가 낫다.
        assertThat(StandardFoodTable.find("라멘")).isEmpty();
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

    /**
     * 기본명 매칭은 수식어를 버린다("연어 샐러드" → 샐러드). 그대로 두면 이름에 적힌
     * 연어의 오메가3까지 사라져 가점 룰이 전부 꺼진다 — 연어 샐러드가 68점으로 나온
     * 원인이었다. 이름이 보증하는 가점 태그는 매칭 결과 위에 얹는다.
     */
    @Test
    @DisplayName("이름이 보증하는 가점 재료는 기본명 매칭 뒤에도 살아남는다")
    void nameBackedBonusTagsSurviveBaseNameFallback() {
        StandardFood salmonSalad = StandardFoodTable.find("연어 샐러드").orElseThrow();
        assertThat(salmonSalad.tags()).contains(IngredientTag.OMEGA3);      // 연어
        assertThat(salmonSalad.tags()).contains(IngredientTag.VITAMIN_C);   // 샐러드(채소)

        // 감점 재료는 이름으로 얹지 않는다 — "고추냉이 연어"가 매운 음식이 되면 안 된다.
        //
        // **단언이 이빨을 가지려면 매칭된 행이 그 태그를 원래 안 갖고 있어야 한다.**
        // 예전 이 자리는 `돼지고기 김치찌개` 의 PROBIOTIC 을 봤는데 그 행에 이미
        // `"tags": ["PROBIOTIC"]` 이 실려 있어 withNameTags 를 지워도 통과했다.
        // `연어구이` 도 마찬가지로 OMEGA3 를 이미 갖고 있어 같은 함정이다.
        // `고추냉이 연어 샐러드` 는 `샐러드` 행(태그 ETC 하나)에 떨어지므로,
        // OMEGA3 가 붙었다면 그건 오직 이름에서 온 것이다.
        StandardFood wasabiSalmonSalad =
                StandardFoodTable.find("고추냉이 연어 샐러드").orElseThrow();
        assertThat(wasabiSalmonSalad.name()).isEqualTo("샐러드");
        assertThat(wasabiSalmonSalad.tags()).contains(IngredientTag.OMEGA3);   // 가점은 이름에서 붙고
        assertThat(wasabiSalmonSalad.tags()).doesNotContain(IngredientTag.CAPSAICIN);  // 감점은 안 붙는다
        assertThat(StandardFoodTable.find("치즈 연어 샐러드").orElseThrow().tags())
                .doesNotContain(IngredientTag.DAIRY);
    }

    @Test
    @DisplayName("모르는 음식과 null 은 조용히 비운다 — AI 추정치로 떨어진다")
    void missingIsEmpty() {
        assertThat(StandardFoodTable.find(null)).isEmpty();
        assertThat(StandardFoodTable.find("  ")).isEmpty();
        assertThat(StandardFoodTable.find("존재하지않는음식이름입니다")).isEmpty();
    }
}
