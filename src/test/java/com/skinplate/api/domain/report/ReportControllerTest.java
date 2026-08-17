package com.skinplate.api.domain.report;

import com.skinplate.api.domain.report.controller.ReportController;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.dto.WeeklyReportResponse;
import com.skinplate.api.domain.report.service.DailyReportService;
import com.skinplate.api.domain.report.service.ReportService;
import com.skinplate.api.domain.report.service.WeeklyReportService;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.global.exception.GlobalExceptionHandler;
import com.skinplate.api.global.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final ReportService reportService = mock(ReportService.class);
    private final DailyReportService dailyReportService = mock(DailyReportService.class);
    private final WeeklyReportService weeklyReportService = mock(WeeklyReportService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ReportController(
                    reportService, dailyReportService, weeklyReportService))
            .setCustomArgumentResolvers(new StubCurrentUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("period=WEEK 이면 주간 집계를 부르고 봉투에 담아 돌려준다")
    void weekReport() throws Exception {
        given(reportService.get(eq(1L), eq(ReportPeriod.WEEK))).willReturn(
                new ReportResponse(ReportPeriod.WEEK, LocalDate.of(2026, 8, 8),
                        LocalDate.of(2026, 8, 14), 72, List.of(), 12, 68, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports").param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recordCount").value(12))
                .andExpect(jsonPath("$.data.averagePlateScore").value(68));
    }

    @Test
    @DisplayName("period 가 없으면 400 이다")
    void missingPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("모르는 period 는 400 이다 — 500 으로 새지 않는다")
    void unknownPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/reports").param("period", "MONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("date 를 생략하면 서비스에 null 이 그대로 넘어간다 — 오늘 판정은 서버가 한다")
    void dailyWithoutDate() throws Exception {
        given(dailyReportService.get(eq(1L), isNull()))
                .willReturn(DailyReportResponse.empty(LocalDate.of(2026, 8, 17)));

        // 날짜 직렬화 형식은 여기서 검증하지 않는다. standaloneSetup 은 앱의 Jackson 설정
        // (write-dates-as-timestamps: false)을 타지 않아 배열로 나온다 — 실제 응답과 다르다.
        mockMvc.perform(get("/api/v1/reports/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordCount").value(0))
                // non_null 직렬화라 점수 키 자체가 빠진다. 앱은 "키 없음 = 값 없음"으로 읽는다.
                .andExpect(jsonPath("$.data.dailyScore").doesNotExist());
    }

    @Test
    @DisplayName("date 를 주면 그 날짜로 조회한다")
    void dailyWithDate() throws Exception {
        given(dailyReportService.get(eq(1L), eq(LocalDate.of(2026, 8, 12))))
                .willReturn(new DailyReportResponse(LocalDate.of(2026, 8, 12), 72, SkinLevel.GOOD,
                        3, List.of(), List.of(), List.of(), "좋은 하루였어요", List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports/daily").param("date", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyScore").value(72))
                .andExpect(jsonPath("$.data.grade").value("GOOD"))
                .andExpect(jsonPath("$.data.recordCount").value(3));
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400 이다 — 500 으로 새지 않는다")
    void dailyWithBrokenDate() throws Exception {
        mockMvc.perform(get("/api/v1/reports/daily").param("date", "2026-13-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("from·to 를 생략해도 주간 리포트를 부른다 — 기본 기간은 서버가 정한다")
    void weeklyWithoutRange() throws Exception {
        given(weeklyReportService.get(eq(1L), isNull(), isNull()))
                .willReturn(new WeeklyReportResponse(
                        LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 17),
                        76, SkinLevel.GOOD, 7, 5, 12,
                        List.of(), List.of(), List.of(), null, null, null));

        mockMvc.perform(get("/api/v1/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageDailyScore").value(76))
                .andExpect(jsonPath("$.data.recordedDays").value(5))
                .andExpect(jsonPath("$.data.totalDays").value(7));
    }

    /** 인증은 이 테스트의 관심사가 아니다. 필터가 넣어 주는 값만 흉내 낸다. */
    private static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }
        @Override public Object resolveArgument(MethodParameter parameter,
                ModelAndViewContainer container, NativeWebRequest request,
                WebDataBinderFactory factory) {
            return 1L;
        }
    }
}
