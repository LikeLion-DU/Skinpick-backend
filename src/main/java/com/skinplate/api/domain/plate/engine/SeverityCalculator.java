package com.skinplate.api.domain.plate.engine;

/**
 * 피부 지표가 심각할수록 룰 델타를 키운다.
 *
 * 홍조 62인 사람과 88인 사람에게 같은 라면 점수를 주면 "개인화"라는 말이 무너진다.
 * 심사에서 가장 먼저 나오는 질문이 "이게 진짜 내 피부에 맞춘 건가요?"이고,
 * 이 계수 하나가 그 질문에 대한 답이다.
 */
public final class SeverityCalculator {

    private SeverityCalculator() {}

    private static final double SEVERE   = 1.5;
    private static final double MODERATE = 1.2;

    /**
     * 지표가 정상 범위일 때. <b>1.0 이 아니라 0.6 인 것이 요점이다.</b>
     *
     * 이 구간이 없던 시절 R03(당류)·R07(튀김)은 피부 게이트 뒤에 있어서 트러블·유분이
     * 정상이면 <b>발동 자체를 안 했다</b> — 감자튀김과 닭가슴살 샐러드가 똑같이 70점이었다.
     * 게이트를 떼는 대신 이 값을 두어 "정상 피부에서도 음식 자체의 부담은 남기되, 피부가
     * 나쁠수록 더 크게 깎는다"로 바꾼다. 튀김 -10 기준으로 정상 -6 · 중간 -12 · 심함 -15 다.
     *
     * <b>게이트가 남아 있는 룰의 점수는 하나도 바뀌지 않는다.</b> 그 룰들의 임계값이 전부
     * 심각도 60 이상에 놓여 있기 때문이다 — 홍조>60 → 심각도>60, 수분<40 → 심각도>60,
     * 장벽<40 → 심각도>60. 이 구간에 닿는 게이트 룰이 없다.
     */
    private static final double MILD = 0.6;

    private static final int SEVERE_THRESHOLD   = 80;
    private static final int MODERATE_THRESHOLD = 60;

    /**
     * @param metricValue   0~100 지표 값
     * @param higherIsWorse oil / redness / trouble 이면 true,
     *                      hydration / barrier 이면 false
     */
    public static double of(int metricValue, boolean higherIsWorse) {
        int severity = higherIsWorse ? metricValue : (100 - metricValue);

        if (severity >= SEVERE_THRESHOLD)   return SEVERE;
        if (severity >= MODERATE_THRESHOLD) return MODERATE;
        return MILD;
    }

    /**
     * 델타에 계수를 적용하고 반올림한다.
     *
     * Math.round 는 floor(x+0.5) 라 음수 .5 에서 0 쪽으로 붙는다 — Math.round(-16.5) 는 -16 이다.
     * 감점 룰에서 이러면 룰표에 적힌 것보다 1점 약하게 적용되고, 그 사실이 어디에도 드러나지 않는다.
     * 절댓값으로 반올림한 뒤 부호를 되돌리면 "감점 상수는 짝수로 유지" 같은 관례가 필요 없어진다.
     */
    public static int apply(int delta, int metricValue, boolean higherIsWorse) {
        return apply(delta, metricValue, higherIsWorse, 1.0);
    }

    /**
     * 피부 축(심각도)과 직교인 <b>음식 축(강도)</b>을 함께 곱는다 — 스키마 v2 의
     * spiciness·oiliness 가 여기로 들어온다. 강도 1.0 이면 위 3인자와 완전히 같다.
     */
    public static int apply(int delta, int metricValue, boolean higherIsWorse, double intensity) {
        double raw = delta * of(metricValue, higherIsWorse) * intensity;
        return (int) (raw < 0 ? -Math.round(-raw) : Math.round(raw));
    }

    /**
     * reason 문장의 수식어("많이 높은"/"높은")를 가르는 데 쓴다. 문장을 위해 임계값을
     * 룰마다 다시 적으면 of() 의 경계와 어긋나는 날이 온다 — 판정과 문장은 같은 경계를 쓴다.
     */
    public static boolean isSevere(int metricValue, boolean higherIsWorse) {
        return of(metricValue, higherIsWorse) == SEVERE;
    }

    /**
     * 지표가 정상 범위인가. <b>게이트를 뗀 룰이 문장을 고르는 데 쓴다</b> — 트러블이 정상인
     * 사람에게 "지금 트러블 지표가 올라와 있는 상태에서" 라고 말하면 그건 거짓이다.
     *
     * {@link #isSevere} 와 같은 이유로 of() 를 되물어 본다. 문장을 위해 임계값을 룰마다
     * 다시 적으면 판정과 문장의 경계가 어긋나는 날이 온다.
     */
    public static boolean isMild(int metricValue, boolean higherIsWorse) {
        return of(metricValue, higherIsWorse) == MILD;
    }
}
