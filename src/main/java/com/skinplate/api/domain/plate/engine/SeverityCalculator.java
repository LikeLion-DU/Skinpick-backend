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
    private static final double NORMAL   = 1.0;

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
        return NORMAL;
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
}
