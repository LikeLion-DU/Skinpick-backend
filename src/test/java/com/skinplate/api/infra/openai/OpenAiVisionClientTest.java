package com.skinplate.api.infra.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.exception.OpenAiClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네트워크 없이 응답만 갈아끼운다. WebClient 자체는 실제 구현을 쓰므로
 * 상태 코드 처리·코덱·재시도가 진짜로 동작하는지 확인된다.
 *
 * 에러 분기가 이 클래스의 핵심이다. 타임아웃과 429 를 한 덩어리로 묶으면
 * 앱의 재시도 UX 가 통째로 도달 불가 코드가 되고, 예산이 남은 채로 실패한다.
 */
class OpenAiVisionClientTest {

    private static final String CONTENT = """
            {"faceDetected":true,"hydration":38,"oil":52,"redness":64,
             "trouble":25,"barrier":78,"summary":"건조하고 홍조가 관찰됩니다."}""";

    private static final String ENVELOPE = """
            {"choices":[{"message":{"content":%s}}]}"""
            .formatted(new ObjectMapper().valueToTree(CONTENT).toString());

    private OpenAiVisionClient clientOf(ExchangeFunction exchange, long timeoutSeconds) {
        return new OpenAiVisionClient(
                WebClient.builder().exchangeFunction(exchange).build(),
                new ObjectMapper(), "gpt-4o", timeoutSeconds);
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body).build();
    }

    @Test
    @DisplayName("정상 응답에서 다섯 지표를 꺼낸다")
    void parsesStructuredOutput() {
        OpenAiSkinResult result = clientOf(request -> Mono.just(json(HttpStatus.OK, ENVELOPE)), 5)
                .analyzeSkin("base64", "image/jpeg");

        assertThat(result.faceDetected()).isTrue();
        assertThat(result.hydration()).isEqualTo(38);
        assertThat(result.barrier()).isEqualTo(78);
    }

    @Test
    @DisplayName("타임아웃은 AI_TIMEOUT 으로 구분된다 — 앱이 재시도 버튼을 띄우는 분기다")
    void timeoutMapsToAiTimeout() {
        assertThatThrownBy(() -> clientOf(request -> Mono.never(), 1).analyzeSkin("base64", "image/jpeg"))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
    }

    @Test
    @DisplayName("429 는 한 번 재시도하고, 두 번째에 성공하면 결과를 돌려준다")
    void retriesOnceOnTooManyRequests() {
        AtomicInteger calls = new AtomicInteger();

        OpenAiSkinResult result = clientOf(request -> Mono.just(
                calls.incrementAndGet() == 1
                        ? json(HttpStatus.TOO_MANY_REQUESTS, "{}")
                        : json(HttpStatus.OK, ENVELOPE)), 5).analyzeSkin("base64", "image/jpeg");

        assertThat(result.hydration()).isEqualTo(38);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("타임아웃은 시도마다 새로 걸린다 — 전체에 한 번이 아니다")
    void timeoutAppliesPerAttempt() {
        // PRD §17.2 의 "최악 0.1+2+18 ≈ 20초" 계산이 이 전제 위에 서 있다.
        // 전체에 한 번이라면 재시도 대기 2초만으로도 1초 제한을 넘겨 실패해야 한다.
        AtomicInteger calls = new AtomicInteger();

        OpenAiSkinResult result = clientOf(request -> Mono.delay(Duration.ofMillis(300))
                .then(Mono.just(calls.incrementAndGet() == 1
                        ? json(HttpStatus.TOO_MANY_REQUESTS, "{}")
                        : json(HttpStatus.OK, ENVELOPE))), 1).analyzeSkin("base64", "image/jpeg");

        assertThat(result.hydration()).isEqualTo(38);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("파싱 실패는 원본 응답을 예외에 실어 보낸다 — 진단에 이것 말고는 단서가 없다")
    void parseFailureCarriesRawResponse() {
        String broken = """
                {"choices":[{"message":{"content":"이건 JSON 이 아니다"}}]}""";

        assertThatThrownBy(() -> clientOf(request -> Mono.just(json(HttpStatus.OK, broken)), 5)
                .analyzeSkin("base64", "image/jpeg"))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("rawResponse").isEqualTo("이건 JSON 이 아니다");
    }

    @Test
    @DisplayName("5xx 는 재시도하지 않는다 — 재시도가 답이 아닌 에러다")
    void doesNotRetryServerError() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> clientOf(request -> {
            calls.incrementAndGet();
            return Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));
        }, 5).analyzeSkin("base64", "image/jpeg"))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);

        assertThat(calls.get()).isEqualTo(1);
    }
}
