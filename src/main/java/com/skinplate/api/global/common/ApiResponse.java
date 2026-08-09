package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.ErrorCode;

/**
 * 모든 API의 공통 응답 래퍼.
 * 성공: { success: true,  data: {...}, error: null }
 * 실패: { success: false, data: null,  error: { code, message } }
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message));
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return fail(code, code.getMessage());
    }

    public record ErrorBody(String code, String message) {}
}
