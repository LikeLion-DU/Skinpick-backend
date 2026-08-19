package com.skinplate.api.domain.plate.engine;

import java.math.BigDecimal;

/**
 * 룰 튜닝은 전부 이 파일에서 한다. (PRD v1.4 §18.6)
 *
 * Day 8 캘리브레이션 때 표준 음식 10종으로 값을 조정하게 되는데,
 * 상수가 각 Rule 클래스에 흩어져 있으면 조정이 9개 파일 수정으로 번진다.
 */
public final class RuleConstants {

    private RuleConstants() {}

    /**
     * 모든 Plate가 여기서 출발한다.
     *
     * <p><b>2026-08-20 재캘리브레이션 — 70 → 75.</b> 실사용 첫 이틀의 기록 전부가
     * 53~75 에 몰렸다. 감점 룰은 6개, 가점 룰은 5개인데 가점 쪽 델타가 작아서
     * "잘 먹었다"가 점수로 안 보였다 — 연어 샐러드가 68점이면 화면이 사용자의
     * 선택을 부정한다. 출발점을 올리고(전 구간 +5) 가점 델타를 키워 좋은 한 끼가
     * 80 후반~90 대에 닿게 했다. 나쁜 한 끼는 감점이 그대로라 55~60 대에 남는다.
     */
    public static final int BASE_SCORE = 75;

    // ---- 룰별 기본 델타 (severityFactor 적용 전) ----
    public static final int R01_HYDRATION_FOOD   =  8;   // 건조 × 수분/오메가3
    public static final int R02_SPICY_REDNESS    = -10;  // 홍조 × 매운 음식
    public static final int R03_SUGAR_TROUBLE    = -12;  // 당류 과다 — 게이트 없음, 심각도로 개인화
    // 나트륨은 초과량 비례에서 3단계로 바꿨다. 비례식은 경계 근처에서 1점씩 흔들려
    // "왜 이 점수인가" 카드가 설명하기 어려웠고, 계단은 회복치를 정확히 계산할 수 있다.
    public static final int R04_SODIUM_HIGH      = -4;   // >1150mg (p75)
    public static final int R04_SODIUM_VERY_HIGH = -8;   // >1700mg (p90)
    public static final int R04_SODIUM_EXTREME   = -12;  // >2300mg (p95)
    public static final int R05_PROTEIN          =  7;   // 단백질 충분
    public static final int R06_VITAMIN          =  7;   // 비타민/항산화
    public static final int R07_FRIED_OIL        = -10;  // 튀김/기름진 음식 — 게이트 없음, 심각도로 개인화
    public static final int R08_OMEGA3_BARRIER   =  7;   // 장벽 약화 × 오메가3
    public static final int R09_PROBIOTIC        =  5;   // 발효식품
    // 피부 지표와 직접 매지 않는 보조 룰이라 심각도 계수를 태우지 않는 고정 델타다.
    // 같은 이유로 ConcernRules 에도 매지 않는다.
    public static final int R10_HIGH_CALORIE      = -4;  // >660kcal (p90)
    public static final int R10_VERY_HIGH_CALORIE = -7;  // >790kcal (p95)
    // R11 도 같은 이유(피부 지표와 못 매단다)로 심각도 계수 없이 고정이다.
    public static final int R11_SAT_FAT           = -2;  // >3.9g  (p75)
    public static final int R11_SAT_FAT_HIGH      = -7;  // >8.2g  (p90)
    public static final int R11_SAT_FAT_EXTREME   = -10; // >12.3g (p95)
    public static final int R12_REFINED_CARB      = -4;  // HIGH_GI 태그 + 탄수 30g 이상
    // 이 두 룰이 "좋은 음식이 올라간다"를 담당한다. 재캘리브레이션에서 가장 크게
    // 올렸다 — 등푸른생선과 채소·나물은 이 룰표가 아는 가장 확실한 "피부에 좋은" 신호다.
    public static final int R14_OMEGA3_FOOD       =  9;  // 오메가3 재료 — 게이트 없음
    public static final int R15_FIBER             =  6;  // 100kcal 당 3.0g (p75)
    public static final int R15_FIBER_HIGH        =  9;  // 100kcal 당 5.0g (p90)
    // R13(생·찜 조리 +3) 과 R16(아연 가점) 은 만들지 않았다. 전자는 "날것이면 피부에 좋다"를
    // 이 룰표로 증명할 수 없고(RAW 에는 육회가, STEAMED 에는 만두가 함께 들어온다),
    // 후자는 아연 밀도가 단백질 밀도와 r=0.38 로 겹치는 데다 상위가 굴·조개·갈비탕·수육이라
    // 부담이 큰 음식에 가점을 주게 된다. 점수를 흩기 위해 근거 없는 룰을 넣지 않는다.

    // ---- 추천 행동 시 회복 점수 ----
    // **여기에 회복치 상수는 없다.** 옛 고정값(8·6·7·5)은 나트륨 1,200mg 짜리 국에도
    // "+8" 이라 말했고 시뮬레이션은 4 만 올렸다 — 확인 가능한 거짓이었다. 다섯 카드가
    // 각자 실제 회복치를 계산한다.
    //
    //   R03·R04·R10  행동 뒤 단계를 다시 세어 그 차이 (Nutrition 의 sugarTierOf ·
    //                sodiumTierOf · calorieTierOf). 단계가 안 내려가면 카드도 안 준다
    //   R02·R07      행동이 룰을 통째로 끄므로 회복치가 곧 감점의 절댓값이다.
    //                심각도·강도 계수가 이미 곱해진 값이라 상수로 둘 수 없다

    /**
     * LESS_RICE 를 실행한 뒤 남는 열량 비율. <b>R10 과 시뮬레이션이 같은 값을 써야</b>
     * 카드가 광고한 회복치와 실제 결과가 같아진다 — 그래서 서비스가 아니라 여기 둔다.
     */
    public static final double CALORIES_AFTER_LESS_RICE = 0.75;

    /**
     * NO_SUGAR_DRINK 를 실행한 뒤 남는 당류 비율. 위와 같은 이유로 여기 둔다 —
     * R03 이 카드에 싣는 회복치와 시뮬레이션이 같은 식을 써야 한다.
     */
    public static final BigDecimal SUGAR_AFTER_NO_DRINK = new BigDecimal("0.4");

    // ---- 음식 특성 강도 계수 (스키마 v2) ----
    // SeverityCalculator 의 피부 축과 직교로 곱는 음식 축이다.
    // MEDIUM·UNKNOWN·NONE(캡사이신으로 발동한 경우)은 1.0 — 특성이 없던 시절과
    // 완전히 같은 점수가 나온다(하위 호환 불변식). AI 유래 값이라 표준 테이블의
    // 재현성 밖이므로 폭을 ±30% 로 상한한다 — 판정이 갈려도 점수가 뛰지 않게.
    public static final double SPICINESS_MILD_FACTOR     = 0.7;
    public static final double SPICINESS_HOT_FACTOR      = 1.3;
    // 튀김이 아닌데 기름진 음식(삼겹살 구이)은 튀김보다 약하게 — R07 확장 트리거.
    public static final double OILINESS_NON_FRIED_FACTOR = 0.7;

    // ---- 영양 단계화 ----
    // 당류는 VERY_HIGH(40g 초과)에서 감점을 더한다. 15~40g 은 기본 델타 그대로다.
    //
    // 경계값(몇 g부터 많다고 보는가)은 여기 없다 — Nutrition 이 SODIUM/PROTEIN/
    // SUGAR/CALORIES 임계값을 이미 전부 쥐고 있고, 단계 경계만 이리 떼어 오면
    // "당류를 몇 g부터 많다고 보는가"의 답이 두 파일로 갈린다.
    // 여기는 델타(몇 점 깎을지)만 둔다. 단계는 Nutrition.sugarTierOf() 가 센다.
    public static final int R03_SUGAR_VERY_HIGH_EXTRA    = -4;

    // ---- 점수 범위 ----
    // 상한이 100 이 아닌 이유: 만점은 "완벽한 음식"이라는 주장이 되고, 그 주장은
    // 이 룰표로 증명할 수 없다. 최고의 한 끼(오메가3·단백질·비타민·발효가 전부
    // 선 접시)가 97 에 닿고, 3점은 언제나 남는다.
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 97;
}
