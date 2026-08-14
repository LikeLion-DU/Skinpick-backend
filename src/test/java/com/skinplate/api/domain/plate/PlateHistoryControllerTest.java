package com.skinplate.api.domain.plate;

import com.skinplate.api.domain.plate.controller.SkinPlateController;
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import com.skinplate.api.domain.plate.service.SkinPlateService;
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

class PlateHistoryControllerTest {

    private final SkinPlateService skinPlateService = mock(SkinPlateService.class);
    private final PlateHistoryService plateHistoryService = mock(PlateHistoryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SkinPlateController(skinPlateService, plateHistoryService))
            .setCustomArgumentResolvers(new StubCurrentUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("from·to 를 날짜로 받아 히스토리를 봉투에 담아 돌려준다")
    void history() throws Exception {
        given(plateHistoryService.get(eq(1L), eq(LocalDate.of(2026, 8, 8)),
                eq(LocalDate.of(2026, 8, 14))))
                .willReturn(new PlateHistoryResponse(List.of()));

        mockMvc.perform(get("/api/v1/plates").param("from", "2026-08-08").param("to", "2026-08-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.days").isArray());
    }

    @Test
    @DisplayName("from 이 없으면 400 이다 — 전체 기록을 통째로 읽는 호출을 만들지 않는다")
    void missingFrom() throws Exception {
        mockMvc.perform(get("/api/v1/plates").param("to", "2026-08-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("날짜 형식이 아니면 400 이다")
    void malformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/plates").param("from", "2026/08/08").param("to", "2026-08-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

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
