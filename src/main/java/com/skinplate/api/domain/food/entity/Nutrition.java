package com.skinplate.api.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nutrition {

    public static final int SODIUM_THRESHOLD_MG   = 1500;   // 초과 시 감점
    public static final int PROTEIN_THRESHOLD_G   = 20;     // 이상이면 가점
    public static final int SUGAR_THRESHOLD_G     = 25;     // 초과 시 감점
    public static final int CALORIES_THRESHOLD    = 900;    // 초과 시 감점 (확장 룰)

    @Column(name = "calories_kcal", nullable = false) private int caloriesKcal;
    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2) private BigDecimal proteinG;
    @Column(name = "fat_g",     nullable = false, precision = 6, scale = 2) private BigDecimal fatG;
    @Column(name = "carb_g",    nullable = false, precision = 6, scale = 2) private BigDecimal carbG;
    @Column(name = "sodium_mg", nullable = false) private int sodiumMg;
    @Column(name = "sugar_g",   nullable = false, precision = 6, scale = 2) private BigDecimal sugarG;

    public static Nutrition of(int caloriesKcal, BigDecimal proteinG, BigDecimal fatG,
                               BigDecimal carbG, int sodiumMg, BigDecimal sugarG) {
        return new Nutrition(
                Math.max(0, caloriesKcal),
                nonNull(proteinG), nonNull(fatG), nonNull(carbG),
                Math.max(0, sodiumMg), nonNull(sugarG));
    }

    // ---- 판정 ----

    public boolean isHighSodium()  { return sodiumMg > SODIUM_THRESHOLD_MG; }
    public boolean isHighSugar()   { return sugarG.compareTo(BigDecimal.valueOf(SUGAR_THRESHOLD_G)) > 0; }
    public boolean isHighProtein() { return proteinG.compareTo(BigDecimal.valueOf(PROTEIN_THRESHOLD_G)) >= 0; }
    public boolean isHighCalorie() { return caloriesKcal > CALORIES_THRESHOLD; }

    public int sodiumExcessMg() { return Math.max(0, sodiumMg - SODIUM_THRESHOLD_MG); }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
