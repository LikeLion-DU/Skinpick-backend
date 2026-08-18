package com.skinplate.api.domain.plate.engine;

/**
 * 룰 튜닝은 전부 이 파일에서 한다. (PRD v1.4 §18.6)
 *
 * Day 8 캘리브레이션 때 표준 음식 10종으로 값을 조정하게 되는데,
 * 상수가 각 Rule 클래스에 흩어져 있으면 조정이 9개 파일 수정으로 번진다.
 */
public final class RuleConstants {

    private RuleConstants() {}

    /** 모든 Plate가 여기서 출발한다. */
    public static final int BASE_SCORE = 70;

    // ---- 룰별 기본 델타 (severityFactor 적용 전) ----
    public static final int R01_HYDRATION_FOOD   =  8;   // 건조 × 수분/오메가3
    public static final int R02_SPICY_REDNESS    = -10;  // 홍조 × 매운 음식
    public static final int R03_SUGAR_TROUBLE    = -12;  // 당류 과다 — 게이트 없음, 심각도로 개인화
    // 나트륨은 초과량 비례에서 3단계로 바꿨다. 비례식은 경계 근처에서 1점씩 흔들려
    // "왜 이 점수인가" 카드가 설명하기 어려웠고, 계단은 회복치를 정확히 계산할 수 있다.
    public static final int R04_SODIUM_HIGH      = -4;   // >1150mg (p75)
    public static final int R04_SODIUM_VERY_HIGH = -8;   // >1700mg (p90)
    public static final int R04_SODIUM_EXTREME   = -12;  // >2300mg (p95)
    public static final int R05_PROTEIN          =  6;   // 단백질 충분
    public static final int R06_VITAMIN          =  5;   // 비타민/항산화
    public static final int R07_FRIED_OIL        = -10;  // 튀김/기름진 음식 — 게이트 없음, 심각도로 개인화
    public static final int R08_OMEGA3_BARRIER   =  7;   // 장벽 약화 × 오메가3
    public static final int R09_PROBIOTIC        =  4;   // 발효식품
    // 피부 지표와 직접 매지 않는 보조 룰이라 심각도 계수를 태우지 않는 고정 델타다.
    // 같은 이유로 ConcernRules 에도 매지 않는다.
    public static final int R10_HIGH_CALORIE      = -4;  // >660kcal (p90)
    public static final int R10_VERY_HIGH_CALORIE = -7;  // >790kcal (p95)
    // R11 도 같은 이유(피부 지표와 못 매단다)로 심각도 계수 없이 고정이다.
    public static final int R11_SAT_FAT           = -2;  // >3.9g  (p75)
    public static final int R11_SAT_FAT_HIGH      = -7;  // >8.2g  (p90)
    public static final int R11_SAT_FAT_EXTREME   = -10; // >12.3g (p95)
    public static final int R12_REFINED_CARB      = -4;  // HIGH_GI 태그 + 탄수 30g 이상
    // 가점 쪽 유일한 단계형. 이 룰이 "좋은 음식이 올라간다"를 담당한다.
    public static final int R15_FIBER             =  4;  // 100kcal 당 3.0g (p75)
    public static final int R15_FIBER_HIGH        =  6;  // 100kcal 당 5.0g (p90)
    // R13(생·찜 조리 +3) 과 R16(아연 가점) 은 만들지 않았다. 전자는 "날것이면 피부에 좋다"를
    // 이 룰표로 증명할 수 없고(RAW 에는 육회가, STEAMED 에는 만두가 함께 들어온다),
    // 후자는 아연 밀도가 단백질 밀도와 r=0.38 로 겹치는 데다 상위가 굴·조개·갈비탕·수육이라
    // 부담이 큰 음식에 가점을 주게 된다. 점수를 흩기 위해 근거 없는 룰을 넣지 않는다.

    // ---- 추천 행동 시 회복 점수 ----
    public static final int GAIN_LESS_SPICY      = 6;
    public static final int GAIN_WATER_NOT_SODA  = 7;
    public static final int GAIN_REMOVE_BATTER   = 5;
    // R04·R10 의 회복치는 상수가 아니다. 단계형이 되면서 "국물 절반"·"밥 줄이기"의
    // 실제 효과가 지금 단계에서 계산된다 — 옛 고정값(8·5)은 나트륨 1,200mg 짜리
    // 국에도 "+8" 이라 말했고, 시뮬레이션은 4 만 올렸다. 확인 가능한 거짓이었다.
    // 두 룰이 각자 Nutrition.sodiumTierOf · calorieTierOf 로 계산한다.

    /**
     * LESS_RICE 를 실행한 뒤 남는 열량 비율. <b>R10 과 시뮬레이션이 같은 값을 써야</b>
     * 카드가 광고한 회복치와 실제 결과가 같아진다 — 그래서 서비스가 아니라 여기 둔다.
     */
    public static final double CALORIES_AFTER_LESS_RICE = 0.75;

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
    // 여기는 델타(몇 점 깎을지)만 둔다. Nutrition.isVeryHighSugar() 를 쓴다.
    public static final int R03_SUGAR_VERY_HIGH_EXTRA    = -4;

    // ---- 점수 범위 ----
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;
}
