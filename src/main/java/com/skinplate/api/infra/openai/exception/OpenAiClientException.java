package com.skinplate.api.infra.openai.exception;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;

/**
 * AI 호출 실패. ErrorCode 를 그대로 들고 나가므로 GlobalExceptionHandler 가
 * 추가 분기 없이 상태 코드와 메시지를 결정한다.
 */
public class OpenAiClientException extends BusinessException {

    public OpenAiClientException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
