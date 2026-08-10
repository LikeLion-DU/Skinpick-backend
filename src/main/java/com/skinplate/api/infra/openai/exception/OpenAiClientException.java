package com.skinplate.api.infra.openai.exception;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import lombok.Getter;

/**
 * AI 호출 실패. ErrorCode 를 그대로 들고 나가므로 GlobalExceptionHandler 가
 * 추가 분기 없이 상태 코드와 메시지를 결정한다.
 *
 * 파싱에 실패한 경우 원본 응답을 함께 싣는다. PRD §17.4 가 "원본을
 * raw_ai_response 에 기록 후 AI_ANALYSIS_FAILED" 를 요구하는데, 로그로만
 * 남기면 Service 가 그 값을 꺼낼 방법이 없어 DB 컬럼을 채우지 못한다.
 * cause 에 담긴 것은 Jackson 예외이지 원본 문자열이 아니다.
 */
@Getter
public class OpenAiClientException extends BusinessException {

    /** 파싱에 실패한 원본 응답. 그 외의 실패에서는 null 이다. */
    private final String rawResponse;

    public OpenAiClientException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, null);
    }

    public OpenAiClientException(ErrorCode errorCode, Throwable cause, String rawResponse) {
        super(errorCode, cause);
        this.rawResponse = rawResponse;
    }
}
