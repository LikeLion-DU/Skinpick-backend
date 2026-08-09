package com.skinplate.api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패 시 Spring Security 기본 HTML 대신 프로젝트 공통 JSON 포맷으로 응답한다.
 * 필터가 남긴 errorCode가 있으면 TOKEN_EXPIRED / UNAUTHORIZED를 구분해서 내려준다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object attr = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        ErrorCode code = (attr instanceof ErrorCode ec) ? ec : ErrorCode.UNAUTHORIZED;

        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code));
    }
}
