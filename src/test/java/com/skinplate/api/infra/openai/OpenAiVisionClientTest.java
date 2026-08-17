package com.skinplate.api.infra.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.FacePhotoType;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.exception.OpenAiClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 네트워크 없이 응답만 갈아끼운다. WebClient 자체는 실제 구현을 쓰므로
 * 상태 코드 처리·코덱·재시도가 진짜로 동작하는지 확인된다.
 *
 * 에러 분기가 이 클래스의 핵심이다. 타임아웃과 429 를 한 덩어리로 묶으면
 * 앱의 재시도 UX 가 통째로 도달 불가 코드가 되고, 예산이 남은 채로 실패한다.
 */
class OpenAiVisionClientTest {

    /**
     * 확장 필드(metricEvidence · skinType · skinAgeAnalysis)가 없는 <b>예전 응답 모양</b>이다.
     * 이게 그대로 파싱돼야 조회 경로가 과거에 저장된 raw_ai_response 를 읽을 수 있다.
     */
    private static final String CONTENT = """
            {"faceDetected":true,"hydration":38,"oil":52,"redness":64,
             "trouble":25,"barrier":78,"summary":"건조하고 홍조가 관찰됩니다."}""";

    private static final String ENVELOPE = """
            {"choices":[{"message":{"content":%s}}]}"""
            .formatted(new ObjectMapper().valueToTree(CONTENT).toString());

    /** 세 장이 한 요청에 실린다. Base64 는 방향마다 다르게 둬서 뒤섞임을 잡는다. */
    private static final List<FacePhoto> PHOTOS = List.of(
            new FacePhoto(FacePhotoType.FRONT, "RlJPTlQ=", "image/jpeg"),
            new FacePhoto(FacePhotoType.LEFT, "TEVGVA==", "image/png"),
            new FacePhoto(FacePhotoType.RIGHT, "UklHSFQ=", "image/jpeg"));

    /**
     * <b>기본 모델은 배포에 나가는 것과 같아야 한다.</b> 아래 타임아웃·429 재시도·5xx·파싱
     * 실패 검증이 전부 이 클라이언트를 쓰는데, 여기가 gpt-4o 면 정작 프로덕션이 타는
     * gpt-5 분기는 요청 모양 테스트 하나만 덮게 된다 — 실제로 그런 상태였다.
     *
     * 피부 타임아웃도 같은 값으로 준다 — 타임아웃 분기 테스트가 피부 호출로 돌아간다.
     */
    private OpenAiVisionClient clientOf(ExchangeFunction exchange, long timeoutSeconds) {
        return clientOf(exchange, timeoutSeconds, "gpt-5.6-luna");
    }

    private OpenAiVisionClient clientOf(ExchangeFunction exchange, long timeoutSeconds, String model) {
        return new OpenAiVisionClient(
                WebClient.builder().exchangeFunction(exchange).build(),
                new ObjectMapper(), model, timeoutSeconds, 1400, timeoutSeconds, timeoutSeconds);
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
                .analyzeSkin(PHOTOS);

        assertThat(result.faceDetected()).isTrue();
        assertThat(result.hydration()).isEqualTo(38);
        assertThat(result.barrier()).isEqualTo(78);
    }

    @Test
    @DisplayName("세 장이 한 요청에 방향 라벨과 함께 실린다 (지시서 §6 · §17)")
    void sendsAllThreePhotosInOneRequest() {
        AtomicInteger requests = new AtomicInteger();
        StringBuilder captured = new StringBuilder();

        clientOf(request -> {
            requests.incrementAndGet();
            captured.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, ENVELOPE));
        }, 5).analyzeSkin(PHOTOS);

        String body = captured.toString();

        // 사진마다 방향 라벨이 바로 앞에 붙는다. 순서로만 구분하면 한 장이 밀려도 드러나지 않는다.
        // left = 고개를 왼쪽으로 돌린 사진(오른쪽 뺨) — FacePhotoType 주석의 결정.
        assertThat(body).contains("[정면]",
                                  "[고개를 왼쪽으로 돌린 측면 — 오른쪽 뺨이 보임]",
                                  "[고개를 오른쪽으로 돌린 측면 — 왼쪽 뺨이 보임]");
        assertThat(body).contains("data:image/jpeg;base64,RlJPTlQ=",
                                  "data:image/png;base64,TEVGVA==",
                                  "data:image/jpeg;base64,UklHSFQ=");
        assertThat(body).contains("\"detail\":\"high\"");
        // 장당 한 번씩 부르면 5~8초가 세 번이고, 지표도 세 벌 나온다.
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("호출마다 출력 상한이 다르다 — 피부 1400 · 인사이트 1200 · 나머지 800")
    void eachCallAsksForItsOwnTokenBudget() {
        String textEnvelope = """
                {"choices":[{"message":{"content":%s}}]}"""
                .formatted(new ObjectMapper().valueToTree(
                        "{\"summary\":\"요약\",\"topics\":[{\"category\":\"DRY\",\"description\":\"설명\"}]}")
                        .toString());

        StringBuilder insight = new StringBuilder();
        clientOf(request -> {
            insight.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, textEnvelope));
        }, 5).generateSkinInsight("무시된다");

        StringBuilder skin = new StringBuilder();
        clientOf(request -> {
            skin.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, ENVELOPE));
        }, 5).analyzeSkin(PHOTOS);

        String commentEnvelope = """
                {"choices":[{"message":{"content":%s}}]}"""
                .formatted(new ObjectMapper().valueToTree(
                        "{\"aiTip\":\"팁\",\"dailyComment\":\"코멘트\"}").toString());

        StringBuilder comment = new StringBuilder();
        clientOf(request -> {
            comment.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, commentEnvelope));
        }, 5).generateComments("무시된다");

        // 한국어 문장 넷(summary + description ×3)이 800 토큰 상한에 닿는다.
        assertThat(insight.toString()).contains("\"max_completion_tokens\":1200");
        // 피부는 5지표 + 8축 + 근거라 출력이 인사이트보다도 크다. 프로퍼티로 주입된다.
        assertThat(skin.toString()).contains("\"max_completion_tokens\":1400");
        // 문장 생성은 그대로 800 — 상한을 다 같이 올리면 출력 폭주 방어가 함께 헐거워진다.
        assertThat(comment.toString()).contains("\"max_completion_tokens\":800");
    }

    @Test
    @DisplayName("gpt-5 계열은 요청 규약이 다르다 — max_tokens 와 temperature 를 보내면 400 이다")
    void reasoningModelUsesItsOwnRequestShape() {
        StringBuilder luna = new StringBuilder();
        clientOf(request -> {
            luna.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, ENVELOPE));
        }, 5, "gpt-5.6-luna").analyzeSkin(PHOTOS);

        // 실측 400: "Use 'max_completion_tokens' instead" / "Only the default (1) value is supported"
        assertThat(luna.toString())
                .contains("\"max_completion_tokens\":1400")
                .contains("\"reasoning_effort\":\"low\"")   // reasoning 이 출력 상한을 갉아먹는다
                .doesNotContain("\"max_tokens\"")
                .doesNotContain("\"temperature\"");

        StringBuilder gpt4o = new StringBuilder();
        clientOf(request -> {
            gpt4o.append(bodyOf(request));
            return Mono.just(json(HttpStatus.OK, ENVELOPE));
        }, 5, "gpt-4o").analyzeSkin(PHOTOS);   // 롤백 경로

        assertThat(gpt4o.toString())
                .contains("\"max_tokens\":1400")
                .contains("\"temperature\":0.2")            // 재현성 레버는 4o 에서만 쓸 수 있다
                .doesNotContain("max_completion_tokens")
                .doesNotContain("reasoning_effort");
    }

    /** 텍스트 전용 호출의 응답 봉투. content 는 JSON 문자열이라 한 번 더 감싸야 한다. */
    private static String textEnvelopeOf(String contentJson) {
        return """
                {"choices":[{"message":{"content":%s}}]}"""
                .formatted(new ObjectMapper().valueToTree(contentJson).toString());
    }

    /** WebClient 는 본문을 BodyInserter 로 들고 있다. 실제로 써 봐야 내용이 보인다. */
    private static String bodyOf(ClientRequest request) {
        MockClientHttpRequest http = new MockClientHttpRequest(HttpMethod.POST, URI.create("/"));
        request.writeTo(http, ExchangeStrategies.withDefaults()).block();
        return http.getBodyAsString().block();
    }

    /**
     * 실제로 타임아웃이 나기를 기다리면 이 테스트 하나가 28초를 먹는다.
     * 클램프는 생성자에서 끝나므로 주입된 값만 본다 — 프로젝트가 이미 쓰는 방식이다.
     */
    private static Duration skinTimeoutOf(long configured) {
        return timeoutFieldOf("skinTimeout", configured, 12);
    }

    private static Duration reportTimeoutOf(long configured) {
        return timeoutFieldOf("reportTimeout", 28, configured);
    }

    private static Duration timeoutFieldOf(String field, long skinTimeout, long reportTimeout) {
        OpenAiVisionClient client = new OpenAiVisionClient(
                WebClient.builder().build(), new ObjectMapper(),
                "gpt-5.6-luna", 5, 1400, skinTimeout, reportTimeout);
        return (Duration) ReflectionTestUtils.getField(client, field);
    }

    @Test
    @DisplayName("피부 타임아웃은 28초를 못 넘는다 — 넘기면 앱이 먼저 끊어 AI_TIMEOUT 이 도달 불가가 된다")
    void skinTimeoutIsClampedToTheClientBudget() {
        // 40 을 그대로 쓰면 429 재시도(2초)가 낀 최악이 42초라 클라이언트 상한(32초)을 넘는다.
        assertThat(skinTimeoutOf(40)).isEqualTo(Duration.ofSeconds(28));
        assertThat(skinTimeoutOf(28)).isEqualTo(Duration.ofSeconds(28));
        assertThat(skinTimeoutOf(20)).isEqualTo(Duration.ofSeconds(20));   // 상한 아래는 그대로
    }

    /**
     * <b>필드를 읽는 것만으로는 부족하다.</b> 배선을 되돌려도(reportTimeout → timeout)
     * 필드는 그대로 채워지므로 리플렉션 단언은 초록으로 남는다. 실제로 호출해서
     * 리포트 예산에서 끊기는지 봐야 이 PR 이 고친 그 한 줄이 검증된다.
     */
    @Test
    @DisplayName("주간 문장은 리포트 예산에서 끊긴다 — 분석용 예산을 물려받지 않는다")
    void weeklyCommentUsesReportBudget() {
        // 응답을 1.5초 지연시킨다. 분석 5초 · 리포트 1초라 갈라져야 정상이다.
        ExchangeFunction slow = request -> Mono.delay(Duration.ofMillis(1500))
                .then(Mono.just(json(HttpStatus.OK, textEnvelopeOf(
                        "{\"goodPoint\":\"좋음\",\"improvePoint\":\"주의\","
                                + "\"habit\":\"습관\",\"nextWeek\":\"추천\"}"))));

        OpenAiVisionClient client = new OpenAiVisionClient(
                WebClient.builder().exchangeFunction(slow).build(), new ObjectMapper(),
                "gpt-5.6-luna", 5, 1400, 5, 1);

        assertThatThrownBy(() -> client.generateWeeklyComment("표"))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
    }

    @Test
    @DisplayName("리포트 예산을 줄여도 분석 경로는 그대로다 — 한 값이 다섯 경로를 흔들지 않는다")
    void analysisPathKeepsItsOwnBudget() {
        ExchangeFunction slow = request -> Mono.delay(Duration.ofMillis(1500))
                .then(Mono.just(json(HttpStatus.OK, textEnvelopeOf(
                        "{\"aiTip\":\"팁\",\"dailyComment\":\"코멘트\"}"))));

        // 리포트만 1초로 조인다. 같은 1.5초 지연이라도 분석용 문장은 5초 예산이라 살아야 한다.
        OpenAiVisionClient client = new OpenAiVisionClient(
                WebClient.builder().exchangeFunction(slow).build(), new ObjectMapper(),
                "gpt-5.6-luna", 5, 1400, 5, 1);

        assertThat(client.generateComments("컨텍스트").aiTip()).isEqualTo("팁");
    }

    @Test
    @DisplayName("리포트 타임아웃 상한은 분석과 다른 15초다 — 오타 하나로 조회 화면이 다시 멎지 않는다")
    void reportTimeoutHasItsOwnCeiling() {
        assertThat(reportTimeoutOf(12)).isEqualTo(Duration.ofSeconds(12));

        // 28 은 분석의 상한이지 리포트의 상한이 아니다. 그대로 허용하면
        // REPORT_TIMEOUT_SECONDS 오타 하나로 이 분리가 통째로 무효가 된다.
        assertThat(reportTimeoutOf(28)).isEqualTo(Duration.ofSeconds(15));
        assertThat(reportTimeoutOf(40)).isEqualTo(Duration.ofSeconds(15));

        // 0 을 넣으면 Duration.ZERO 가 되어 주간 문장이 항상 죽는데,
        // fail-soft 라 화면은 멀쩡해서 아무도 모른다. 하한이 그 자리를 막는다.
        assertThat(reportTimeoutOf(0)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("0 이하는 하한으로 올린다 — Duration.ZERO 면 모든 분석이 즉시 타임아웃으로 죽는다")
    void skinTimeoutHasALowerBound() {
        // 아무 로그 없이 기능만 사라지는 자리라 위쪽 상한보다 이쪽이 더 나쁘다.
        assertThat(skinTimeoutOf(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(skinTimeoutOf(-28)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("타임아웃은 AI_TIMEOUT 으로 구분된다 — 앱이 재시도 버튼을 띄우는 분기다")
    void timeoutMapsToAiTimeout() {
        assertThatThrownBy(() -> clientOf(request -> Mono.never(), 1).analyzeSkin(PHOTOS))
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
                        : json(HttpStatus.OK, ENVELOPE)), 5).analyzeSkin(PHOTOS);

        assertThat(result.hydration()).isEqualTo(38);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("시도마다 타임아웃이 새로 걸린다 — 재시도 대기가 첫 시도 몫을 깎지 않는다")
    void timeoutAppliesPerAttempt() {
        // PRD §17.2 의 "최악 0.1+2+25 ≈ 27초" 계산이 이 전제 위에 서 있다.
        // 전체에 한 번이라면 재시도 대기 2초만으로도 1초 제한을 넘겨 실패해야 한다.
        AtomicInteger calls = new AtomicInteger();

        OpenAiSkinResult result = clientOf(request -> Mono.delay(Duration.ofMillis(300))
                .then(Mono.just(calls.incrementAndGet() == 1
                        ? json(HttpStatus.TOO_MANY_REQUESTS, "{}")
                        // 2초다. 시나리오가 0.3 + 2(재시도 대기) + 0.3 ≈ 2.6초라,
                        // 전체 한 번짜리 타임아웃이었다면 2초에서 먼저 끊겨 실패한다 —
                        // 그게 이 테스트가 증명하려는 것이다. 전체 데드라인은 2+2=4초라
                        // 여유 1.4초가 남아 CI 에서도 흔들리지 않는다.
                        // 5초로 두면 전체 예산 안에도 2.6초가 들어가 변별력이 사라진다.
                        : json(HttpStatus.OK, ENVELOPE))), 2).analyzeSkin(PHOTOS);

        assertThat(result.hydration()).isEqualTo(38);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("출력 상한에서 잘리면 파싱 전에 막는다 — 빈 content 는 형식 오류와 구분되지 않는다")
    void truncatedResponseIsRejectedBeforeParsing() {
        // reasoning 토큰이 상한을 다 먹으면 content 가 빈 문자열로 온다. isTextual() 이
        // true 라 가드를 통과하고, 파싱 단계에서 "No content to map" 으로 뭉개지면
        // "상한을 올려라"라는 유일한 신호가 사라진다.
        String truncated = """
                {"choices":[{"finish_reason":"length","message":{"content":""}}],
                 "usage":{"completion_tokens":1400,"completion_tokens_details":{"reasoning_tokens":1400}}}""";

        assertThatThrownBy(() -> clientOf(request -> Mono.just(json(HttpStatus.OK, truncated)), 5)
                .analyzeSkin(PHOTOS))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("content 가 빈 문자열이면 성공으로 보지 않는다")
    void blankContentIsNotAcceptedAsSuccess() {
        String blank = """
                {"choices":[{"finish_reason":"stop","message":{"content":"   "}}]}""";

        assertThatThrownBy(() -> clientOf(request -> Mono.just(json(HttpStatus.OK, blank)), 5)
                .analyzeSkin(PHOTOS))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("파싱 실패는 원본 응답을 예외에 실어 보낸다 — 진단에 이것 말고는 단서가 없다")
    void parseFailureCarriesRawResponse() {
        String broken = """
                {"choices":[{"message":{"content":"이건 JSON 이 아니다"}}]}""";

        assertThatThrownBy(() -> clientOf(request -> Mono.just(json(HttpStatus.OK, broken)), 5)
                .analyzeSkin(PHOTOS))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("rawResponse").isEqualTo("이건 JSON 이 아니다");
    }

    @Test
    @DisplayName("429 가 두 번 연속이면 원인이 429 그대로 남는다 — 응답 본문이 로그의 유일한 단서다")
    void retryExhaustedKeepsOriginalCause() {
        AtomicInteger calls = new AtomicInteger();

        Throwable thrown = catchThrowable(() -> clientOf(request -> {
            calls.incrementAndGet();
            return Mono.just(json(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));
        }, 5).analyzeSkin(PHOTOS));

        // Reactor 기본값은 여기서 원인을 RetryExhaustedException 으로 갈아끼운다.
        // 그러면 로깅이 상태코드·본문 분기를 못 타고 내부 스택트레이스만 남는다.
        assertThat(thrown)
                .isInstanceOf(OpenAiClientException.class)
                .hasCauseInstanceOf(WebClientResponseException.TooManyRequests.class);
        assertThat(((OpenAiClientException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("5xx 는 재시도하지 않는다 — 재시도가 답이 아닌 에러다")
    void doesNotRetryServerError() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> clientOf(request -> {
            calls.incrementAndGet();
            return Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));
        }, 5).analyzeSkin(PHOTOS))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);

        assertThat(calls.get()).isEqualTo(1);
    }
}
