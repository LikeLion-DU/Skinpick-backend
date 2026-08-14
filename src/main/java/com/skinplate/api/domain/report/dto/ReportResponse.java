package com.skinplate.api.domain.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 오늘과 이번 주가 같은 형태를 쓴다. 화면이 하나이기 때문이다.
 *
 * null 인 필드는 응답에서 키가 통째로 빠진다(default-property-inclusion: non_null).
 * 앱은 "키가 없음"을 "값 없음"으로 읽어야 한다.
 *
 * @param latestSkinScore  기간 내 가장 최근 분석 1건의 점수. <b>주간 평균이 아니다.</b>
 *                         얼굴을 매일 찍지 않으므로 TODAY 에서는 대개 비어 있다
 * @param skinScoreTrend   WEEK 전용. TODAY 는 빈 배열
 * @param meals            TODAY 전용. WEEK 는 빈 배열
 */
public record ReportResponse(
        ReportPeriod period,
        LocalDate from,
        LocalDate to,
        Integer latestSkinScore,
        List<TrendPointDto> skinScoreTrend,
        int recordCount,
        Integer averagePlateScore,
        List<PenaltyDto> penalties,
        List<MealDto> meals
) {}
