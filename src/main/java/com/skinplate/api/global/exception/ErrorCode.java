package com.skinplate.api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ---- 인증 ----
    INVALID_INPUT       (HttpStatus.BAD_REQUEST,  "요청 값이 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,     "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS (HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED        (HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TOKEN_EXPIRED       (HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    TEST_LOGIN_DISABLED (HttpStatus.FORBIDDEN,    "테스트 로그인이 비활성화된 환경입니다."),
    USER_NOT_FOUND      (HttpStatus.NOT_FOUND,    "사용자를 찾을 수 없습니다."),

    // ---- 이미지 / AI ----
    INVALID_IMAGE       (HttpStatus.BAD_REQUEST,          "이미지 형식 또는 용량이 올바르지 않습니다."),
    FACE_NOT_DETECTED   (HttpStatus.UNPROCESSABLE_ENTITY, "얼굴을 인식하지 못했습니다. 밝은 곳에서 다시 촬영해 주세요."),
    FOOD_NOT_DETECTED   (HttpStatus.UNPROCESSABLE_ENTITY, "음식을 인식하지 못했습니다. 다시 촬영해 주세요."),
    AI_ANALYSIS_FAILED  (HttpStatus.BAD_GATEWAY,          "분석에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    AI_TIMEOUT          (HttpStatus.GATEWAY_TIMEOUT,      "분석이 지연되고 있습니다. 다시 시도해 주세요."),
    RATE_LIMIT_EXCEEDED (HttpStatus.TOO_MANY_REQUESTS,    "오늘 분석 가능 횟수를 모두 사용했습니다."),

    // ---- 리소스 ----
    SKIN_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."),
    FOOD_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "음식 분석 결과를 찾을 수 없습니다."),
    PLATE_NOT_FOUND        (HttpStatus.NOT_FOUND, "Skin Plate를 찾을 수 없습니다."),

    // ---- 요청 오류 ----
    RESOURCE_NOT_FOUND (HttpStatus.NOT_FOUND,          "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED (HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다."),

    // ---- 기타 ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
