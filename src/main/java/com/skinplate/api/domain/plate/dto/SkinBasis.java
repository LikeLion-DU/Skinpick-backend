package com.skinplate.api.domain.plate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 채점 기준이 된 피부 분석이 <b>언제의 피부인가</b>.
 *
 * <p>skinAnalysisId 를 생략하면 서버가 최신 분석을 쓰는데, 그 "최신"이 2주 전일 수 있다.
 * 그 사실을 숨긴 채 점수만 내려보내면 사용자는 "오늘 내 피부 기준"으로 읽는다 —
 * 화면이 거짓말을 하게 되는 자리라 기준 시점을 응답에 명시한다.
 *
 * <p>컬럼이 아니라 파생값이다. 분석 응답에서는 <b>오늘(KST)</b> 대비, 저장된 기록에서는
 * <b>기록 저장일</b> 대비로 계산한다 — 과거 기록을 다시 열었을 때 "그날 기준으로
 * 오늘 피부였나"가 유지되고, 시간이 흐른다고 TODAY 가 RECENT 로 변하지 않는다.
 */
public enum SkinBasis {

    /** 기준일에 측정한 피부다. */
    TODAY,

    /** 기준일보다 과거에 측정한 피부다. 앱이 측정일과 함께 안내한다. */
    RECENT;

    /**
     * @return 어느 한쪽이 없으면 null — non_null 직렬화로 키가 통째로 빠진다.
     *         (운영에서는 created_at 이 NOT NULL 이라 항상 값이 있다)
     */
    public static SkinBasis of(LocalDateTime skinMeasuredAt, LocalDate referenceDate) {
        if (skinMeasuredAt == null || referenceDate == null) return null;

        return skinMeasuredAt.toLocalDate().isEqual(referenceDate) ? TODAY : RECENT;
    }
}
