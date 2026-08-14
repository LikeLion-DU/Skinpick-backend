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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

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
     * 표준 영양값 덮어쓰기가 여기서 일어난다. 룰 엔진은 sodiumMg 를 1500과 비교하는데
     * 그 값이 AI 추정치면 같은 사진에 세 번 다른 점수가 나온다. 시연 음식 3종만
     * 표준 DB 값으로 바꿔 재현성을 확보한다. (설계서 §1.22.1)
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

        Nutrition nutrition = StandardNutrition.find(foodName)
                .orElseGet(() -> toNutrition(aiResult.nutrition()));

        if (StandardNutrition.isStandard(foodName)) {
            log.debug("표준 영양값 적용: {}", foodName);
        }

        FoodAnalysis food = FoodAnalysis.create(
                user,
                foodName,
                trim(aiResult.foodCategory(), CATEGORY_MAX_LENGTH),
                nutrition,
                toCookingMethod(aiResult.cookingMethod()),
                aiResult.spicy(),
                toJson(aiResult, jti));

        food.addIngredients(toIngredients(aiResult.ingredients()));
        return food;
    }

    private List<FoodIngredient> toIngredients(List<OpenAiFoodResult.Ingredient> ingredients) {
        if (ingredients == null) return List.of();

        return ingredients.stream()
                .filter(ingredient -> ingredient.name() != null && !ingredient.name().isBlank())
                .limit(INGREDIENT_MAX_COUNT)
                .map(ingredient -> FoodIngredient.of(
                        trim(ingredient.name(), 50), toIngredientTag(ingredient.tag())))
                .toList();
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
