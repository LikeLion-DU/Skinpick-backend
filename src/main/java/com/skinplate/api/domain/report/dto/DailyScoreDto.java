package com.skinplate.api.domain.report.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;

/**
 * 하루의 대표 점수 한 줄. 주간 추이 · BEST DAY · WORST DAY 가 같은 모양을 쓴다 —
 * 셋 다 "어느 날 몇 점"이고, 화면도 같은 칩으로 그린다.
 *
 * <p><b>기록이 없는 날은 만들지 않는다.</b> 0 점짜리 점이 추이에 섞이면 평균도
 * 최저값도 거짓이 된다.
 */
public record DailyScoreDto(LocalDate date, int dailyScore, SkinLevel grade) {

    public static DailyScoreDto of(LocalDate date, int dailyScore) {
        return new DailyScoreDto(date, dailyScore, SkinLevel.of(dailyScore));
    }
}
