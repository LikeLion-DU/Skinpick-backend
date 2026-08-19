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
 * <p><b>기록이 하나라도 있는 날은 여섯 항목이 모두 온다</b>(값이 0 이어도 뺴지 않는다 —
 * 날마다 막대 수가 달라지면 화면이 흔들린다). 기록이 <b>아예 없는</b> 날만 빈 배열이다 —
 * 0 짜리 막대 여섯 개는 "안 먹었다"로 읽히는데 사실은 "안 찍었다"이기 때문이다.
 *
 * @param amount  그날(주간이면 하루 평균) 합계. 소수 첫째 자리까지
 * @param percent 기준 대비 비율(%)
 * @param status  기준 대비 위치. <b>null 이면 "재지 못했다"</b>는 뜻이다 —
 *                {@link NutrientType.Group#SKIN} 항목은 표준 음식표에 매칭된 끼니에서만
 *                값이 나오므로, 그날 매칭된 기록이 하나도 없으면 0 이 아니라 null 이다.
 *                0 을 내려보내면 화면이 "부족"이라고 단정하는데 사실은 모르는 것이다
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

    /**
     * 값을 재지 못한 항목. 막대는 비고 상태어 자리는 비운다.
     *
     * <p><b>항목을 배열에서 빼지 않는 이유</b>는 여섯 항목을 늘 함께 내려보내는 것과
     * 같다 — 날마다 타일 수가 달라지면 화면이 흔들리고, 무엇보다 "이 영양소를 재지
     * 못했다"는 것 자체가 사용자가 알아야 할 정보다(표준 음식표에 없는 음식을 먹었다).
     */
    public static NutritionItemDto unmeasured(NutrientType nutrient) {
        return new NutritionItemDto(nutrient, nutrient.getLabel(), nutrient.getUnit(),
                BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY),
                nutrient.getDailyTarget(), 0, null, nutrient.isHigherIsWorse());
    }
}
