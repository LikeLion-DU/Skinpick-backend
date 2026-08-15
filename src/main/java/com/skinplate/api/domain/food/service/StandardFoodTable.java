package com.skinplate.api.domain.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.IngredientTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공공데이터 기반 표준 음식 테이블. 조회 전용이고 기동 때 한 번 읽는다.
 *
 * <p><b>왜 필요한가.</b> 룰 엔진은 `sodiumMg` 를 1500 과 비교하는데, 그 값이 AI 가
 * 사진을 보고 추정한 숫자면 같은 사진에서도 1400~2100 사이로 흔들린다. 경계를
 * 넘나드는 순간 점수가 8점씩 뛴다. 같은 사진·같은 얼굴인데 점수가 다르면
 * "점수는 규칙이 계산한다"는 이 제품의 주장이 그 자리에서 무너진다.
 *
 * <p><b>2단계로 찾는다.</b> AI 는 재료를 앞에 붙여 답한다("돼지고기 김치찌개").
 * <ol>
 *   <li>정확한 이름 — `김치찌개_참치` 처럼 재료까지 맞는 항목</li>
 *   <li>기본명 — 낱말이 `김치찌개` 로 끝나면 그 중앙값</li>
 * </ol>
 * 1단계가 있으면 참치찌개와 삼겹살찌개가 다른 값을 받는다. 없으면 기본명으로
 * 떨어져 최소한 "김치찌개다운" 값은 보장된다.
 *
 * <p><b>낱말 단위로 끝을 본다.</b> 단순 포함(contains)이면 "라면사리 부대찌개"가
 * 라면으로 잡혀 부대찌개에 라면 영양값이 들어간다. 점수는 사진과 무관한 숫자로
 * 계산되는데 사용자도 로그도 그걸 알 방법이 없다. 한국어 음식 이름은 핵심 낱말이
 * 뒤에 오므로 낱말의 끝을 본다.
 *
 * <p>데이터를 다시 만들려면 {@code tools/build_standard_food.py} 를 돌린다.
 */
@Slf4j
public final class StandardFoodTable {

    private StandardFoodTable() {}

    private static final String RESOURCE = "food/standard-food.json";

    /** 정확한 이름 → 항목. 완전 일치만 본다. */
    private static final Map<String, StandardFood> EXACT = new HashMap<>();

    /**
     * 기본명 → 항목. 낱말 끝 매칭으로 훑기 때문에 순서가 결과를 바꾼다.
     * <b>긴 이름이 앞에 온다</b> — "김치찌개"와 "찌개"가 둘 다 있을 때 짧은 쪽이
     * 먼저 걸리면 모든 찌개가 같은 값을 받는다.
     */
    private static final Map<String, StandardFood> BASE = new LinkedHashMap<>();

    static {
        load();
    }

    private static void load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = mapper.readTree(stream);

            for (JsonNode node : root.path("exact")) {
                StandardFood food = parse(node);
                EXACT.put(food.name(), food);
            }

            List<StandardFood> bases = new ArrayList<>();
            for (JsonNode node : root.path("base")) {
                bases.add(parse(node));
            }
            // exact 에만 있는 이름도 기본명으로 쓸 수 있어야 한다. "김치찌개" 가
            // exact 에 있으면 base 에서는 빠져 있는데(중복 제거), 그러면
            // "돼지고기 김치찌개" 가 낱말 매칭에서 갈 곳을 잃는다.
            bases.addAll(EXACT.values());
            bases.sort((left, right) -> right.name().length() - left.name().length());
            for (StandardFood food : bases) {
                BASE.putIfAbsent(food.name(), food);
            }

            log.info("표준 음식 테이블 적재: 정확 {}종 · 기본명 {}종", EXACT.size(), BASE.size());
        } catch (IOException e) {
            // 테이블이 없어도 앱은 떠야 한다. AI 추정치로 떨어질 뿐이다.
            log.error("표준 음식 테이블을 읽지 못했다 — AI 추정 영양값으로 동작한다", e);
        }
    }

    private static StandardFood parse(JsonNode node) {
        List<IngredientTag> tags = new ArrayList<>();
        for (JsonNode tag : node.path("tags")) {
            toTag(tag.asText()).ifPresent(tags::add);
        }

        return new StandardFood(
                node.path("name").asText(),
                intOrNull(node, "caloriesKcal"),
                decimalOrNull(node, "proteinG"),
                decimalOrNull(node, "fatG"),
                decimalOrNull(node, "carbG"),
                intOrNull(node, "sodiumMg"),
                decimalOrNull(node, "sugarG"),
                toCookingMethod(node.path("cookingMethod").asText()),
                node.path("spicy").asBoolean(false),
                List.copyOf(tags),
                node.path("measured").asBoolean(false),
                node.path("sampleCount").asInt(0));
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull()
                ? null : BigDecimal.valueOf(value.asDouble());
    }

    private static CookingMethod toCookingMethod(String value) {
        try {
            return CookingMethod.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return CookingMethod.ETC;
        }
    }

    private static Optional<IngredientTag> toTag(String value) {
        try {
            return Optional.of(IngredientTag.valueOf(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /**
     * 2단계 조회. 정확한 이름이 먼저다.
     *
     * <pre>
     *   "돼지고기 김치찌개" → exact 없음 → 낱말 [돼지고기, 김치찌개] → 김치찌개  ✅
     *   "김치찌개_참치"     → exact 적중                                    ✅
     *   "라면사리 부대찌개"  → 낱말 [라면사리, 부대찌개] → 라면으로 안 끝남    ✅ 안 걸림
     * </pre>
     */
    public static Optional<StandardFood> find(String foodName) {
        if (foodName == null || foodName.isBlank()) return Optional.empty();

        String trimmed = foodName.trim();

        StandardFood exact = EXACT.get(trimmed);
        if (exact != null) return Optional.of(exact);

        // **뒤 낱말부터 본다.** 한국어 음식 이름은 핵심 낱말이 끝에 온다 —
        // "돼지고기 김치찌개" 의 정체는 김치찌개지 돼지고기가 아니다.
        // 모든 낱말을 동등하게 훑으면 앞의 재료가 먼저 걸려서, 찌개에 고기구이
        // 영양값이 들어간다(650kcal·나트륨 334mg). 화면에도 로그에도 안 드러난다.
        String[] words = trimmed.split("\\s+");
        for (int i = words.length - 1; i >= 0; i--) {
            // BASE 는 긴 이름부터라 이 낱말에 걸리는 가장 구체적인 항목이 먼저 나온다.
            for (Map.Entry<String, StandardFood> entry : BASE.entrySet()) {
                if (words[i].endsWith(entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    public static int size() {
        return EXACT.size() + BASE.size();
    }
}
