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

    /**
     * 이 태그가 표준 음식표에서 온 것인가. <b>점수에 쓰이는 태그를 가르는 값이다.</b>
     *
     * <p>AI 가 사진에서 읽은 재료는 회차마다 달라진다 — 같은 떡볶이 사진에서 ANTIOXIDANT 가
     * 왔다 가고 PROBIOTIC 이 붙었다 떨어진다. 그대로 점수에 쓰면 같은 사진이 50·55·58 점을
     * 낸다. 표시는 AI 재료를 그대로 하되 점수는 표준표가 아는 태그로만 낸다.
     */
    @Column(name = "from_standard", nullable = false)
    private boolean fromStandard;

    /** AI 가 사진에서 읽은 재료. 화면에는 보이지만 점수에는 쓰이지 않는다. */
    public static FoodIngredient of(String name, IngredientTag tag) {
        return create(name, tag, false);
    }

    /** 표준 음식표가 이름으로 확정한 태그. 점수는 이것만 본다. */
    public static FoodIngredient fromStandardTable(String name, IngredientTag tag) {
        return create(name, tag, true);
    }

    /**
     * 출처까지 그대로 옮긴 사본. 시뮬레이션이 원본을 건드리지 않으려고 사본을 만드는데,
     * 여기서 출처를 잃으면 표준 유래 태그가 전부 AI 유래로 바뀌어 점수용 태그가 통째로
     * 사라진다 — before 가 저장 점수와 갈라진다.
     */
    public static FoodIngredient copyOf(FoodIngredient origin) {
        return create(origin.name, origin.tag, origin.fromStandard);
    }

    private static FoodIngredient create(String name, IngredientTag tag, boolean fromStandard) {
        FoodIngredient ingredient = new FoodIngredient();
        ingredient.name = name;
        ingredient.tag = tag == null ? IngredientTag.ETC : tag;
        ingredient.fromStandard = fromStandard;
        return ingredient;
    }

    /** 연관관계 편의 메서드에서만 호출한다. */
    void assignTo(FoodAnalysis foodAnalysis) {
        this.foodAnalysis = foodAnalysis;
    }
}
