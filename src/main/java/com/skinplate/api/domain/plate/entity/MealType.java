package com.skinplate.api.domain.plate.entity;

import java.time.LocalDateTime;

/**
 * 기록이 어느 끼니인지. 시안의 기록 카드가 "아침 8:20" 처럼 시각과 함께 보여준다.
 *
 * <p>컬럼으로 두지 않고 {@code createdAt} 에서 파생한다. 시안 어디에도 끼니를 고르는
 * UI 가 없어서 사용자가 값을 줄 방법이 없고, 저장 시각이 곧 먹은 시각이기 때문이다.
 * 컬럼을 만들면 항상 서버가 채우는 값이 되는데, 그러면 저장된 값과 파생값이 언젠가
 * 어긋나면서 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * <p>경계는 한국 식사 시간대에 맞췄다. 저녁이 자정을 넘겨 새벽까지 이어지는 것은
 * 의도다 — 새벽 두 시의 야식을 "아침"으로 부르면 사용자가 자기 기록을 못 믿는다.
 *
 * <p>한국어 표기는 앱이 맡는다. 서버는 enum 이름만 보낸다.
 */
public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER;

    public static MealType from(LocalDateTime recordedAt) {
        int hour = recordedAt.getHour();
        if (hour >= 4 && hour < 11) return BREAKFAST;
        if (hour >= 11 && hour < 17) return LUNCH;
        return DINNER;
    }
}
