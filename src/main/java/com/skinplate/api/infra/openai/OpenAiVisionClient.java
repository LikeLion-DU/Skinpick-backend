package com.skinplate.api.infra.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.dto.PlateComments;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import com.skinplate.api.infra.openai.exception.OpenAiClientException;
import com.skinplate.api.infra.openai.prompt.FoodAnalysisPrompt;
import com.skinplate.api.infra.openai.prompt.PlateCommentPrompt;
import com.skinplate.api.infra.openai.prompt.SkinAnalysisPrompt;
import com.skinplate.api.infra.openai.prompt.SkinInsightPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI Vision 호출. 인식만 맡기고 점수는 Rule Engine 이 계산한다.
 *
 * 이미지 해상도가 다르다 — 피부는 high, 음식은 low.
 * 홍조·트러블 판정이 512px 다운샘플로는 성립하지 않는데 이 제품의 개인화 전체가
 * 그 숫자에 얹혀 있다. 음식은 "김치찌개인가"만 알면 되므로 low 로 충분하다. (PRD §17.2)
 */
@Slf4j
@Component
public class OpenAiVisionClient implements VisionClient {

    private static final String SKIN_DETAIL = "high";
    private static final String FOOD_DETAIL = "low";
    private static final double TEMPERATURE = 0.2;      // 재현성 확보
    private static final int MAX_TOKENS = 800;          // 출력 폭주 방지

    /**
     * 인사이트만 상한이 크다. 한국어 문장이 넷(summary + description ×3)이라 800 이 상한에
     * 닿는데, 잘려서 실패하면 temperature 0.2 라 다시 불러도 같은 자리에서 또 잘린다 —
     * 재시도로 회복되지 않는 실패다.
     */
    private static final int INSIGHT_MAX_TOKENS = 1200;

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final Duration timeout;

    /**
     * 피부만 상한과 타임아웃을 따로 받는다. 출력이 5지표 + 8축 + 근거라 다른 호출의
     * 두 배 가까이 나오고, 상한을 다 같이 올리면 음식 호출의 출력 폭주 방어까지 헐거워진다.
     * 프로퍼티로 빼 둔 이유는 실기기에서 재보고 조여야 하는 값이라서다.
     */
    private final int skinMaxTokens;
    private final Duration skinTimeout;

    /**
     * gpt-5 계열은 요청 규약이 다르다. 실제로 던져 본 결과다.
     *   max_tokens        → 400 "Use 'max_completion_tokens' instead"
     *   temperature=0.2   → 400 "Only the default (1) value is supported"
     * 모델 이름으로 갈라 두면 모델 교체가 application.yml 한 줄로 끝난다.
     */
    private final boolean reasoningModel;

    /** gpt-5 계열에서 reasoning 토큰은 출력 상한을 같이 갉아먹는다. 낮게 물려 상한을 지킨다. */
    private static final String REASONING_EFFORT = "low";

    public OpenAiVisionClient(WebClient openAiWebClient,
                              ObjectMapper objectMapper,
                              @Value("${app.ai.model}") String model,
                              @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
                              @Value("${app.ai.skin-max-tokens}") int skinMaxTokens,
                              @Value("${app.ai.skin-timeout-seconds}") long skinTimeoutSeconds) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.skinMaxTokens = skinMaxTokens;
        this.skinTimeout = Duration.ofSeconds(skinTimeoutSeconds);
        this.reasoningModel = model.startsWith("gpt-5");
    }

    /**
     * 사진 세 장이 메시지 하나에 들어간다. 사진마다 바로 앞에 방향 라벨을 텍스트로 끼워
     * 넣는데, 그러지 않으면 AI 가 순서로만 방향을 추측하게 되고 그 추측이 틀려도
     * 아무 데도 드러나지 않는다. 라벨 세 줄은 30토큰이 채 안 된다.
     */
    @Override
    public OpenAiSkinResult analyzeSkin(List<FacePhoto> photos) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(text(SkinAnalysisPrompt.USER));

        for (FacePhoto photo : photos) {
            content.add(text("[" + photo.type().getLabel() + "]"));
            content.add(image(photo.base64(), photo.mediaType(), SKIN_DETAIL));
        }

        return call(SkinAnalysisPrompt.SYSTEM, SkinAnalysisPrompt.SCHEMA,
                "skin_analysis", content, skinMaxTokens, skinTimeout, OpenAiSkinResult.class);
    }

    @Override
    public OpenAiFoodResult analyzeFood(String base64Image, String mediaType) {
        return call(FoodAnalysisPrompt.SYSTEM, FoodAnalysisPrompt.SCHEMA, "food_analysis",
                List.of(text(FoodAnalysisPrompt.USER), image(base64Image, mediaType, FOOD_DETAIL)),
                MAX_TOKENS, timeout, OpenAiFoodResult.class);
    }

    /** 텍스트 전용이라 이미지 파트가 없다. 같은 call() 을 타므로 타임아웃·429 정책도 같다. */
    @Override
    public PlateComments generateComments(String userContext) {
        return call(PlateCommentPrompt.SYSTEM, PlateCommentPrompt.SCHEMA, "plate_comments",
                List.of(text(userContext)), MAX_TOKENS, timeout, PlateComments.class);
    }

    /**
     * generateComments 와 같은 텍스트 전용 호출이다. 타임아웃·429 정책도 같고,
     * 출력 상한만 INSIGHT_MAX_TOKENS 로 넓힌다.
     */
    @Override
    public SkinInsightSentences generateSkinInsight(String userContext) {
        return call(SkinInsightPrompt.SYSTEM, SkinInsightPrompt.SCHEMA, "skin_insight",
                List.of(text(userContext)), INSIGHT_MAX_TOKENS, timeout, SkinInsightSentences.class);
    }

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    /**
     * 선언한 타입과 실제 바이트가 어긋나면 400 이다.
     * image/jpeg 로 고정해 두면 PNG 를 올린 사용자만 "분석 실패"를 본다.
     */
    private static Map<String, Object> image(String base64, String mediaType, String detail) {
        return Map.of("type", "image_url", "image_url", Map.of(
                "url", "data:" + mediaType + ";base64," + base64,
                "detail", detail));
    }

    private <T> T call(String system, Map<String, Object> schema, String schemaName,
                       List<Map<String, Object>> userContent, int maxTokens,
                       Duration callTimeout, Class<T> type) {

        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userContent)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", schemaName,
                                "strict", true,
                                "schema", schema))));

        if (reasoningModel) {
            body.put("max_completion_tokens", maxTokens);
            body.put("reasoning_effort", REASONING_EFFORT);
            // temperature 는 보내지 않는다 — 1 고정이라 다른 값을 실으면 400 이다.
        } else {
            body.put("max_tokens", maxTokens);
            body.put("temperature", TEMPERATURE);
        }

        return openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                // 타임아웃은 재시도하지 않는다. 재시도까지 하면 앱 타임아웃(32초)을 넘긴다.
                .timeout(callTimeout)
                // 429 만 재시도한다. 429 응답은 즉시 오므로 최악은 0.1 + 2 + callTimeout 이다.
                // 음식·문장(25초)은 ≈27초로 앱 타임아웃(32초) 안이지만, 피부(30초)는 ≈32.1초라
                // 429 가 한 번 끼면 앱이 먼저 끊는다 — 그 경우 AI_TIMEOUT 분기가 아니라 앱의
                // 네트워크 오류로 보인다. 실기기에서 걸리면 앱을 35초로 올리거나 피부를 28초로 줄인다.
                // 예산과 처리량 상한은 다른 축이고, 429 는 재시도가 유일한 정답인 에러다. (PRD §17.2)
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2))
                        .filter(error -> error instanceof WebClientResponseException.TooManyRequests)
                        // 기본 동작은 재시도가 소진되면 원래 예외를 Reactor 내부 예외로 갈아끼운다.
                        // 그러면 429 응답 본문(어떤 한도인지·언제 풀리는지)이 로그에서 사라지는데,
                        // 하필 그게 429 가 두 번 연속인 상황 — 본문이 가장 필요한 때다.
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .map(this::extractContent)
                .map(content -> parse(content, type))
                .onErrorMap(TimeoutException.class,
                        error -> new OpenAiClientException(ErrorCode.AI_TIMEOUT, error))
                .onErrorMap(error -> !(error instanceof OpenAiClientException),
                        error -> new OpenAiClientException(ErrorCode.AI_ANALYSIS_FAILED, logCause(error)))
                .block();
    }

    /**
     * 원인을 여기서 남기지 않으면 사라진다. GlobalExceptionHandler 는 ErrorCode 와
     * 사용자용 메시지만 찍기 때문에, 키가 틀린 401 과 실제 OpenAI 장애가
     * 화면에도 로그에도 "분석에 실패했습니다" 한 줄로 똑같이 보인다.
     * 그 상태에서는 원인을 찾으려고 OpenAI 상태 페이지부터 열게 된다.
     */
    private Throwable logCause(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            log.warn("OpenAI 호출 실패 {} — {}",
                    response.getStatusCode(), response.getResponseBodyAsString());
        } else {
            log.warn("OpenAI 호출 실패", error);
        }
        return error;
    }

    /** Structured Outputs 라도 본문은 choices[0].message.content 안의 문자열이다. */
    private String extractContent(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !content.isTextual()) {
            log.warn("OpenAI 응답에서 content 를 찾지 못했다: {}", response);
            throw new OpenAiClientException(ErrorCode.AI_ANALYSIS_FAILED, null, response.toString());
        }
        return content.asText();
    }

    private <T> T parse(String content, Class<T> type) {
        try {
            return objectMapper.readValue(content, type);
        } catch (Exception e) {
            // 원본을 예외에 실어 보낸다 — Service 가 raw_ai_response 에 남길 수 있어야 한다 (PRD §17.4)
            log.warn("AI 응답 파싱 실패: {}", content);
            throw new OpenAiClientException(ErrorCode.AI_ANALYSIS_FAILED, e, content);
        }
    }
}
