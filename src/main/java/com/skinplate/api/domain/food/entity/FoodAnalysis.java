package com.skinplate.api.domain.food.entity;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "food_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 50)
    private String foodCategory;

    @Embedded
    private Nutrition nutrition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CookingMethod cookingMethod;

    @Column(name = "is_spicy", nullable = false)
    private boolean spicy;

    /**
     * AI 관찰 특성 5종 (V7). create() 시그니처를 늘리지 않고 {@link #assignTraits} 로
     * 한 번만 붙인다 — 기존 호출부·테스트가 그대로 남고, 안 붙인 엔티티는
     * {@link #getTraits()}가 UNKNOWN 으로 읽어 기존과 동일하게 동작한다.
     */
    @Embedded
    private FoodTraits traits;

    @OneToMany(mappedBy = "foodAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodIngredient> ingredients = new ArrayList<>();

    /**
     * AI 분석 원본 + 서버 메타데이터. 기록 저장(POST /plates/records)이 만든 행은
     * 최상위 형제 키로 {@code _meta.jti} 를 얹어 멱등키로 쓴다 — 새 컬럼·테이블 없이
     * 기존 jsonb 하나를 재사용한다. 이 컬럼이 없던 시절 행은 {@code _meta} 가 없고,
     * {@code raw_ai_response->'_meta'->>'jti'} 는 그 행들에서 null 을 준다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static FoodAnalysis create(AppUser user,
                                      String foodName,
                                      String foodCategory,
                                      Nutrition nutrition,
                                      CookingMethod cookingMethod,
                                      boolean spicy,
                                      String rawAiResponse) {
        FoodAnalysis food = new FoodAnalysis();
        food.user = user;
        food.foodName = foodName;
        food.foodCategory = foodCategory;
        food.nutrition = nutrition;
        food.cookingMethod = cookingMethod == null ? CookingMethod.ETC : cookingMethod;
        food.spicy = spicy;
        food.rawAiResponse = rawAiResponse;
        return food;
    }

    /** 생성 직후 한 번만 부른다. null 을 넣어도 getTraits() 가 UNKNOWN 으로 흡수한다. */
    public void assignTraits(FoodTraits traits) {
        this.traits = traits;
    }

    /**
     * null 을 반환하지 않는다. V7 이전 행·구 토큰·트레이트를 안 붙인 픽스처는
     * 전부 UNKNOWN 으로 읽혀 룰·리포트가 기존과 동일하게 동작한다.
     */
    public FoodTraits getTraits() {
        return traits == null ? FoodTraits.UNKNOWN : traits;
    }

    // ---- 연관관계 ----

    public void addIngredient(FoodIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.assignTo(this);
    }

    public void addIngredients(List<FoodIngredient> ingredientList) {
        ingredientList.forEach(this::addIngredient);
    }

    // ---- Rule Engine이 사용하는 질의 ----

    public boolean hasTag(IngredientTag tag) {
        return ingredients.stream().anyMatch(ingredient -> ingredient.getTag() == tag);
    }

    public boolean hasAnyTag(IngredientTag... tags) {
        for (IngredientTag tag : tags) {
            if (hasTag(tag)) return true;
        }
        return false;
    }

    public boolean isSoup() {
        return cookingMethod == CookingMethod.BOILED;
    }

    public boolean isFried() {
        return cookingMethod == CookingMethod.FRIED;
    }

    /** 명시적 spicy 플래그 또는 캡사이신 재료 */
    public boolean isSpicyFood() {
        return spicy || hasTag(IngredientTag.CAPSAICIN);
    }
}
