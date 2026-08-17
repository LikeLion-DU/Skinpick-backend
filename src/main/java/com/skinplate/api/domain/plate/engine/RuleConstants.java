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
    public static final int R03_SUGAR_TROUBLE    = -12;  // 트러블 × 당류 과다
    public static final int R04_SODIUM           = -8;   // 나트륨 과다
    public static final int R05_PROTEIN          =  6;   // 단백질 충분
    public static final int R06_VITAMIN          =  5;   // 비타민/항산화
    public static final int R07_FRIED_OIL        = -10;  // 유분 × 튀김/기름진 음식
    public static final int R08_OMEGA3_BARRIER   =  7;   // 장벽 약화 × 오메가3
    public static final int R09_PROBIOTIC        =  4;   // 발효식품
    // 피부 지표와 직접 매지 않는 보조 룰이라 심각도 계수를 태우지 않는 고정 델타다.
    // 시연 음식(520·610kcal)에는 걸리지 않아 예시 60·87 이 그대로다.
    public static final int R10_HIGH_CALORIE     = -5;   // 고열량

    // ---- 추천 행동 시 회복 점수 ----
    public static final int GAIN_SOUP_HALF       = 8;
    public static final int GAIN_LESS_SPICY      = 6;
    public static final int GAIN_WATER_NOT_SODA  = 7;
    public static final int GAIN_REMOVE_BATTER   = 5;
    // R10 은 고정 -5 이고 LESS_RICE 가 1200kcal 아래에서는 반드시 그 룰을 끈다 —
    // 회복치가 결정론적으로 5 다. 다른 GAIN 들은 감점이 가변(R04 는 -8~-15)이라
    // 근사가 불가피하지만, 여기서 4 를 쓰면 카드가 "+4" 라 말하고 시뮬레이션은
    // 5 를 올리는, 확인 가능한 거짓이 된다.
    public static final int GAIN_LESS_RICE       = 5;   // R10 쌍 · |R10_HIGH_CALORIE| 와 같다

    // ---- 나트륨 초과량 비례 감점 ----
    public static final int SODIUM_STEP_MG       = 500;  // 500mg 초과마다 1점 추가 감점
    public static final int SODIUM_MAX_PENALTY   = 15;

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
    // 당류는 VERY_HIGH 에서 감점을 더한다. 25~40g 구간은 기존과 동일하다.
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
