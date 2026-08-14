package com.skinplate.api.domain.plate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.dto.FoodAnalysisDto;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.repository.FoodAnalysisRepository;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.plate.dto.FeedbackGroupDto;
import com.skinplate.api.domain.plate.dto.PlateAnalysisResponse;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.dto.SkinPlateResponse;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateEvaluation;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.PlateActionCode;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.security.AnalysisTokenProvider;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 상극 분석. 엔진은 이미 완성돼 있고 이 클래스는 순서와 트랜잭션 경계만 책임진다.
 *
 * 점수를 LLM 에 맡기지 않는 이유가 여기서 드러난다 — AI 는 "무슨 음식인가"까지만
 * 판단하고, 그 결과를 PlateRuleEngine 이 피부 지표와 맞춰 점수를 낸다.
 */
@Service
@RequiredArgsConstructor
public class SkinPlateService {

    private static final BigDecimal SUGAR_WITHOUT_DRINK = new BigDecimal("0.4");

    private final AppUserRepository userRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;
    private final FoodAnalysisRepository foodAnalysisRepository;
    private final SkinPlateRepository skinPlateRepository;
    private final FoodAnalysisService foodAnalysisService;
    private final PlateRuleEngine engine;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisTokenProvider analysisTokenProvider;

    /**
     * AI 호출을 먼저 끝낸 뒤 저장 구간만 트랜잭션으로 감싼다.
     * 25초짜리 대기를 트랜잭션 안에 두면 커넥션 하나가 그동안 잠긴다.
     *
     * 단, 기준이 될 피부 분석이 있는지는 <b>유료 호출 전에</b> 본다. 인덱스 읽기 한 번이다.
     * 뒤로 미루면 피부 분석을 한 번도 안 한 사용자가 20초를 기다린 끝에 404 를 보고,
     * 그 요청마다 gpt-4o 호출이 한 번씩 버려진다. 남의 id·오래된 id 도 마찬가지다.
     */
    public SkinPlateResponse create(Long userId, MultipartFile image, Long skinAnalysisId) {
        Long resolvedId = resolveSkinAnalysisId(userId, skinAnalysisId);

        OpenAiFoodResult aiResult = foodAnalysisService.recognize(image);

        return transactionTemplate.execute(status -> save(userId, aiResult, resolvedId));
    }

    /**
     * 저장하지 않는다. 결과와 서명 토큰만 돌려주고, 저장은 이 토큰을 되받는
     * POST /plates/records(Task 3) 가 한다. 순서는 create() 와 같다 — 피부 분석
     * 확인이 AI 호출보다 먼저다. 이유도 같다: 유료 호출 전에 404 를 걸러야 한다.
     *
     * 트랜잭션을 열지 않는다. 저장이 없으니 감쌀 구간도 없다.
     */
    public PlateAnalysisResponse analyze(Long userId, MultipartFile image, Long skinAnalysisId) {
        SkinAnalysis skinAnalysis = resolveSkinAnalysis(userId, skinAnalysisId);

        OpenAiFoodResult aiResult = foodAnalysisService.recognize(image);

        // user = null — detachedCopy() 와 같은 이유로 저장 불가 상태로 만들어
        // 실수로 persist 되는 것을 막는다. toEntity 를 그대로 태우는 이유는 표준
        // 영양값 덮어쓰기·문자열 trim 이 여기서 일어나기 때문이다 — 건너뛰면 analyze
        // 의 점수와 Task 3 의 저장 점수가 달라진다.
        FoodAnalysis food = foodAnalysisService.toEntity(null, aiResult);

        PlateEvaluation evaluation =
                engine.evaluate(new PlateContext(skinAnalysis.getMetrics(), food));

        // 토큰에는 AI 원본(aiResult)을 담는다. FoodAnalysis 엔티티가 아니다 —
        // Task 3 이 이 토큰을 받아 같은 toEntity 를 다시 태워야 같은 점수가 나온다.
        String analysisToken = analysisTokenProvider.issue(userId, skinAnalysis.getId(), aiResult);

        return new PlateAnalysisResponse(
                analysisToken,
                skinAnalysis.getId(),
                evaluation.score(),
                RuleConstants.BASE_SCORE,
                evaluation.summary(),
                FoodAnalysisDto.from(food),
                FeedbackGroupDto.from(evaluation.toFeedbacks()),
                evaluation.appliedRuleCodes());
    }

    @Transactional(readOnly = true)
    public SkinPlateResponse get(Long userId, Long plateId) {
        SkinPlate plate = skinPlateRepository.findByIdAndUserId(plateId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLATE_NOT_FOUND));

        return SkinPlateResponse.from(plate, parseAppliedRules(plate.getAppliedRules()));
    }

    /**
     * 추천 행동을 실행했다고 가정하고 다시 계산한다. **저장하지 않는다.**
     *
     * readOnly = true 가 핵심 안전망이다. 누군가 실수로 원본을 건드려도 Hibernate 가
     * FlushMode.MANUAL 로 동작해 변경이 DB 로 나가지 않는다. 주석만으로는 부족하다 —
     * 문제는 저장 여부가 아니라 관리 엔티티를 만지는 것 자체다. (설계서 §1.19.2)
     */
    @Transactional(readOnly = true)
    public PlateSimulateResponse simulate(Long userId, Long plateId, List<PlateActionCode> actions) {
        SkinPlate plate = skinPlateRepository.findByIdAndUserId(plateId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLATE_NOT_FOUND));

        FoodAnalysis origin = plate.getFoodAnalysis();
        FoodAnalysis simulated = detachedCopy(origin, actions);
        SkinMetrics skin = plate.getSkinAnalysis().getMetrics();

        // appliedRules 는 JSON 문자열이다. 파싱하는 것보다 원본으로 한 번 더 부르는 게 싸다.
        // 엔진은 DB 를 건드리지 않는 순수 계산이라 재호출이 사실상 공짜다.
        PlateEvaluation before = engine.evaluate(new PlateContext(skin, origin));
        PlateEvaluation after = engine.evaluate(new PlateContext(skin, simulated));

        return PlateSimulateResponse.of(
                plate.getId(), plate.getPlateScore(), after.score(),
                actions, removedRules(before, after), buildActionSummary(actions));
    }

    // ---- 내부 ----

    private SkinPlateResponse save(Long userId, OpenAiFoodResult aiResult, Long skinAnalysisId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        SkinAnalysis skinAnalysis = resolveSkinAnalysis(userId, skinAnalysisId);

        FoodAnalysis food = foodAnalysisRepository.save(
                foodAnalysisService.toEntity(user, aiResult));

        PlateEvaluation evaluation =
                engine.evaluate(new PlateContext(skinAnalysis.getMetrics(), food));

        SkinPlate plate = SkinPlate.create(user, skinAnalysis, food,
                evaluation.score(), evaluation.summary(),
                toJson(evaluation.appliedRuleCodes()));
        plate.addFeedbacks(evaluation.toFeedbacks());

        skinPlateRepository.save(plate);

        return SkinPlateResponse.from(plate, evaluation.appliedRuleCodes());
    }

    /**
     * skinAnalysisId 를 생략하면 최신 피부 분석을 쓴다. 한 번도 안 찍었으면 404 다 —
     * 비교할 기준이 없으면 상극 분석이 성립하지 않는다.
     */
    private Long resolveSkinAnalysisId(Long userId, Long skinAnalysisId) {
        return resolveSkinAnalysis(userId, skinAnalysisId).getId();
    }

    private SkinAnalysis resolveSkinAnalysis(Long userId, Long skinAnalysisId) {
        if (skinAnalysisId != null) {
            // 타인의 id 면 403 이 아니라 404 다. 존재 여부 자체를 알려주지 않는다.
            return skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));
        }

        return skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND,
                        "먼저 피부 분석을 한 번 진행해 주세요."));
    }

    /**
     * user = null 인 복사본. 영속성 컨텍스트에 들어가지 않으므로 저장될 길이 없다.
     *
     * 원본을 그 자리에서 고치면 LESS_SPICY 가 orphanRemoval 컬렉션에서 CAPSAICIN 을
     * 지우는 순간 food_ingredient 행이 DELETE 되고, HALVE_SOUP 은 sodium_mg 를
     * 영구히 절반으로 바꾼다. 무대에서 버튼을 누르면 68 이 뜨고, 뒤로 갔다 다시
     * 들어오면 원래 점수가 68 이다.
     */
    private FoodAnalysis detachedCopy(FoodAnalysis origin, List<PlateActionCode> actions) {
        boolean lessSpicy = actions.contains(PlateActionCode.LESS_SPICY);
        boolean removeBatter = actions.contains(PlateActionCode.REMOVE_BATTER);

        FoodAnalysis copy = FoodAnalysis.create(
                null,                                   // ← 저장 불가 상태로 만든다
                origin.getFoodName(),
                origin.getFoodCategory(),
                adjustNutrition(origin.getNutrition(), actions),
                removeBatter ? CookingMethod.GRILLED : origin.getCookingMethod(),
                !lessSpicy && origin.isSpicy(),
                "{}");

        origin.getIngredients().stream()
                .filter(ingredient -> !(lessSpicy && ingredient.getTag() == IngredientTag.CAPSAICIN))
                .forEach(ingredient -> copy.addIngredient(
                        FoodIngredient.of(ingredient.getName(), ingredient.getTag())));

        return copy;
    }

    /**
     * REMOVE_BATTER 는 영양값을 건드리지 않는다. 어떤 룰도 지방을 보지 않아
     * 점수에 영향이 0 이고, 실제 효과는 cookingMethod = GRILLED 로 R07 이 꺼지는 것뿐이다.
     */
    private Nutrition adjustNutrition(Nutrition nutrition, List<PlateActionCode> actions) {
        int sodium = actions.contains(PlateActionCode.HALVE_SOUP)
                ? nutrition.getSodiumMg() / 2
                : nutrition.getSodiumMg();

        BigDecimal sugar = actions.contains(PlateActionCode.NO_SUGAR_DRINK)
                ? nutrition.getSugarG().multiply(SUGAR_WITHOUT_DRINK)
                : nutrition.getSugarG();

        return Nutrition.of(nutrition.getCaloriesKcal(), nutrition.getProteinG(),
                nutrition.getFatG(), nutrition.getCarbG(), sodium, sugar);
    }

    /** 행동으로 사라진 감점 룰. S07 에서 "나트륨 과다 카드가 없어졌다"를 보여주는 데 쓴다. */
    private List<String> removedRules(PlateEvaluation before, PlateEvaluation after) {
        List<String> remaining = after.appliedRuleCodes();

        return before.appliedRuleCodes().stream()
                .filter(code -> !remaining.contains(code))
                .toList();
    }

    /**
     * 조사(을/를)를 붙이면 행동 문구 끝소리에 따라 비문이 된다.
     * 라벨을 나열하고 줄표로 잇는 편이 안전하다.
     */
    private String buildActionSummary(List<PlateActionCode> actions) {
        String labels = actions.stream()
                .map(PlateActionCode::getLabel)
                .collect(Collectors.joining(" · "));

        return labels + " — 실행했을 때의 예상 점수입니다.";
    }

    private List<String> parseAppliedRules(String json) {
        if (json == null || json.isBlank()) return List.of();

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            // 룰 코드 목록은 화면의 계산 내역 카드에만 쓰인다.
            // 저장된 JSON 이 깨졌다고 결과 전체를 못 보여줄 이유는 없다.
            return List.of();
        }
    }

    private String toJson(List<String> appliedRuleCodes) {
        try {
            return objectMapper.writeValueAsString(appliedRuleCodes);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
