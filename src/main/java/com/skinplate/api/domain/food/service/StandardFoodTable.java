package com.skinplate.api.domain.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.IngredientTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공공데이터 기반 표준 음식 테이블. 조회 전용이고 첫 조회 때 한 번 읽는다.
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

    /**
     * 이름 → 항목. exact 와 base 를 한 통에 담는다 — 조회가 완전 일치를 먼저 보고
     * 그다음 낱말의 접미사를 긴 쪽부터 보므로, 둘을 갈라 둘 필요가 없다.
     */
    private static final Map<String, StandardFood> TABLE = new HashMap<>();

    static {
        load();
    }

    private static void load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = mapper.readTree(stream);

            // 기본명을 먼저, 정확한 이름을 나중에 — 같은 이름이 양쪽에 있으면
            // 더 구체적인 exact 쪽이 남는다.
            for (JsonNode node : root.path("base")) {
                parse(node).ifPresent(food -> TABLE.put(food.name(), food));
            }
            for (JsonNode node : root.path("exact")) {
                parse(node).ifPresent(food -> TABLE.put(food.name(), food));
            }

            log.info("표준 음식 테이블 적재: {}종", TABLE.size());
        } catch (Exception e) {
            // 테이블이 없어도 앱은 떠야 한다. AI 추정치로 떨어질 뿐이다.
            // IOException 만 잡으면 나머지는 ExceptionInInitializerError 로 올라가고,
            // 그다음부터는 이 클래스를 건드릴 때마다 NoClassDefFoundError 가 난다.
            log.error("표준 음식 테이블을 읽지 못했다 — AI 추정 영양값으로 동작한다", e);
        }
    }

    /**
     * 이름 없는 행은 버린다. 빈 이름이 키로 들어가면 조회가 그 행으로 흘러가
     * 모르는 음식 전부가 남의 영양값을 받는다 — 화면에도 로그에도 안 드러난다.
     */
    private static Optional<StandardFood> parse(JsonNode node) {
        String name = node.path("name").asText("").trim();
        if (name.isEmpty()) {
            log.warn("이름 없는 표준 음식 행을 건너뛴다: {}", node);
            return Optional.empty();
        }

        List<IngredientTag> tags = new ArrayList<>();
        for (JsonNode tag : node.path("tags")) {
            toTag(tag.asText()).ifPresent(tags::add);
        }

        return Optional.of(new StandardFood(
                name,
                intOrNull(node, "caloriesKcal"),
                decimalOrNull(node, "proteinG"),
                decimalOrNull(node, "fatG"),
                decimalOrNull(node, "carbG"),
                intOrNull(node, "sodiumMg"),
                decimalOrNull(node, "sugarG"),
                decimalOrNull(node, "saturatedFatG"),
                decimalOrNull(node, "fiberG"),
                intOrNull(node, "vitaminAUg"),
                decimalOrNull(node, "vitaminCMg"),
                decimalOrNull(node, "zincMg"),
                toCookingMethod(node.path("cookingMethod").asText()),
                node.path("spicy").asBoolean(false),
                List.copyOf(tags),
                node.path("measured").asBoolean(false),
                node.path("sampleCount").asInt(0)));
    }

    /**
     * 숫자가 아니면 없는 것으로 본다. `"N/A"` 같은 값에 asInt() 를 물리면 조용히 0 이
     * 되는데, 나트륨 0 은 "짜지 않다"는 뜻이라 R04 가 안 걸리고 점수가 8점 올라간다.
     * 비어 있으면 AI 추정치를 쓰는 편이 낫다({@link StandardFood#toNutrition}).
     */
    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? BigDecimal.valueOf(value.asDouble()) : null;
    }

    // 조용히 삼키면 안 된다. enum 에 값을 하나 더하면서 스크립트를 안 고치면
    // 1,500 행이 통째로 ETC 가 되는데, 로그가 없으면 점수가 왜 바뀌었는지 알 수 없다.
    private static CookingMethod toCookingMethod(String value) {
        try {
            return CookingMethod.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("표준 음식 테이블에 모르는 조리 방식: {}", value);
            return CookingMethod.ETC;
        }
    }

    private static Optional<IngredientTag> toTag(String value) {
        try {
            return Optional.of(IngredientTag.valueOf(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("표준 음식 테이블에 모르는 재료 태그: {}", value);
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

        StandardFood exact = TABLE.get(trimmed);
        if (exact != null) return Optional.of(exact);

        // **곁들임을 먼저 떼어 낸다.** AI 는 접시 전체를 나열해 답하기도 한다 —
        // "소스가 뿌려진 돼지고기 돈가스와 양배추 샐러드". 아래 규칙은 뒤 낱말부터 보므로
        // 그대로 두면 곁들임(샐러드, 293kcal)이 본체(돈가스, 704kcal)를 이겨서 같은 사진이
        // 회차마다 다른 영양값을 받는다. 나열의 첫 조각이 본체다.
        String head = trimmed.split("(와|과)\\s|,")[0].trim();
        if (!head.equals(trimmed)) {
            Optional<StandardFood> byHead = findInPhrase(head);
            if (byHead.isPresent()) return byHead;
        }

        return findInPhrase(trimmed);
    }

    /**
     * **뒤 낱말부터 본다.** 한국어 음식 이름은 핵심 낱말이 끝에 온다 —
     * "돼지고기 김치찌개" 의 정체는 김치찌개지 돼지고기가 아니다.
     * 모든 낱말을 동등하게 훑으면 앞의 재료가 먼저 걸려서, 찌개에 고기구이
     * 영양값이 들어간다(650kcal·나트륨 334mg). 화면에도 로그에도 안 드러난다.
     */
    private static Optional<StandardFood> findInPhrase(String phrase) {
        String[] words = phrase.split("\\s+");
        for (int i = words.length - 1; i >= 0; i--) {
            // 접미사를 긴 쪽부터 찍어 본다. "김치찌개"와 "찌개"가 둘 다 있으면 긴 쪽이
            // 먼저 나오므로, 모든 찌개가 같은 값을 받는 일이 없다.
            String word = words[i];
            for (int start = 0; start < word.length(); start++) {
                StandardFood hit = TABLE.get(word.substring(start));
                if (hit != null) return Optional.of(hit);
            }
        }
        return Optional.empty();
    }

    /** 조회 가능한 이름의 수. 기동 로그와 적재 테스트가 본다. */
    public static int size() {
        return TABLE.size();
    }
}
