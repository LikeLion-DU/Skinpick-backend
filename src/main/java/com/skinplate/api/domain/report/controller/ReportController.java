package com.skinplate.api.domain.report.controller;

import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.service.ReportService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 기준일은 서버의 오늘(KST) 이다. 과거 임의 날짜는 히스토리가 맡는다.
     *
     * 모르는 period 는 Spring 이 MethodArgumentTypeMismatchException 을 던지고
     * GlobalExceptionHandler 가 400 으로 받는다.
     */
    @GetMapping
    public ApiResponse<ReportResponse> get(@CurrentUser Long userId,
                                           @RequestParam ReportPeriod period) {
        return ApiResponse.ok(reportService.get(userId, period));
    }
}
