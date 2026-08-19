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
import java.util.regex.Pattern;

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

    /**
     * "A와 B가 <b>들어간</b> C" 처럼 앞이 재료고 뒤가 본체인 표현. 이 표지가 보이면
     * 나열이 아니라 수식절이라, 첫 조각을 본체로 믿으면 안 된다.
     *
     * <p><b>구분자 뒤에서만 찾는다.</b> 이름 전체에서 찾으면 "치즈가 올라간 떡볶이와 순대"
     * 처럼 앞이 수식절이고 뒤가 나열인 이름이 통째로 수식절로 읽혀 곁들임(순대)이 본체를
     * 이긴다 — 고치려던 것과 정반대 결과다.
     */
    private static final Pattern MODIFIER_CLAUSE =
            Pattern.compile("(들어간|들어있는|들어 있는|곁들인|곁들여|올라간|얹은|넣은|넣어|넣고)\\s");

    /**
     * 목적격·주격 조사가 붙은 낱말. 용언 어미를 다 나열하는 대신 이걸 함께 본다 —
     * "채소<b>를</b> 넣고 끓인 김치찌개" 처럼 표지 목록에 없는 활용형이 와도 잡힌다.
     *
     * <p>주격 "이" 는 뺐다. 음식 이름에 그 끝소리가 흔해서("떡볶이"·"오이") 나열을
     * 수식절로 오해한다 — 조사 하나 더 잡으려다 멀쩡한 이름을 잃는다.
     */
    private static final Pattern OBJECT_PARTICLE = Pattern.compile("\\S+(가|를|을)\\s");

    /**
     * 같은 음식의 흔한 표기 차이. AI 는 "돈까스"라고 답하는데 공공데이터는 "돈가스"만 있고,
     * 그러면 조회가 뒤 낱말로 밀려 <b>재료 이름("돼지고기")에 걸린다</b> — 돈가스가 고기구이
     * 영양값(650kcal)을 받는다. 실사진 E2E 에서 실제로 그렇게 됐다.
     *
     * <p><b>판정이 아니라 철자다.</b> 여기에 다른 음식을 이어 붙이지 마라 — "라멘 → 라면"은
     * 표기 차이가 아니라 다른 음식이고, 그 줄이 생기는 순간 이 표는 앱이 만든 판정 규칙이 된다.
     */
    private static final Map<String, String> SPELLINGS = Map.of(
            "돈까스", "돈가스",
            "돈카츠", "돈가스",
            "떡뽁이", "떡볶이",
            "떡뽀끼", "떡볶이");

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
     * <b>완전 일치만</b> 본다. 접미사 fallback 이 없다.
     *
     * <p>{@link #find} 는 AI 가 답한 자유 문장에서 음식을 <b>찾아내는</b> 것이 일이라
     * 못 찾으면 낱말을 잘라 가며 근사값을 낸다. 반대로 이 메서드는 이미
     * {@code standardFoodName} 으로 <b>저장해 둔 정식 이름</b>을 되짚는 자리다 —
     * 그 이름이 표에 없다면 표를 다시 만들면서 사라진 것이고, 그때 근사값을 주면
     * "김치찌개_참치" 가 "참치" 행의 결측 여부를 물려받는다. 모르는 것은 모르는 채로
     * 두는 편이 낫다.
     */
    public static Optional<StandardFood> findExact(String standardFoodName) {
        if (standardFoodName == null || standardFoodName.isBlank()) return Optional.empty();
        return Optional.ofNullable(TABLE.get(standardFoodName.trim()));
    }

    /**
     * 조회한 <b>이름</b>에서 확실히 읽히는 가점 재료. 기본명 매칭은 수식어를 버리는
     * 설계라("연어 샐러드" → 샐러드), 이름에 적혀 있는 연어의 오메가3 까지 함께
     * 사라진다 — 가점 룰이 전부 꺼진 연어 샐러드가 68점으로 나온 원인이다.
     *
     * <p>이름은 결정적이다 — 같은 이름이면 같은 태그가 선다. AI 태그를 다시 믿는 것이
     * 아니라, 표준표가 확정한 항목 위에 이름이 보증하는 재료를 얹는 것이다.
     *
     * <p><b>가점 태그만 얹는다.</b> 감점 재료(당·유제품·매운맛)는 영양값과 spicy 가
     * 이미 잡고 있고, 낱말 하나로 벌을 주면 "고추냉이 연어"류 오탐이 점수를 깎는다.
     * 낱말 목록은 tools/build_standard_food.py 의 TAG_RULES 와 같은 값을 쓴다.
     */
    private record BonusNameTag(IngredientTag tag, List<String> words) {}

    private static final List<BonusNameTag> BONUS_NAME_TAGS = List.of(
            new BonusNameTag(IngredientTag.OMEGA3,
                    List.of("연어", "고등어", "삼치", "꽁치", "참치", "정어리", "방어", "멸치", "들기름")),
            new BonusNameTag(IngredientTag.VITAMIN_C,
                    List.of("파프리카", "브로콜리", "키위", "피망", "딸기", "귤", "오렌지", "레몬", "샐러드")),
            new BonusNameTag(IngredientTag.VITAMIN_A,
                    List.of("당근", "시금치", "단호박", "부추", "깻잎", "나물")),
            new BonusNameTag(IngredientTag.ANTIOXIDANT,
                    List.of("토마토", "녹차", "블루베리", "베리", "가지", "양파")),
            new BonusNameTag(IngredientTag.PROBIOTIC,
                    List.of("김치", "된장", "고추장", "청국장", "요거트", "요구르트", "낫토")));

    /** 표준 항목의 태그에 조회 이름이 보증하는 가점 태그를 합친다. 중복은 한 번만. */
    private static StandardFood withNameTags(StandardFood food, String queriedName) {
        List<IngredientTag> merged = new ArrayList<>(food.tags());
        for (BonusNameTag bonus : BONUS_NAME_TAGS) {
            if (merged.contains(bonus.tag())) continue;
            if (bonus.words().stream().anyMatch(queriedName::contains)) {
                merged.add(bonus.tag());
            }
        }
        if (merged.size() == food.tags().size()) return food;

        return new StandardFood(food.name(), food.caloriesKcal(), food.proteinG(),
                food.fatG(), food.carbG(), food.sodiumMg(), food.sugarG(),
                food.saturatedFatG(), food.fiberG(), food.vitaminAUg(), food.vitaminCMg(),
                food.zincMg(), food.cookingMethod(), food.spicy(), List.copyOf(merged),
                food.measured(), food.sampleCount());
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

        String trimmed = normalizeSpelling(foodName.trim());
        // 어느 경로로 찾았든 조회 이름이 보증하는 가점 태그를 얹는다 — 기본명
        // 매칭이 버린 수식어("연어 샐러드"의 연어)를 여기서 돌려받는다.
        return locate(trimmed).map(found -> withNameTags(found, trimmed));
    }

    private static Optional<StandardFood> locate(String trimmed) {
        StandardFood exact = TABLE.get(trimmed);
        if (exact != null) return Optional.of(exact);

        // **곁들임을 먼저 떼어 낸다.** AI 는 접시 전체를 나열해 답하기도 한다 —
        // "소스가 뿌려진 돼지고기 돈가스와 양배추 샐러드". 아래 규칙은 뒤 낱말부터 보므로
        // 그대로 두면 곁들임(샐러드, 293kcal)이 본체(돈가스, 704kcal)를 이겨서 같은 사진이
        // 회차마다 다른 영양값을 받는다. 나열의 첫 조각이 본체다.
        //
        // **단, 수식절이면 정반대다.** "돼지고기와 채소가 들어간 김치찌개" 에서 앞은 재료고
        // 본체는 끝에 있다. 첫 조각을 믿으면 김치찌개가 돼지고기(650kcal) 영양값을 받는데,
        // 그 뒤로는 표준 매칭이 확정돼 AI 태그까지 눌리므로 틀린 답이 결정론적으로 굳는다 —
        // 못 찾는 것보다 나쁘다. 뒤에 수식 표지가 있으면 지름길을 쓰지 않는다.
        //
        // **표지는 첫 조각 뒤에서만 찾는다.** 이름 전체에서 찾으면 첫 조각 안에 든 수식
        // ("소스가 올라간 돈가스와 양배추 샐러드")까지 지름길을 껐고, 그러면 곁들임인
        // 샐러드(293kcal)가 다시 본체 돈가스(704kcal)를 이겼다. 앞을 꾸미는 말과
        // 뒤를 가리키는 말은 자리가 다르다.
        String firstChunk = trimmed.split("(와|과)\\s|,")[0];
        String head = firstChunk.trim();
        String rest = trimmed.substring(firstChunk.length());
        if (!head.equals(trimmed) && !MODIFIER_CLAUSE.matcher(rest).find()) {
            // 첫 조각에서는 **마지막 낱말만** 본다. 앞 낱말까지 훑으면 "소스가 뿌려진
            // 돼지고기 돈까스" 가 돈가스를 못 찾았을 때 "돼지고기"(고기구이 650kcal)로
            // 떨어진다 — 못 찾는 것보다 나쁘다. 못 찾으면 아래에서 이름 전체를 훑는다.
            String[] headWords = head.split("\\s+");
            Optional<StandardFood> byHead = findSuffix(headWords[headWords.length - 1]);
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
            Optional<StandardFood> hit = findSuffix(words[i]);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    /**
     * 접미사를 긴 쪽부터 찍어 본다. "김치찌개"와 "찌개"가 둘 다 있으면 긴 쪽이 먼저
     * 나오므로, 모든 찌개가 같은 값을 받는 일이 없다.
     */
    private static Optional<StandardFood> findSuffix(String word) {
        for (int start = 0; start < word.length(); start++) {
            StandardFood hit = TABLE.get(word.substring(start));
            if (hit != null) return Optional.of(hit);
        }
        return Optional.empty();
    }

    /**
     * 표기 차이를 표준 이름으로 되돌린다. 부분 문자열 치환이라 <b>키가 서로를 품으면 안 된다</b> —
     * 지금 넷은 서로 겹치지 않아 순서와 무관하게 같은 답이 나온다. 한 키의 결과가 다른 키를
     * 품는 순간 {@code Map.of} 의 순회 순서(JVM 마다 다르다)가 답을 가르고, 결정론이 목적인
     * 이 파일에서 재기동만으로 점수가 달라진다.
     */
    private static String normalizeSpelling(String name) {
        String normalized = name;
        for (Map.Entry<String, String> spelling : SPELLINGS.entrySet()) {
            normalized = normalized.replace(spelling.getKey(), spelling.getValue());
        }
        return normalized;
    }

    /** 조회 가능한 이름의 수. 기동 로그와 적재 테스트가 본다. */
    public static int size() {
        return TABLE.size();
    }
}
