package com.skinplate.api.domain.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 영양 밸런스 막대 하나.
 *
 * <p>평평한 필드(calories, protein …) 대신 배열로 내려보낸다. 항목이 늘어도 앱이
 * 그리는 코드는 그대로고, 라벨·단위·기준값을 서버가 함께 실어 보내므로 앱에
 * 영양 상수가 하나도 남지 않는다.
 *
 * <p><b>기록된 끼니만 센다.</b> 점심 한 끼만 찍은 날은 칼로리가 당연히 LOW 로 뜬다 —
 * 안 먹은 게 아니라 안 찍은 것이고, 서버는 그 차이를 알 방법이 없다.
 *
 * @param amount  그날(주간이면 하루 평균) 합계. 소수 첫째 자리까지
 * @param percent 기준 대비 비율(%)
 */
public record NutritionItemDto(NutrientType nutrient, String label, String unit,
                               BigDecimal amount, int target, int percent,
                               NutrientType.Status status, boolean higherIsWorse) {

    public static NutritionItemDto of(NutrientType nutrient, BigDecimal amount) {
        BigDecimal rounded = amount.setScale(1, RoundingMode.HALF_UP);

        return new NutritionItemDto(nutrient, nutrient.getLabel(), nutrient.getUnit(),
                rounded, nutrient.getDailyTarget(), nutrient.percentOf(rounded),
                nutrient.statusOf(rounded), nutrient.isHigherIsWorse());
    }
}
