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

    /**
     * 컬럼이 NUMERIC(6,2) 라 9999.99 를 넘으면 저장에서 `numeric field overflow` 가 난다.
     * 그 시점엔 25초짜리 유료 호출이 이미 끝나 있고 공짜로 다시 부를 수 없다.
     *
     * 정수는 원래 잘라 담고 있었는데 소수는 그냥 통과하고 있었다. AI 가 carbG 를
     * 12000 으로 답하는 일은 드물지만, 드물게 나는 500 은 하필 시연 중에 난다.
     * 이 파일의 다른 방어(varchar 자르기·모르는 enum 은 ETC)와 같은 방식으로 자른다.
     */
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("9999.99");

    public static Nutrition of(int caloriesKcal, BigDecimal proteinG, BigDecimal fatG,
                               BigDecimal carbG, int sodiumMg, BigDecimal sugarG) {
        return new Nutrition(
                Math.max(0, caloriesKcal),
                clamp(proteinG), clamp(fatG), clamp(carbG),
                Math.max(0, sodiumMg), clamp(sugarG));
    }

    /** 0 미만은 0 으로, 컬럼 상한을 넘으면 상한으로. 소수 자리도 컬럼에 맞춘다. */
    private static BigDecimal clamp(BigDecimal value) {
        BigDecimal bounded = nonNull(value).max(BigDecimal.ZERO).min(MAX_DECIMAL);

        // scale 이 2 를 넘으면 Postgres 가 반올림해 넣지만, 엔티티와 DB 값이 달라져
        // 같은 요청을 다시 읽었을 때 숫자가 미묘하게 바뀐다. 여기서 맞춰 둔다.
        return bounded.setScale(2, java.math.RoundingMode.HALF_UP);
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
