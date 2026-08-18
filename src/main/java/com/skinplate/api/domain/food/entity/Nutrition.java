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

    /**
     * 포화지방 단계 (R11). 표준 음식 1,443종 1인분의 p75 / p90 / p95 다.
     *
     * <p><b>총지방이 아니라 포화지방인 것이 요점이다.</b> 총지방으로 재면 연어(28g)와
     * 스테이크(39g)가 같은 단계에 들어가는데, 포화지방으로 보면 연어 4.4g · 스테이크 12.7g 로
     * 갈린다. 총지방을 쓰던 시절에는 이 한계를 "OMEGA3 태그면 한 단계 완화" 라는 대리
     * 지표로 메웠고, 이제 실측값이 있으니 그 우회로가 통째로 필요 없다.
     */
    public static final double SAT_FAT_THRESHOLD_G = 3.9;
    public static final double SAT_FAT_HIGH_G      = 8.2;
    public static final double SAT_FAT_EXTREME_G   = 12.3;

    /**
     * R12(정제탄수)가 HIGH_GI 태그와 <b>함께</b> 보는 값.
     *
     * <p>태그만으로 걸면 감자 된장국(70kcal · 탄수 10.6g)이 감점을 먹는다 — 태깅이
     * 재료 이름을 보기 때문이다. "정제 탄수 재료가 들었다"(태그)와 "실제로 많이 들었다"(이 값)
     * 두 신호가 다 있을 때만 깎는다.
     */
    public static final int REFINED_CARB_MIN_G = 30;

    /**
     * 가점 룰이 쓰는 <b>영양 밀도</b> 경계 — 100kcal 당 함량이다. 표준 음식 1,443종의
     * p75 / p90 을 반올림했다.
     *
     * <p><b>절대량이 아니라 밀도인 이유.</b> 절대량으로 재면 감자튀김(식이섬유 5.8g)이
     * 샐러드(3.8g)를 이긴다 — 감자튀김이 468kcal 이고 샐러드가 293kcal 이기 때문이다.
     * 그러면 점수가 "이 음식이 얼마나 좋은가"가 아니라 "얼마나 큰가"를 재게 된다.
     * 밀도로 보면 콩나물무침 4.8 · 샐러드 1.3 · 떡볶이 0.7 · 돈가스 0.4 로 상식과 맞는다.
     *
     * <p>감점 쪽은 절대량 그대로다. 부담은 실제로 먹은 총량이 만들고, 밀도로 바꾸면
     * 1,000kcal 짜리 한 끼가 "밀도는 낮으니 괜찮다"로 빠져나간다.
     */
    public static final double FIBER_DENSITY_THRESHOLD     = 3.0;   // p75 3.08
    public static final double FIBER_DENSITY_HIGH          = 5.0;   // p90 5.11
    public static final double VITAMIN_A_DENSITY_THRESHOLD = 36.0;  // p75 36.8 (μg RAE)
    public static final double VITAMIN_C_DENSITY_THRESHOLD = 4.0;   // p75 4.17 (mg)

    @Column(name = "calories_kcal", nullable = false) private int caloriesKcal;
    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2) private BigDecimal proteinG;
    @Column(name = "fat_g",     nullable = false, precision = 6, scale = 2) private BigDecimal fatG;
    @Column(name = "carb_g",    nullable = false, precision = 6, scale = 2) private BigDecimal carbG;
    @Column(name = "sodium_mg", nullable = false) private int sodiumMg;
    @Column(name = "sugar_g",   nullable = false, precision = 6, scale = 2) private BigDecimal sugarG;

    // ---- 표준 영양 확장 (V9) ----
    // 전부 "모르면 0" 이다. 이 다섯을 보는 룰이 전부 "값이 클수록 발동" 이라
    // 모르는 값은 어느 쪽으로도 점수를 움직이지 않는다. AI 스키마에는 이 다섯이 없으므로
    // 표준 테이블에서 못 찾은 음식은 항상 0 으로 남는다.
    @Column(name = "saturated_fat_g", nullable = false, precision = 6, scale = 2) private BigDecimal saturatedFatG;
    @Column(name = "fiber_g",      nullable = false, precision = 6, scale = 2) private BigDecimal fiberG;
    @Column(name = "vitamin_a_ug", nullable = false) private int vitaminAUg;
    @Column(name = "vitamin_c_mg", nullable = false, precision = 6, scale = 2) private BigDecimal vitaminCMg;
    @Column(name = "zinc_mg",      nullable = false, precision = 6, scale = 2) private BigDecimal zincMg;

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
                Math.max(0, sodiumMg), clamp(sugarG),
                BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 표준 영양 확장 다섯 개를 얹은 사본. <b>여섯 인자짜리 of() 를 열한 개로 늘리지 않는
     * 이유</b>는 이 다섯이 늘 함께 오고 늘 함께 비기 때문이다 — 표준 테이블에서 찾으면
     * 다섯 다 있고, 못 찾으면 다섯 다 없다. 인자 열한 개를 순서로 맞추는 호출부보다
     * 이쪽이 잘못 넣기 어렵다.
     */
    public Nutrition withMicronutrients(BigDecimal saturatedFatG, BigDecimal fiberG,
                                        Integer vitaminAUg, BigDecimal vitaminCMg,
                                        BigDecimal zincMg) {
        return new Nutrition(
                this.caloriesKcal, this.proteinG, this.fatG, this.carbG, this.sodiumMg, this.sugarG,
                clamp(saturatedFatG), clamp(fiberG),
                vitaminAUg == null ? 0 : Math.max(0, vitaminAUg),
                clamp(vitaminCMg), clamp(zincMg));
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

    /** 당류 단계 0~2. 위 둘과 같은 이유로 static 을 함께 둔다(NO_SUGAR_DRINK 회복치 계산). */
    public static int sugarTierOf(BigDecimal sugarG) {
        if (sugarG.compareTo(BigDecimal.valueOf(SUGAR_VERY_HIGH_G)) > 0) return 2;
        if (sugarG.compareTo(BigDecimal.valueOf(SUGAR_THRESHOLD_G)) > 0)  return 1;
        return 0;
    }

    public int sodiumTier()  { return sodiumTierOf(sodiumMg); }
    public int calorieTier() { return calorieTierOf(caloriesKcal); }
    public int sugarTier()   { return sugarTierOf(sugarG); }

    /** 포화지방 단계 0~3. 값이 없으면(=0) 0 이라 R11 이 발동하지 않는다. */
    public int saturatedFatTier() {
        double grams = saturatedFatG.doubleValue();
        if (grams > SAT_FAT_EXTREME_G)   return 3;
        if (grams > SAT_FAT_HIGH_G)      return 2;
        if (grams > SAT_FAT_THRESHOLD_G) return 1;
        return 0;
    }

    /** HIGH_GI 태그가 붙은 음식에서 "실제로 정제 탄수가 많은가"를 되묻는다. */
    public boolean hasRefinedCarbLoad() {
        return carbG.compareTo(BigDecimal.valueOf(REFINED_CARB_MIN_G)) >= 0;
    }

    /** 식이섬유 단계 0~2 (밀도 기준). 값을 모르면(=0) 0 이라 R15 가 발동하지 않는다. */
    public int fiberTier() {
        double density = densityOf(fiberG.doubleValue());
        if (density >= FIBER_DENSITY_HIGH)      return 2;
        if (density >= FIBER_DENSITY_THRESHOLD) return 1;
        return 0;
    }

    /**
     * 실측 비타민이 풍부한가. A 와 C 중 <b>하나만 넘어도</b> 참이다 — 둘 다 요구하면
     * 나물(A 는 높고 C 는 낮다)과 생채소(그 반대)가 함께 떨어진다.
     */
    public boolean isVitaminRich() {
        return densityOf(vitaminAUg) >= VITAMIN_A_DENSITY_THRESHOLD
                || densityOf(vitaminCMg.doubleValue()) >= VITAMIN_C_DENSITY_THRESHOLD;
    }

    /** 100kcal 당 함량. 열량이 0 이면 나눌 수 없으므로 0 으로 본다(=모른다). */
    private double densityOf(double amount) {
        return caloriesKcal <= 0 ? 0.0 : amount * 100.0 / caloriesKcal;
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
