package com.skinplate.api.domain.food.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "food_ingredient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodIngredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_analysis_id", nullable = false)
    private FoodAnalysis foodAnalysis;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngredientTag tag;

    public static FoodIngredient of(String name, IngredientTag tag) {
        FoodIngredient ingredient = new FoodIngredient();
        ingredient.name = name;
        ingredient.tag = tag == null ? IngredientTag.ETC : tag;
        return ingredient;
    }

    /** 연관관계 편의 메서드에서만 호출한다. */
    void assignTo(FoodAnalysis foodAnalysis) {
        this.foodAnalysis = foodAnalysis;
    }
}
