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

    /** 델타에 계수를 적용하고 반올림한다. */
    public static int apply(int delta, int metricValue, boolean higherIsWorse) {
        return (int) Math.round(delta * of(metricValue, higherIsWorse));
    }
}
