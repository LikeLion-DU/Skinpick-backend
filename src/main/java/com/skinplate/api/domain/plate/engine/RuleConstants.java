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
    public static final int R07_FRIED_OIL        = -10;  // 유분 × 튀김
    public static final int R08_OMEGA3_BARRIER   =  7;   // 장벽 약화 × 오메가3
    public static final int R09_PROBIOTIC        =  4;   // 발효식품
    // ---- R10(고열량)은 미구현이다. 델타와 회복 점수를 쌍으로 남겨둔다. ----
    // 문서 §18.6 룰표가 R10을 "확장"으로 명시하고 있으므로 상수도 함께 남긴다.
    // 나중에 넣을 때 값을 다시 정하지 않아도 되고, 지금 지우면 GAIN_LESS_RICE 만
    // 고아가 되거나(둘은 한 쌍이다) 룰표와 코드가 또 어긋난다.
    public static final int R10_HIGH_CALORIE     = -5;   // 고열량 (확장 · 미구현)

    // ---- 추천 행동 시 회복 점수 ----
    public static final int GAIN_SOUP_HALF       = 8;
    public static final int GAIN_LESS_SPICY      = 6;
    public static final int GAIN_WATER_NOT_SODA  = 7;
    public static final int GAIN_REMOVE_BATTER   = 5;
    public static final int GAIN_LESS_RICE       = 4;   // R10 쌍 (확장 · 미구현)

    // ---- 나트륨 초과량 비례 감점 ----
    public static final int SODIUM_STEP_MG       = 500;  // 500mg 초과마다 1점 추가 감점
    public static final int SODIUM_MAX_PENALTY   = 15;

    // ---- 점수 범위 ----
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;
}
