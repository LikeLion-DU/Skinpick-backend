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
import com.skinplate.api.domain.plate.dto.PlateAnalysisSimulateResponse;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.dto.SkinPlateResponse;
import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateEvaluation;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.MealType;
import com.skinplate.api.domain.plate.entity.PlateActionCode;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.common.DateRange;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.security.AnalysisTokenPayload;
import com.skinplate.api.global.security.AnalysisTokenProvider;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.PlateComments;
import com.skinplate.api.infra.openai.prompt.PlateCommentPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 상극 분석. 엔진은 이미 완성돼 있고 이 클래스는 순서와 트랜잭션 경계만 책임진다.
 *
 * 점수를 LLM 에 맡기지 않는 이유가 여기서 드러난다 — AI 는 "무슨 음식인가"까지만
 * 판단하고, 그 결과를 PlateRuleEngine 이 피부 지표와 맞춰 점수를 낸다.
 */
@Slf4j
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
    private final VisionClient visionClient;

    /**
     * 저장하지 않는다. 결과와 서명 토큰만 돌려주고, 저장은 이 토큰을 되받는
     * POST /plates/records 가 한다.
     *
     * 기준이 될 피부 분석이 있는지는 <b>유료 호출 전에</b> 본다. 인덱스 읽기 한 번이다.
     * 뒤로 미루면 피부 분석을 한 번도 안 한 사용자가 20초를 기다린 끝에 404 를 보고,
     * 그 요청마다 gpt-4o 호출이 한 번씩 버려진다. 남의 id·오래된 id 도 마찬가지다.
     *
     * skinAnalysisId 는 선택이다. 생략하면 최신 피부 분석을 쓴다 —
     * 앱이 홈에서 바로 음식만 찍고 들어오는 경로가 있기 때문이다.
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

    /**
     * analyze() 가 발급한 토큰을 되받아 기록을 저장한다. 점수는 여기서 다시 계산된다 —
     * 토큰이 나르는 건 AI 원본(payload.food())뿐이고, 클라이언트가 보낸 점수·영양값은
     * 애초에 받지 않는다(PlateRecordRequest 필드는 analysisToken 하나뿐이라 조작할 대상이 없다).
     *
     * 락 · jti 조회 · 저장이 반드시 한 트랜잭션 안에 있어야 한다. 피부 분석 조회를
     * analyze() 처럼 트랜잭션 밖으로 꺼내면 findForUpdate 락이 트랜잭션 없이 걸려
     * TransactionRequiredException 이 난다 — 저장할 AI 응답이 이미 있으니(재호출이 없으니)
     * 트랜잭션을 아낄 이유도 없다. 처음부터 안에 둔다.
     */
    public SkinPlateResponse saveRecord(Long userId, String analysisToken) {
        AnalysisTokenPayload payload = analysisTokenProvider.parse(analysisToken, userId);

        // 같은 토큰의 재시도면 유료 AI 호출 전에 알아챈다 — analyze() 의
        // "유료 호출 전 인덱스 읽기 한 번"과 같은 원칙이다. 락 없는 선조회라
        // 동시 요청 경쟁은 못 막지만 그건 아래 트랜잭션 안의 재확인이 맡는다.
        // 여기서 아끼는 것은 정확성이 아니라 25초와 과금이다.
        boolean alreadySaved =
                foodAnalysisRepository.findIdByUserIdAndJti(userId, payload.jti()).isPresent();

        // AI 문장은 트랜잭션 밖에서 만든다 — OpenAI 를 트랜잭션 안에서 부르면
        // 응답을 기다리는 내내 커넥션을 쥐고 있게 된다. 실패하면 문장 없이 저장한다.
        // 문장은 부가 정보고, 기록이 본체다.
        PlateComments comments = alreadySaved
                ? PlateComments.EMPTY
                : generateCommentsSafely(userId, payload);

        return transactionTemplate.execute(status -> {
            // 소유 확인 — 토큰의 skinAnalysisId 로 조회한다. 최신 분석으로 갈아타지 않는다.
            SkinAnalysis skinAnalysis = resolveSkinAnalysis(userId, payload.skinAnalysisId());

            // 이 분석 행에 줄을 세운다. RecommendationService.createOnce 와 같은 방식 —
            // 반환값은 쓰지 않는다, 목적은 동시 요청을 순서대로 세우는 것뿐이다.
            skinAnalysisRepository.findForUpdate(skinAnalysis.getId());

            Optional<Long> existingFoodId =
                    foodAnalysisRepository.findIdByUserIdAndJti(userId, payload.jti());

            if (existingFoodId.isPresent()) {
                // 같은 토큰이 다시 왔다 — 새로 만들지 않고 이미 저장된 기록을 그대로 돌려준다.
                SkinPlate existing = skinPlateRepository
                        .findByFoodAnalysisIdAndUserId(existingFoodId.get(), userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PLATE_NOT_FOUND));
                return SkinPlateResponse.from(existing, parseAppliedRules(existing.getAppliedRules()));
            }

            return save(userId, payload.food(), skinAnalysis.getId(), payload.jti(), comments);
        });
    }

    /**
     * 룰 엔진 결과와 오늘의 기록을 모아 AI 에 문장 두 개를 청탁한다.
     * <b>여기서 점수는 이미 다 계산돼 있다</b> — AI 입력은 결과지, 재료가 아니다.
     *
     * 어떤 예외든 EMPTY 로 삼킨다. 문장 생성이 저장을 막는 순간
     * "AI 없이도 도는 제품"이라는 전제가 무너진다.
     */
    private PlateComments generateCommentsSafely(Long userId, AnalysisTokenPayload payload) {
        try {
            SkinAnalysis skinAnalysis = resolveSkinAnalysis(userId, payload.skinAnalysisId());
            FoodAnalysis food = foodAnalysisService.toEntity(null, payload.food());
            PlateEvaluation evaluation =
                    engine.evaluate(new PlateContext(skinAnalysis.getMetrics(), food));

            // 오늘 이미 저장된 기록들. 저장 시각은 KST 로 고정돼 있다(JpaConfig).
            LocalDateTime todayStart = LocalDate.now(DateRange.KST).atStartOfDay();
            List<String> todaysRecords = skinPlateRepository
                    .findInRange(userId, todayStart, todayStart.plusDays(1))
                    .stream()
                    .map(plate -> mealLabel(plate.getCreatedAt()) + " "
                            + plate.getFoodAnalysis().getFoodName() + " "
                            + plate.getPlateScore() + "점")
                    .collect(Collectors.toList());

            return visionClient.generateComments(PlateCommentPrompt.user(
                    skinAnalysis.getMetrics(),
                    food.getFoodName(),
                    evaluation.score(),
                    evaluation.toFeedbacks(),
                    todaysRecords));
        } catch (Exception e) {
            log.warn("AI 코멘트 생성 실패 — 문장 없이 저장한다", e);
            return PlateComments.EMPTY;
        }
    }

    /** AI 컨텍스트용 한국어 끼니 라벨. 화면 표기는 앱이 따로 한다. */
    private static String mealLabel(LocalDateTime recordedAt) {
        return switch (MealType.from(recordedAt)) {
            case BREAKFAST -> "아침";
            case LUNCH -> "점심";
            case DINNER -> "저녁";
        };
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

    /**
     * simulate() 와 하는 일은 같다. 다른 점은 입력을 어디서 얻느냐뿐이다 — 저장된 Plate
     * 대신 analyze() 가 발급한 토큰이 skin·food 를 나른다. 결과 화면에는 plateId 가 없어서
     * (저장 전이므로) {@code /{plateId}/simulate} 를 부를 수 없는 것을 이 엔드포인트가 대신한다.
     *
     * readOnly = true 인 이유는 simulate() 와 같다 — resolveSkinAnalysis 가 DB 를 읽을 뿐
     * 저장은 없고, 관리 엔티티를 실수로 만지는 안전망이 필요하다.
     */
    @Transactional(readOnly = true)
    public PlateAnalysisSimulateResponse simulateFromToken(
            Long userId, String analysisToken, List<PlateActionCode> actions) {
        AnalysisTokenPayload payload = analysisTokenProvider.parse(analysisToken, userId);
        SkinMetrics skin = resolveSkinAnalysis(userId, payload.skinAnalysisId()).getMetrics();

        // user = null — detachedCopy() 와 같은 이유로 저장 불가 상태로 만들어
        // 실수로 persist 되는 것을 막는다. toEntity 를 태우는 이유는 analyze() 와 같다 —
        // 표준 영양값 덮어쓰기·trim 을 건너뛰면 beforeScore 가 analyze 의 점수와 갈라진다.
        FoodAnalysis origin = foodAnalysisService.toEntity(null, payload.food());
        FoodAnalysis simulated = detachedCopy(origin, actions);

        PlateEvaluation before = engine.evaluate(new PlateContext(skin, origin));
        PlateEvaluation after = engine.evaluate(new PlateContext(skin, simulated));

        return PlateAnalysisSimulateResponse.of(
                before.score(), after.score(), actions,
                removedRules(before, after), buildActionSummary(actions));
    }

    // ---- 내부 ----

    /** jti 는 멱등키다. 저장 경로가 saveRecord() 하나뿐이므로 항상 값이 있다. */
    private SkinPlateResponse save(Long userId, OpenAiFoodResult aiResult, Long skinAnalysisId,
                                   String jti, PlateComments comments) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        SkinAnalysis skinAnalysis = resolveSkinAnalysis(userId, skinAnalysisId);

        FoodAnalysis food = foodAnalysisRepository.save(
                foodAnalysisService.toEntity(user, aiResult, jti));

        PlateEvaluation evaluation =
                engine.evaluate(new PlateContext(skinAnalysis.getMetrics(), food));

        SkinPlate plate = SkinPlate.create(user, skinAnalysis, food,
                evaluation.score(), evaluation.summary(),
                toJson(evaluation.appliedRuleCodes()));
        plate.addFeedbacks(evaluation.toFeedbacks());
        plate.attachAiComments(comments.aiTip(), comments.dailyComment());

        skinPlateRepository.save(plate);

        return SkinPlateResponse.from(plate, evaluation.appliedRuleCodes());
    }

    /**
     * skinAnalysisId 를 생략하면 최신 피부 분석을 쓴다. 한 번도 안 찍었으면 404 다 —
     * 비교할 기준이 없으면 상극 분석이 성립하지 않는다.
     */
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
