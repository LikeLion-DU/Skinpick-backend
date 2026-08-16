package com.skinplate.api.domain.food.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.image.ImageEncoder;
import com.skinplate.api.global.image.ImageEncoder.EncodedImage;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 음식 사진 인식. AI 는 "무슨 음식인가"만 판단하고 점수는 Rule Engine 이 계산한다.
 *
 * 트랜잭션을 열지 않는다. AI 왕복이 18초까지 걸리는데 그걸 트랜잭션 안에 두면
 * DB 커넥션 하나가 그동안 잠긴다. 저장 경계는 호출자(SkinPlateService)가 잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodAnalysisService {

    /** food_name 컬럼이 VARCHAR(100), food_category 가 VARCHAR(50) 이다. */
    private static final int NAME_MAX_LENGTH = 100;
    private static final int CATEGORY_MAX_LENGTH = 50;

    /** 재료가 수십 개 오면 화면도 못 쓰고 저장만 무거워진다. */
    private static final int INGREDIENT_MAX_COUNT = 12;

    private final VisionClient visionClient;
    private final ObjectMapper objectMapper;

    /**
     * 표준 음식 테이블을 기동 때 적재한다. 놔두면 첫 요청이 316KB 파싱을 물게 되는데,
     * 그 첫 요청이 기록 저장이면 `findForUpdate` 로 잡은 행 잠금과 커넥션을 쥔 채로
     * 파일을 읽는다. 리소스가 깨져 있어도 사용자가 아니라 기동 로그에서 먼저 드러난다.
     */
    @PostConstruct
    void loadStandardFoodTable() {
        StandardFoodTable.size();
    }

    /** 트랜잭션 밖에서 부른다. */
    public OpenAiFoodResult recognize(MultipartFile image) {
        EncodedImage encoded = ImageEncoder.encode(image);
        OpenAiFoodResult aiResult = visionClient.analyzeFood(encoded.base64(), encoded.mediaType());

        // 음식이 아니면 저장하지 않는다. 남겨두면 사용자의 기록에 정체불명의 행이 쌓인다.
        //
        // 이름이 비어 있는 경우도 같이 막는다. 스키마가 required 로 강제하지만 그건
        // OpenAI 쪽 약속이고, 빈 이름이 통과하면 food_name 이 NOT NULL 인 저장 단계에서
        // 500 이 난다 — 유료 호출이 끝난 뒤라 되돌릴 수도 없다. 이름을 못 붙였다는 건
        // 음식을 인식하지 못했다는 뜻이므로 같은 422 로 내린다.
        if (!aiResult.foodDetected() || isBlank(aiResult.foodName())) {
            throw new BusinessException(ErrorCode.FOOD_NOT_DETECTED);
        }
        return aiResult;
    }

    /**
     * 트랜잭션 안에서 부른다. 아직 저장하지는 않는다 — 호출자가 Repository 에 넘긴다.
     *
     * 표준값 덮어쓰기가 여기서 일어난다. 룰 엔진은 sodiumMg 를 1500과 비교하는데
     * 그 값이 AI 추정치면 같은 사진에 세 번 다른 점수가 나온다. 공공데이터 기반
     * 표준 테이블에서 찾히면 그 값으로 바꿔 재현성을 확보한다.
     * (설계서 §1.22.1 · {@link StandardFoodTable})
     */
    public FoodAnalysis toEntity(AppUser user, OpenAiFoodResult aiResult) {
        return toEntity(user, aiResult, null);
    }

    /**
     * 기록 저장(POST /plates/records)이 쓰는 경로. jti 를 raw_ai_response 에
     * 형제 키로 얹어 멱등키로 남긴다 — null 이면 _meta 를 붙이지 않는다(2인자 오버로드).
     */
    public FoodAnalysis toEntity(AppUser user, OpenAiFoodResult aiResult, String jti) {
        String foodName = trim(aiResult.foodName(), NAME_MAX_LENGTH);

        Nutrition aiNutrition = toNutrition(aiResult.nutrition());
        Optional<StandardFood> standard = StandardFoodTable.find(foodName);

        // 표준 DB 에 있으면 영양값뿐 아니라 조리법·매운맛까지 고정한다.
        // 영양값만 고정하면 R02(매운맛)·R07(튀김)이 여전히 AI 추정에 흔들려
        // 같은 사진에서 점수가 갈린다 — 재현성이 반쪽이 된다.
        //
        // 다만 셋의 확신도가 다르다. 영양값은 표준 DB 가 항상 이긴다(룰이 비교하는
        // 숫자가 그것뿐이다). 조리법·매운맛은 이름에서 뽑은 값이라 사진을 본 AI 보다
        // 확실할 때만 이긴다 — ETC 와 spicy=false 는 "아니다"가 아니라 "이름만 봐서는
        // 모르겠다"는 뜻이므로 AI 의 답을 지우지 않는다.
        Nutrition nutrition = standard
                .map(food -> food.toNutrition(aiNutrition))
                .orElse(aiNutrition);
        CookingMethod cookingMethod = standard
                .map(StandardFood::cookingMethod)
                .filter(method -> method != CookingMethod.ETC)
                .orElseGet(() -> toCookingMethod(aiResult.cookingMethod()));
        boolean spicy = standard.map(StandardFood::spicy).orElse(false) || aiResult.spicy();

        // 이름이 그대로면 debug 로 충분하지만, 다른 이름의 값으로 바뀌었다면 그게 요점이다.
        // "돈코츠 라멘" 이 "라멘" 값을 받은 걸 배포 서버(기본 INFO)에서 볼 수 없으면,
        // 점수가 사진과 무관한 숫자로 계산돼도 사용자도 로그도 알 방법이 없다.
        standard.ifPresent(food -> {
            String message = "표준 음식 적용: {} → {} ({}, 표본 {}건)";
            if (food.name().equals(foodName)) {
                log.debug(message, foodName, food.name(),
                        food.measured() ? "실측" : "산출", food.sampleCount());
            } else {
                log.info(message, foodName, food.name(),
                        food.measured() ? "실측" : "산출", food.sampleCount());
            }
        });

        FoodAnalysis food = FoodAnalysis.create(
                user,
                foodName,
                trim(aiResult.foodCategory(), CATEGORY_MAX_LENGTH),
                nutrition,
                cookingMethod,
                spicy,
                toJson(aiResult, jti));

        food.addIngredients(toIngredients(aiResult.ingredients(), standard.orElse(null)));
        return food;
    }

    /**
     * AI 가 사진에서 읽은 재료를 그대로 쓰되, 표준 DB 가 아는 태그를 보탠다.
     *
     * AI 를 지우지 않는 이유 — 사진에는 이름에 없는 재료가 보인다("김치찌개"에
     * 올라간 두부). 표준 DB 를 보태는 이유 — 이름에서 확실히 아는 태그(김치→발효)를
     * AI 가 어떤 날 빠뜨리면 그날만 점수가 달라진다. 둘은 서로를 대체하지 않는다.
     */
    private List<FoodIngredient> toIngredients(List<OpenAiFoodResult.Ingredient> ingredients,
                                               StandardFood standard) {
        List<FoodIngredient> result = new ArrayList<>();
        Set<IngredientTag> seen = EnumSet.noneOf(IngredientTag.class);

        if (ingredients != null) {
            ingredients.stream()
                    .filter(ingredient -> ingredient.name() != null && !ingredient.name().isBlank())
                    .limit(INGREDIENT_MAX_COUNT)
                    .forEach(ingredient -> {
                        IngredientTag tag = toIngredientTag(ingredient.tag());
                        result.add(FoodIngredient.of(trim(ingredient.name(), 50), tag));
                        seen.add(tag);
                    });
        }

        if (standard != null) {
            for (IngredientTag tag : standard.tags()) {
                // ETC 는 "모르겠다"는 뜻이라 보탤 값이 없다.
                if (tag == IngredientTag.ETC || !seen.add(tag)) continue;
                if (result.size() >= INGREDIENT_MAX_COUNT) break;
                // 위 AI 경로와 같은 이유로 자른다 — food_ingredient.name 은 VARCHAR(50) 이고,
                // 표준 DB 이름은 스크립트가 만든다(`곱창전골_간편조리세트_…`).
                result.add(FoodIngredient.of(trim(standard.name(), 50), tag));
            }
        }
        return result;
    }

    /**
     * 스키마가 enum 을 강제하지만 그건 OpenAI 쪽 약속이다. 모르는 값이 한 번 오면
     * valueOf 가 예외를 던지고, 18초짜리 유료 호출이 그 자리에서 버려진다.
     * 태그를 못 읽으면 룰 하나가 안 걸릴 뿐이므로 ETC 로 흘려보내는 편이 낫다.
     */
    private IngredientTag toIngredientTag(String tag) {
        if (tag == null) return IngredientTag.ETC;
        try {
            return IngredientTag.valueOf(tag);
        } catch (IllegalArgumentException e) {
            log.warn("모르는 재료 태그: {}", tag);
            return IngredientTag.ETC;
        }
    }

    private CookingMethod toCookingMethod(String cookingMethod) {
        if (cookingMethod == null) return CookingMethod.ETC;
        try {
            return CookingMethod.valueOf(cookingMethod);
        } catch (IllegalArgumentException e) {
            log.warn("모르는 조리 방식: {}", cookingMethod);
            return CookingMethod.ETC;
        }
    }

    private Nutrition toNutrition(OpenAiFoodResult.Nutrition nutrition) {
        if (nutrition == null) {
            return Nutrition.of(0, BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }

        return Nutrition.of(
                nutrition.caloriesKcal(), nutrition.proteinG(), nutrition.fatG(),
                nutrition.carbG(), nutrition.sodiumMg(), nutrition.sugarG());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 컬럼 길이를 넘기면 저장에서 터진다. 그 시점엔 유료 호출이 이미 끝나 있다. */
    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;

        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }

    /**
     * raw_ai_response 는 jsonb 다. 프롬프트를 바꿔도 과거 데이터를 재해석할 수 있다.
     *
     * jti 가 있으면 트리에 _meta 형제 키 하나만 얹는다. 문자열을 직접 조작하거나
     * Map 으로 옮겨 담으면 기존 키 순서·타입이 흔들린다 — valueToTree 로 트리를 얻어
     * 얹은 뒤 다시 직렬화해야 원본 키가 그대로 살아남는다.
     */
    private String toJson(OpenAiFoodResult aiResult, String jti) {
        try {
            ObjectNode node = objectMapper.valueToTree(aiResult);
            if (jti != null) {
                node.putObject("_meta").put("jti", jti);
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED, e);
        }
    }
}
