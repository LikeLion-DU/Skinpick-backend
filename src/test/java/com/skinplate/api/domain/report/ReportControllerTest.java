package com.skinplate.api.domain.report;

import com.skinplate.api.domain.report.controller.ReportController;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.service.ReportService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final ReportService reportService = mock(ReportService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ReportController(reportService))
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
