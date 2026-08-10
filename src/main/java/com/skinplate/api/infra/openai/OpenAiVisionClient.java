package com.skinplate.api.infra.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.exception.OpenAiClientException;
import com.skinplate.api.infra.openai.prompt.FoodAnalysisPrompt;
import com.skinplate.api.infra.openai.prompt.SkinAnalysisPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
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

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final Duration timeout;

    public OpenAiVisionClient(WebClient openAiWebClient,
                              ObjectMapper objectMapper,
                              @Value("${app.ai.model}") String model,
                              @Value("${app.ai.timeout-seconds}") long timeoutSeconds) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public OpenAiSkinResult analyzeSkin(String base64Image, String mediaType) {
        return call(SkinAnalysisPrompt.SYSTEM, SkinAnalysisPrompt.USER, SkinAnalysisPrompt.SCHEMA,
                "skin_analysis", base64Image, mediaType, SKIN_DETAIL, OpenAiSkinResult.class);
    }

    @Override
    public OpenAiFoodResult analyzeFood(String base64Image, String mediaType) {
        return call(FoodAnalysisPrompt.SYSTEM, FoodAnalysisPrompt.USER, FoodAnalysisPrompt.SCHEMA,
                "food_analysis", base64Image, mediaType, FOOD_DETAIL, OpenAiFoodResult.class);
    }

    private <T> T call(String system, String user, Map<String, Object> schema, String schemaName,
                       String base64Image, String mediaType, String detail, Class<T> type) {

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", user),
                                // 선언한 타입과 실제 바이트가 어긋나면 400 이다.
                                // image/jpeg 로 고정해 두면 PNG 를 올린 사용자만 "분석 실패"를 본다.
                                Map.of("type", "image_url", "image_url", Map.of(
                                        "url", "data:" + mediaType + ";base64," + base64Image,
                                        "detail", detail))))),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", schemaName,
                                "strict", true,
                                "schema", schema)),
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS);

        return openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                // 타임아웃은 재시도하지 않는다. 재시도까지 하면 앱 타임아웃(25초)을 넘긴다.
                .timeout(timeout)
                // 429 만 재시도한다. 응답이 즉시 오므로 최악 0.1+2+18 ≈ 20초로 앱 타임아웃 안에 들어온다.
                // 예산과 처리량 상한은 다른 축이고, 429 는 재시도가 유일한 정답인 에러다. (PRD §17.2)
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2))
                        .filter(error -> error instanceof WebClientResponseException.TooManyRequests))
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
