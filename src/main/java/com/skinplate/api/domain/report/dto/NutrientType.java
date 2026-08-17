package com.skinplate.api.domain.report.dto;

import java.math.BigDecimal;

/**
 * 리포트의 영양 밸런스 항목. 하루 기준값과 방향을 서버가 쥔다.
 *
 * <p>기준값을 앱에 하드코딩하면 조정할 때마다 앱 배포가 필요해진다
 * ({@code PlateHistoryDayDto.targetScore} 와 같은 이유). 값은 성인 1일 참고
 * 섭취량 수준이고, 정밀한 영양 상담이 아니라 막대 하나를 그리기 위한 기준이다.
 *
 * <p><b>{@link com.skinplate.api.domain.food.entity.Nutrition} 의 임계값과 다른 축이다.</b>
 * 저기는 끼니 하나를 룰 엔진이 판정하는 값(나트륨 1500mg 초과 = 감점)이고,
 * 여기는 하루치 합계를 화면에 견줄 기준이다. 한쪽을 다른 쪽에 맞추면 한 끼만
 * 기록한 날이 항상 "나트륨 정상"으로 뜬다.
 *
 * @param higherIsWorse 초과가 문제인 항목이면 true. 단백질만 부족이 문제다
 */
public enum NutrientType {

    CALORIES("칼로리",   "kcal", 2000, true),
    CARB    ("탄수화물", "g",     300, true),
    PROTEIN ("단백질",   "g",      55, false),
    FAT     ("지방",     "g",      50, true),
    SODIUM  ("나트륨",   "mg",   2000, true),
    SUGAR   ("당류",     "g",      50, true);

    /** 기준의 이 비율 아래면 부족, 위면 과다. 100 에 딱 맞추는 사람은 없으므로 폭을 둔다. */
    private static final int LOW_PERCENT  = 70;
    private static final int HIGH_PERCENT = 110;

    private final String label;
    private final String unit;
    private final int dailyTarget;
    private final boolean higherIsWorse;

    NutrientType(String label, String unit, int dailyTarget, boolean higherIsWorse) {
        this.label = label;
        this.unit = unit;
        this.dailyTarget = dailyTarget;
        this.higherIsWorse = higherIsWorse;
    }

    public String getLabel()        { return label; }

    public String getUnit()         { return unit; }

    public int getDailyTarget()     { return dailyTarget; }

    public boolean isHigherIsWorse() { return higherIsWorse; }

    /** 기준 대비 비율(%). 반올림한 정수다 — 화면이 소수점을 쓰지 않는다. */
    public int percentOf(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(dailyTarget), 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }

    public Status statusOf(BigDecimal amount) {
        int percent = percentOf(amount);
        if (percent < LOW_PERCENT)  return Status.LOW;
        if (percent > HIGH_PERCENT) return Status.HIGH;
        return Status.NORMAL;
    }

    /**
     * 좋고 나쁨이 아니라 위치다. 어느 쪽이 나쁜지는 {@link #higherIsWorse} 가 말한다 —
     * 단백질 LOW 와 나트륨 LOW 를 같은 색으로 칠하면 화면이 거짓말을 한다.
     */
    public enum Status { LOW, NORMAL, HIGH }
}
