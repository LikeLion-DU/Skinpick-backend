package com.skinplate.api.domain.report.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.user.entity.SkinConcern;

/**
 * 자가 신고 피부 고민 하나에 대한 식단 점수.
 *
 * <p>고민 자체를 측정한 값이 아니다 — <b>그 고민과 관련된 룰만 모아 다시 센 식단
 * 점수</b>다({@link com.skinplate.api.domain.report.service.ConcernRules}). "여드름이
 * 62점"이 아니라 "여드름 관점에서 본 오늘 식단이 62점"이다.
 *
 * <p>저장하지 않고 조회할 때마다 다시 센다. 그래서 <b>매핑표를 바꾸면 과거 리포트의
 * 이 값도 바뀐다</b> — 왜 스냅샷을 두지 않는지는 {@code ConcernRules} 주석에 있다.
 *
 * @param score              0~100. <b>기간 평균</b>이다(일일이면 그날 값)
 * @param changeFromFirstDay 기간의 <b>첫 기록일 → 마지막 기록일</b> 변화. 기록일이
 *                           하나뿐이거나 일일 리포트면 null 이라 키가 빠진다(non_null).
 *                           <p>이름을 길게 둔 이유가 있다 — {@code score} 와 축이 다르다.
 *                           54·62·70 인 사흘은 {@code score=62, changeFromFirstDay=+16} 인데,
 *                           이것을 "평균 대비 변화"로 읽으면 이전 값이 46 이었다는 뜻이 되고
 *                           그런 날은 없었다
 */
public record ConcernScoreDto(SkinConcern concern, String label, int score,
                              SkinLevel status, Integer changeFromFirstDay) {

    public static ConcernScoreDto of(SkinConcern concern, int score, Integer changeFromFirstDay) {
        return new ConcernScoreDto(concern, concern.getLabel(), score,
                SkinLevel.of(score), changeFromFirstDay);
    }
}
