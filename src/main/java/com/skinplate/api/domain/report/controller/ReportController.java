package com.skinplate.api.domain.report.controller;

import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.WeeklyReportResponse;
import com.skinplate.api.domain.report.service.DailyReportService;
import com.skinplate.api.domain.report.service.WeeklyReportService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final DailyReportService dailyReportService;
    private final WeeklyReportService weeklyReportService;

    /**
     * 일일 리포트. date 는 KST 달력일이고, 생략하면 서버의 오늘이다.
     * 과거 날짜도 그대로 조회된다 — 저장된 기록만 다시 세므로 언제 열어도 같은 값이다.
     */
    @GetMapping("/daily")
    public ApiResponse<DailyReportResponse> daily(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ApiResponse.ok(dailyReportService.get(userId, date));
    }

    /**
     * 주간 리포트. from·to 는 KST 달력일이고 to 도 포함한다. 둘 다 생략하면 오늘 포함
     * 최근 7일이다. 기간을 넓히면 그대로 월간 집계가 된다(상한 90일 — DateRange).
     *
     * <p>이 응답은 그 기간의 <b>일일 리포트를 집계한 결과</b>다. 기록이 없는 날은 평균에
     * 들어가지 않는다.
     */
    @GetMapping("/weekly")
    public ApiResponse<WeeklyReportResponse> weekly(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ApiResponse.ok(weeklyReportService.get(userId, from, to));
    }
}
