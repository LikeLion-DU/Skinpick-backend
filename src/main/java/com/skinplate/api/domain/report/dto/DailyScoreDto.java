package com.skinplate.api.domain.report.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루의 대표 점수 한 줄. 주간 추이 · BEST DAY · WORST DAY 가 같은 모양을 쓴다 —
 * 셋 다 "어느 날 몇 점"이고, 화면도 같은 칩으로 그린다.
 *
 * <p><b>기록이 없는 날은 만들지 않는다.</b> 0 점짜리 점이 추이에 섞이면 평균도
 * 최저값도 거짓이 된다.
 *
 * @param plateIds 그날 기록의 id 목록. <b>BEST/WORST 카드에만 채운다</b> — 시안이 그 두
 *                 카드에만 그날 먹은 음식 썸네일을 그린다. 사진은 서버에 없고 앱이 저장할
 *                 때 남긴 로컬 파일이라(PRD §9.6), 앱이 사진을 찾으려면 id 가 필요하다.
 *                 <p>추이 그래프의 7칸에는 null 이라 키가 빠진다 — 그래프는 점수만 쓰는데
 *                 id 를 함께 실으면 한 주 응답에 쓰이지 않는 배열이 일곱 개 붙는다
 */
public record DailyScoreDto(LocalDate date, int dailyScore, SkinLevel grade,
                            List<Long> plateIds) {

    /** 추이 그래프용. 썸네일이 필요 없는 자리라 id 를 싣지 않는다. */
    public static DailyScoreDto of(LocalDate date, int dailyScore) {
        return new DailyScoreDto(date, dailyScore, SkinLevel.of(dailyScore), null);
    }

    /** BEST/WORST 카드용. 같은 날의 점수는 그대로 두고 id 만 얹는다. */
    public DailyScoreDto withPlateIds(List<Long> plateIds) {
        return new DailyScoreDto(date, dailyScore, grade,
                plateIds == null ? List.of() : List.copyOf(plateIds));
    }
}
