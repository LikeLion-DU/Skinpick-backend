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

    @Column(nullable = false, length = 500)
    private String imageUrl;

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

    @OneToMany(mappedBy = "foodAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodIngredient> ingredients = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static FoodAnalysis create(AppUser user,
                                      String imageUrl,
                                      String foodName,
                                      String foodCategory,
                                      Nutrition nutrition,
                                      CookingMethod cookingMethod,
                                      boolean spicy,
                                      String rawAiResponse) {
        FoodAnalysis food = new FoodAnalysis();
        food.user = user;
        food.imageUrl = imageUrl;
        food.foodName = foodName;
        food.foodCategory = foodCategory;
        food.nutrition = nutrition;
        food.cookingMethod = cookingMethod == null ? CookingMethod.ETC : cookingMethod;
        food.spicy = spicy;
        food.rawAiResponse = rawAiResponse;
        return food;
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
