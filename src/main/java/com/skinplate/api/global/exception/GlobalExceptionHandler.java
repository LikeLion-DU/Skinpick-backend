package com.skinplate.api.global.exception;

import com.skinplate.api.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("[{}] {}", errorCode.name(), exception.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, exception.getMessage()));
    }

    /** @Valid 검증 실패 → 첫 번째 필드 메시지를 그대로 사용자에게 보여준다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .filter(defaultMessage -> defaultMessage != null && !defaultMessage.isBlank())
                .collect(Collectors.joining(" "));

        if (message.isBlank()) message = ErrorCode.INVALID_INPUT.getMessage();

        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, message));
    }

    /**
     * 존재하지 않는 경로. Spring Boot 3.2+ 는 NoResourceFoundException 을 던지는데,
     * 아래 포괄 핸들러가 이걸 삼키면 모든 오타 경로가 500 이 된다.
     * 프론트는 "일시적인 오류가 발생했습니다"를 보고 원인을 백엔드에서 찾게 되고,
     * PRD §8.2 의 "5xx 에러율 ≤ 1%" 지표도 404 로 오염된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException exception) {
        log.warn("존재하지 않는 경로: {}", exception.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        log.warn("허용되지 않은 메서드: {}", exception.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 요청이 잘못된 경우들. 전부 클라이언트 잘못이므로 400 이고 log.warn 이다.
     * 포괄 핸들러에 맡기면 500 + 스택트레이스가 되어, 프론트 통합 중
     * 로그가 남의 오타로 가득 찬다.
     *
     * MissingServletRequestPartException 이 특히 중요하다 —
     * multipart 의 파트 이름을 "image" 가 아닌 것으로 보내면 여기 걸린다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,          // 본문이 없거나 깨진 JSON
            MethodArgumentTypeMismatchException.class,      // /plates/abc 같은 타입 불일치
            MissingServletRequestPartException.class,       // multipart 파트 누락
            MissingServletRequestParameterException.class   // 필수 쿼리 파라미터 누락
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        log.warn("잘못된 요청: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_IMAGE.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_IMAGE, "이미지는 5MB 이하만 업로드할 수 있습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("처리되지 않은 예외", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
