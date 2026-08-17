package com.skinplate.api.domain.report.dto;

import com.skinplate.api.domain.skin.entity.SkinLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 리포트. <b>저장하지 않는다</b> — 매 조회마다 그 기간의 일일 리포트를 다시
 * 집계한다. 주간 전용 테이블을 두면 일일 기록을 지웠을 때 두 화면의 숫자가 갈린다.
 *
 * <p>월간도 이 응답을 쓴다. 다른 것은 {@code from}~{@code to} 폭뿐이다.
 *
 * @param averageDailyScore <b>기록이 있는 날만</b> 평균한다. 7일 중 5일 기록이면 ÷5 다 —
 *                          안 찍은 날을 0 점으로 세면 성실하게 기록할수록 점수가 낮아진다.
 *                          기록이 하나도 없으면 null
 * @param totalDays         조회 기간의 달력일 수
 * @param recordedDays      그중 기록이 하나라도 있는 날 수. 평균의 분모다
 * @param recordCount       기간 전체의 기록(끼니) 수
 * @param dailyScores       날짜 오름차순. 기록이 없는 날은 들어가지 않는다
 * @param nutrition         <b>기록이 있는 날의 하루 평균</b>. 합계가 아니다 —
 *                          하루 기준값과 견주는 화면이라 축을 맞춘다
 * @param concerns          고민별 기간 평균과 첫 기록일 대비 변화
 * @param bestDay           가장 높은 날. 동점이면 <b>이른 날짜</b>가 이긴다
 * @param worstDay          가장 낮은 날. 동점 규칙은 bestDay 와 같다
 * @param aiComment         집계된 숫자만 보고 쓴 문장 넷. 생성에 실패하면 키가 빠진다
 */
public record WeeklyReportResponse(
        LocalDate from,
        LocalDate to,
        Integer averageDailyScore,
        SkinLevel grade,
        int totalDays,
        int recordedDays,
        int recordCount,
        List<DailyScoreDto> dailyScores,
        List<NutritionItemDto> nutrition,
        List<ConcernScoreDto> concerns,
        DailyScoreDto bestDay,
        DailyScoreDto worstDay,
        WeeklyCommentDto aiComment
) {
    /** AI 문장은 트랜잭션 밖에서 뒤늦게 붙는다. record 라 복사본을 만든다. */
    public WeeklyReportResponse withAiComment(WeeklyCommentDto comment) {
        return new WeeklyReportResponse(from, to, averageDailyScore, grade,
                totalDays, recordedDays, recordCount,
                dailyScores, nutrition, concerns, bestDay, worstDay, comment);
    }
}
