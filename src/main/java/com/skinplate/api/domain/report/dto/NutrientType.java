package com.skinplate.api.domain.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 리포트의 영양 밸런스 항목. 하루 기준값과 방향을 서버가 쥔다.
 *
 * <p>기준값을 앱에 하드코딩하면 조정할 때마다 앱 배포가 필요해진다
 * ({@code PlateHistoryDayDto.targetScore} 와 같은 이유). 값은 성인 1일 참고
 * 섭취량 수준이고, 정밀한 영양 상담이 아니라 막대 하나를 그리기 위한 기준이다.
 *
 * <p><b>{@link com.skinplate.api.domain.food.entity.Nutrition} 의 임계값과 다른 축이다.</b>
 * 저기는 끼니 하나를 룰 엔진이 판정하는 값(나트륨 1150mg 초과 = 감점)이고,
 * 여기는 하루치 합계를 화면에 견줄 기준이다. 한쪽을 다른 쪽에 맞추면 한 끼만
 * 기록한 날이 항상 "나트륨 정상"으로 뜬다.
 *
 * @param higherIsWorse 초과가 문제인 항목이면 true. 단백질만 부족이 문제다
 */
public enum NutrientType {

    // ---- 영양 밸런스 (하루 합계) ----
    CALORIES("칼로리",   "kcal", 2000, true,  Group.MACRO),
    CARB    ("탄수화물", "g",     300, true,  Group.MACRO),
    PROTEIN ("단백질",   "g",      55, false, Group.MACRO),
    FAT     ("지방",     "g",      50, true,  Group.MACRO),
    SODIUM  ("나트륨",   "mg",   2000, true,  Group.MACRO),
    SUGAR   ("당류",     "g",      50, true,  Group.MACRO),

    // ---- 피부 영양 포인트 ----
    // 시안이 "영양 밸런스"와 별도 카드로 갈라 놓은 셋이다. 배열도 따로 내려보낸다
    // ({@code DailyReportResponse.skinNutrients}) — 한 배열에 섞으면 단위가 g·mg·회로
    // 뒤섞이고, 무엇보다 **측정 가능 여부가 다르다**. 아래 셋은 표준 음식표에 매칭된
    // 끼니에서만 값이 나온다.
    //
    // 셋 다 higherIsWorse=false 다. 많아서 문제가 되는 양이 아니고, 화면도 "부족"을
    // 경고색으로 칠해야 한다.
    VITAMIN_C("비타민C", "mg", 100, false, Group.SKIN),
    /**
     * <b>단위가 g 이 아니라 '회' 다.</b> 표준 음식표 원본에 오메가3 지방산 컬럼이 있지만
     * 요리 계열 2,589행 중 <b>46%가 결측</b>이라 테이블 생성에서 제외돼 있다
     * ({@code tools/build_standard_food.py}). 그 값을 그대로 쓰면 절반의 음식이 0 으로
     * 들어오고, 화면은 "모른다"와 "없다"를 구분하지 못해 <b>부족이라고 단정</b>한다.
     *
     * <p>그래서 양이 아니라 <b>빈도</b>를 센다 — 그날 기록 중 {@code OMEGA3} 재료 태그가
     * 붙은 끼니 수다. 태그는 표준표에서 오거나(from_standard) AI 재료에서 오는 실제
     * 관찰값이라, 0 이면 "오늘 오메가3 식품 기록이 없다"가 사실이다.
     *
     * <p>기준 1회는 하루에 한 번은 챙기자는 뜻이고, 정밀한 영양 권고가 아니다
     * (이 enum 의 다른 기준값과 같은 성격이다).
     */
    OMEGA3("오메가3", "회", 1, false, Group.SKIN),
    ZINC("아연", "mg", 10, false, Group.SKIN);

    /** 기준의 이 비율 아래면 부족, 위면 과다. 100 에 딱 맞추는 사람은 없으므로 폭을 둔다. */
    private static final int LOW_PERCENT  = 70;
    private static final int HIGH_PERCENT = 110;

    /**
     * 어느 카드에 실리는가. 시안이 "영양 밸런스"와 "피부 영양 포인트"를 다른 카드로
     * 그리고, 두 묶음의 <b>측정 가능 여부가 다르다</b> — MACRO 는 AI 추정만으로도
     * 채워지지만 SKIN 은 표준 음식표에 매칭돼야 값이 있다.
     */
    public enum Group { MACRO, SKIN }

    private final String label;
    private final String unit;
    private final int dailyTarget;
    private final boolean higherIsWorse;
    private final Group group;

    NutrientType(String label, String unit, int dailyTarget, boolean higherIsWorse, Group group) {
        this.label = label;
        this.unit = unit;
        this.dailyTarget = dailyTarget;
        this.higherIsWorse = higherIsWorse;
        this.group = group;
    }

    public String getLabel()        { return label; }

    public String getUnit()         { return unit; }

    public int getDailyTarget()     { return dailyTarget; }

    public boolean isHigherIsWorse() { return higherIsWorse; }

    public Group getGroup()         { return group; }

    /** 선언 순서 그대로. 앱이 항목 목록을 갖지 않으므로 이 순서가 화면 순서다. */
    public static java.util.List<NutrientType> of(Group group) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.group == group)
                .toList();
    }

    /** 기준 대비 비율(%). 반올림한 정수다 — 화면이 소수점을 쓰지 않는다. */
    public int percentOf(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(dailyTarget), 0, RoundingMode.HALF_UP)
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
