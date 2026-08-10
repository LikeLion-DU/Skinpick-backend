package com.skinplate.api.infra.openai.exception;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import lombok.Getter;

/**
 * AI 호출 실패. ErrorCode 를 그대로 들고 나가므로 GlobalExceptionHandler 가
 * 추가 분기 없이 상태 코드와 메시지를 결정한다.
 *
 * 파싱에 실패한 경우 원본 응답을 함께 싣는다. cause 에 담긴 것은 Jackson
 * 예외이지 원본 문자열이 아니라, 그것만으로는 "AI 가 뭐라고 답했는가"를 알 수 없다.
 *
 * 이 값은 진단용이지 저장용이 아니다. raw_ai_response 컬럼은 분석이 성공했을
 * 때만 채운다 — 실패 응답에는 지표가 없어 NOT NULL 을 가짜 값으로 메워야 하고,
 * 그 행이 GET /skin/analyses/latest 에 잡혀 홈 화면이 0점을 띄운다. (PRD §17.4)
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
