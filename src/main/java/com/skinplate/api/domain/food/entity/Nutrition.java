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

    /**
     * 나트륨 1단계. 표준 음식 1,452종의 p75 다(중앙값 688 · p90 1,822).
     * 옛 1,500mg 은 p85 라 6종 중 1종만 걸렸고, 한 끼에 하루 권장량의 75% 를 허용하는 값이었다.
     */
    public static final int SODIUM_THRESHOLD_MG   = 1150;
    public static final int PROTEIN_THRESHOLD_G   = 20;     // 이상이면 가점
    /**
     * 초과 시 감점(R03). 25g 은 표준 음식 1,452종에서 <b>상위 2.5%</b>(p97.5)라
     * 사실상 아무것도 걸리지 않던 값이다 — 당류 33g 짜리 국물떡볶이조차 비켜 갔다.
     * 15g 은 p92 근처이고, 이 표의 당류 중앙값이 2.8g · p75 가 6.4g 이다.
     */
    public static final int SUGAR_THRESHOLD_G     = 15;     // 초과 시 감점
    /** 열량 1단계. 표준 음식의 p90 이다(중앙값 268 · p95 791). 옛 900kcal 은 p97 이었다. */
    public static final int CALORIES_THRESHOLD    = 660;    // 초과 시 감점 (R10)

    // 단계화 경계. 임계값은 전부 이 클래스가 소유한다 — 룰이 제 안에 숫자를 들면
    // "당류를 몇 g부터 많다고 보는가"의 답이 파일마다 달라진다.
    // 델타(몇 점 깎을지)는 RuleConstants 몫이다. 그 둘은 다른 축이다.
    public static final int SUGAR_VERY_HIGH_G     = 40;     // 초과 시 R03 추가 감점
    public static final int SODIUM_VERY_HIGH_MG   = 1700;   // 나트륨 2단계 (p90 근처)
    public static final int SODIUM_EXTREME_MG     = 2300;   // 나트륨 3단계 (p95 근처)
    public static final int CALORIES_VERY_HIGH    = 790;    // 열량 2단계 (p95)

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

    public boolean isHighSodium()  { return sodiumTier() > 0; }
    public boolean isHighSugar()   { return sugarG.compareTo(BigDecimal.valueOf(SUGAR_THRESHOLD_G)) > 0; }
    public boolean isHighProtein() { return proteinG.compareTo(BigDecimal.valueOf(PROTEIN_THRESHOLD_G)) >= 0; }
    public boolean isHighCalorie() { return calorieTier() > 0; }

    /** isHighSugar 를 이미 통과한 뒤 한 단계 더 보는 값이다. 15~40g 은 false. */
    public boolean isVeryHighSugar()  { return sugarG.compareTo(BigDecimal.valueOf(SUGAR_VERY_HIGH_G)) > 0; }

    /** R04 문구의 수식어("매우 높은"/"높은")를 가른다. 단계 2 이상이 그것이다. */
    public boolean isVeryHighSodium() { return sodiumTier() >= 2; }

    /**
     * 나트륨 단계 0~3. <b>경계는 이 클래스가, 델타는 RuleConstants 가 소유한다.</b>
     *
     * <p>임의의 mg 를 받는 static 이 함께 있는 이유는 R04 가 "국물을 절반만 남기면
     * 몇 점 오르는가"를 <b>계산해서</b> 말해야 하기 때문이다. 단계를 룰 안에서 다시 세면
     * 경계가 두 파일로 갈리고, 카드가 광고한 회복치와 시뮬레이션 결과가 언젠가 어긋난다.
     */
    public static int sodiumTierOf(int sodiumMg) {
        if (sodiumMg > SODIUM_EXTREME_MG)   return 3;
        if (sodiumMg > SODIUM_VERY_HIGH_MG) return 2;
        if (sodiumMg > SODIUM_THRESHOLD_MG) return 1;
        return 0;
    }

    /** 열량 단계 0~2. 위와 같은 이유로 static 을 함께 둔다(LESS_RICE 회복치 계산). */
    public static int calorieTierOf(int caloriesKcal) {
        if (caloriesKcal > CALORIES_VERY_HIGH) return 2;
        if (caloriesKcal > CALORIES_THRESHOLD) return 1;
        return 0;
    }

    public int sodiumTier()  { return sodiumTierOf(sodiumMg); }
    public int calorieTier() { return calorieTierOf(caloriesKcal); }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
