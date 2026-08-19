package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.controller.SkinAnalysisController;
import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.skin.service.SkinAnalysisService;
import com.skinplate.api.global.exception.GlobalExceptionHandler;
import com.skinplate.api.global.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세 장이 다 와야 분석이 시작된다. 한 장이라도 빠진 요청은 AI 를 부르기 전에 막힌다 —
 * 두 장으로 분석하면 결과는 나오지만 그게 어떤 입력으로 나온 점수인지 알 수 없게 된다.
 *
 * 방향을 파트 이름으로 받기 때문에 "중복 type" · "알 수 없는 type" 은 여기서 검사하지
 * 않는다. 표현될 수가 없는 상태라 테스트할 것도 없다 — 배열 + type 필드였다면
 * 셋 다 직접 막아야 했을 자리다. (지시서 §9)
 */
class SkinAnalysisControllerTest {

    private final SkinAnalysisService skinAnalysisService = mock(SkinAnalysisService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SkinAnalysisController(skinAnalysisService))
            .setCustomArgumentResolvers(new StubCurrentUserResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("세 장이 다 오면 201 이고 방향 그대로 서비스에 넘어간다")
    void threePhotosCreated() throws Exception {
        given(skinAnalysisService.analyze(eq(1L), any(), any(), any())).willReturn(response());

        mockMvc.perform(multipart("/api/v1/skin/analyses")
                        .file(photo("front")).file(photo("left")).file(photo("right")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skinAnalysisId").value(101));
    }

    @Test
    @DisplayName("front 가 없으면 400 — AI 를 부르지 않는다")
    void missingFront() throws Exception {
        expectBadRequest(photo("left"), photo("right"));
    }

    @Test
    @DisplayName("left 가 없으면 400")
    void missingLeft() throws Exception {
        expectBadRequest(photo("front"), photo("right"));
    }

    @Test
    @DisplayName("right 가 없으면 400")
    void missingRight() throws Exception {
        expectBadRequest(photo("front"), photo("left"));
    }

    @Test
    @DisplayName("옛 계약대로 image 한 장만 보내면 400 — 조용히 한 장으로 분석되지 않는다")
    void singleLegacyImage() throws Exception {
        expectBadRequest(photo("image"));
    }

    private void expectBadRequest(MockMultipartFile... parts) throws Exception {
        var request = multipart("/api/v1/skin/analyses");
        for (MockMultipartFile part : parts) request = request.file(part);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verify(skinAnalysisService, never()).analyze(any(), any(), any(), any());
    }

    private static MockMultipartFile photo(String partName) {
        return new MockMultipartFile(partName, "face.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    private static SkinAnalysisResponse response() {
        return new SkinAnalysisResponse(101L, 55, SkinLevel.of(55), null, List.of(), null, null,
                "요약", List.of(), List.of(), null, null,
                LocalDateTime.of(2026, 8, 13, 12, 30));
    }

    /** 인증은 이 테스트의 관심사가 아니다. 필터가 넣어 주는 값만 흉내 낸다. */
    private static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory factory) {
            return 1L;
        }
    }
}
