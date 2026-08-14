package com.skinplate.api.domain.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.repository.FoodAnalysisRepository;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.plate.dto.PlateAnalysisResponse;
import com.skinplate.api.domain.plate.dto.PlateAnalysisSimulateResponse;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.dto.SkinPlateResponse;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.plate.entity.PlateActionCode;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.plate.service.SkinPlateService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.security.AnalysisTokenPayload;
import com.skinplate.api.global.security.AnalysisTokenProvider;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 시뮬레이션이 이 서비스에서 유일하게 위험한 지점이다. 원본을 그 자리에서 고치면
 * LESS_SPICY 가 orphanRemoval 컬렉션에서 CAPSAICIN 을 지우는 순간 재료 행이 DELETE 되고
 * HALVE_SOUP 이 나트륨을 영구히 절반으로 바꾼다. 무대에서 버튼을 누르면 68 이 뜨고,
 * 뒤로 갔다 다시 들어오면 원래 점수가 68 이 되어 있다.
 *
 * 엔진 자체는 PlateRuleEngineTest 가 지킨다. 여기서는 조립 결과와 원본 불변만 본다.
 */
class SkinPlateServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PLATE_ID = 10L;
    private static final Long ANALYSIS_ID = 100L;

    private AppUserRepository userRepository;
    private SkinAnalysisRepository skinAnalysisRepository;
    private FoodAnalysisRepository foodAnalysisRepository;
    private SkinPlateRepository skinPlateRepository;
    private FoodAnalysisService foodAnalysisService;
    private AnalysisTokenProvider analysisTokenProvider;
    private TransactionTemplate transactionTemplate;
    private PlateRuleEngine engine;
    private SkinPlateService skinPlateService;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        foodAnalysisRepository = mock(FoodAnalysisRepository.class);
        skinPlateRepository = mock(SkinPlateRepository.class);
        foodAnalysisService = mock(FoodAnalysisService.class);
        analysisTokenProvider = mock(AnalysisTokenProvider.class);

        transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));

        engine = new PlateRuleEngine(List.of(
                new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
                new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
                new ProteinRule(), new VitaminRule(), new ProbioticRule()));

        skinPlateService = new SkinPlateService(
                userRepository, skinAnalysisRepository, foodAnalysisRepository,
                skinPlateRepository, foodAnalysisService, engine,
                new ObjectMapper(), transactionTemplate, analysisTokenProvider);
    }

    @Test
    @DisplayName("무대에서 말할 숫자가 그대로 나온다 — 60 → 국물 절반 68 → 매운 양념까지 80")
    void simulate_reproducesDemoNumbers() {
        givenPlate();

        assertThat(simulate(PlateActionCode.HALVE_SOUP).afterScore()).isEqualTo(68);
        assertThat(simulate(PlateActionCode.HALVE_SOUP, PlateActionCode.LESS_SPICY).afterScore())
                .isEqualTo(80);
    }

    @Test
    @DisplayName("before 는 저장된 점수 그대로다 — 시뮬레이션이 원본 점수를 덮어쓰지 않는다")
    void simulate_keepsStoredScoreAsBefore() {
        givenPlate();

        PlateSimulateResponse response = simulate(PlateActionCode.HALVE_SOUP);

        assertThat(response.beforeScore()).isEqualTo(60);
        assertThat(response.plateId()).isEqualTo(PLATE_ID);
    }

    @Test
    @DisplayName("세 번 돌려도 원본은 그대로다 — 재료도 나트륨도 매운맛도 안 바뀐다")
    void simulate_neverMutatesTheOriginal() {
        SkinPlate plate = givenPlate();
        FoodAnalysis origin = plate.getFoodAnalysis();

        int ingredientsBefore = origin.getIngredients().size();
        int sodiumBefore = origin.getNutrition().getSodiumMg();

        for (int attempt = 0; attempt < 3; attempt++) {
            simulate(PlateActionCode.HALVE_SOUP, PlateActionCode.LESS_SPICY);
        }

        // LESS_SPICY 가 CAPSAICIN 을 지우고 HALVE_SOUP 이 나트륨을 절반으로 바꾸는 대상은
        // 복사본이어야 한다. 여기가 깨지면 DB 의 food_ingredient 행이 사라진 것이다.
        assertThat(origin.getIngredients()).hasSize(ingredientsBefore);
        assertThat(origin.getNutrition().getSodiumMg()).isEqualTo(sodiumBefore);
        assertThat(origin.isSpicy()).isTrue();
        assertThat(plate.getPlateScore()).isEqualTo(60);
    }

    @Test
    @DisplayName("남의 plate 는 403 이 아니라 404 다")
    void simulate_otherUsersPlate_returns404() {
        given(skinPlateRepository.findByIdAndUserId(PLATE_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> simulate(PlateActionCode.HALVE_SOUP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("analyze 는 아무것도 저장하지 않는다 — foodAnalysisRepository·skinPlateRepository 둘 다 save 가 안 불린다")
    void analyze_savesNothing() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        given(foodAnalysisService.recognize(any())).willReturn(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(analysisTokenProvider.issue(USER_ID, ANALYSIS_ID, aiResult)).willReturn("signed-token");

        skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        verify(foodAnalysisRepository, never()).save(any());
        verify(skinPlateRepository, never()).save(any());
    }

    @Test
    @DisplayName("발급된 분석 토큰이 응답에 그대로 실린다")
    void analyze_returnsIssuedToken() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        given(foodAnalysisService.recognize(any())).willReturn(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(analysisTokenProvider.issue(USER_ID, ANALYSIS_ID, aiResult)).willReturn("signed-token");

        PlateAnalysisResponse response = skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        assertThat(response.analysisToken()).isEqualTo("signed-token");
        verify(analysisTokenProvider).issue(USER_ID, ANALYSIS_ID, aiResult);
    }

    @Test
    @DisplayName("analyze 도 피부 분석 확인이 AI 호출보다 먼저다")
    void analyze_withoutSkinAnalysis_failsBeforeCallingAi() {
        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);

        verify(foodAnalysisService, never()).recognize(any());
    }

    @Test
    @DisplayName("plateScore 는 엔진 결과와 같고 baseScore 는 항상 RuleConstants.BASE_SCORE 다")
    void analyze_scoreMatchesEngine() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        given(foodAnalysisService.recognize(any())).willReturn(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(analysisTokenProvider.issue(USER_ID, ANALYSIS_ID, aiResult)).willReturn("signed-token");

        PlateAnalysisResponse response = skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        assertThat(response.plateScore()).isEqualTo(60);
        assertThat(response.baseScore()).isEqualTo(RuleConstants.BASE_SCORE);

        // analyze 와 record(Task 3) 의 점수 동일성이 이 호출 하나에 걸려 있다.
        // toEntity 를 우회해 aiResult 로 FoodAnalysis 를 직접 만들면 표준 영양값
        // 덮어쓰기·문자열 trim 이 빠져, 결과 화면과 저장된 기록의 점수가 갈라진다.
        verify(foodAnalysisService).toEntity(null, aiResult);
    }

    @Test
    @DisplayName("최초 저장 — jti 조회가 비면 저장하고, AI 는 다시 부르지 않는다")
    void saveRecord_firstTime_savesAndNeverCallsAiAgain() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-first", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-first")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-first")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        skinPlateService.saveRecord(USER_ID, "token");

        verify(skinPlateRepository).save(any());
        // 인식은 analyze() 에서 이미 끝났다 — 저장 경로가 VisionClient 를 다시 태우면
        // 유료 호출이 두 번 나간다.
        verify(foodAnalysisService, never()).recognize(any());
    }

    @Test
    @DisplayName("_meta.jti 가 raw_ai_response 최상위 형제 키로 기록되고 기존 키는 그대로 남는다")
    void saveRecord_writesMetaJtiAsSiblingKey_andKeepsExistingTopLevelKeys() throws Exception {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-real", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-real")).willReturn(Optional.empty());

        ArgumentCaptor<FoodAnalysis> foodCaptor = ArgumentCaptor.forClass(FoodAnalysis.class);
        given(foodAnalysisRepository.save(foodCaptor.capture()))
                .willAnswer(invocation -> invocation.getArgument(0));

        // toEntity 는 mock 이 아니라 진짜를 태운다 — _meta 를 얹는 트리 조작이
        // 실제로 일어나는지, 기존 키가 흔들리지 않는지는 실제 구현으로만 확인된다.
        ObjectMapper realMapper = new ObjectMapper();
        FoodAnalysisService realFoodAnalysisService =
                new FoodAnalysisService(mock(VisionClient.class), realMapper);
        SkinPlateService serviceWithRealFoodAnalysis = new SkinPlateService(
                userRepository, skinAnalysisRepository, foodAnalysisRepository,
                skinPlateRepository, realFoodAnalysisService, engine,
                realMapper, transactionTemplate, analysisTokenProvider);

        serviceWithRealFoodAnalysis.saveRecord(USER_ID, "token");

        JsonNode raw = realMapper.readTree(foodCaptor.getValue().getRawAiResponse());
        assertThat(raw.path("_meta").path("jti").asText()).isEqualTo("jti-real");

        // 기존 최상위 키가 형제로 그대로 남아 있다 — 호환성의 핵심이다. _meta 를 얹는다고
        // foodName·nutrition 같은 원본 키가 사라지거나 다른 키 아래로 들어가면 안 된다.
        assertThat(raw.path("foodName").asText()).isEqualTo(aiResult.foodName());
        assertThat(raw.has("nutrition")).isTrue();
        assertThat(raw.path("nutrition").has("sodiumMg")).isTrue();
        assertThat(raw.has("ingredients")).isTrue();

        List<String> topLevelKeys = new ArrayList<>();
        raw.fieldNames().forEachRemaining(topLevelKeys::add);
        assertThat(topLevelKeys).contains(
                "foodDetected", "foodName", "foodCategory", "cookingMethod",
                "spicy", "ingredients", "nutrition", "_meta");
    }

    @Test
    @DisplayName("멱등 — jti 조회가 기존 id 를 반환하면 새로 저장하지 않고 기존 Plate 를 돌려준다")
    void saveRecord_idempotent_returnsExistingPlateWithoutSaving() {
        givenSkinAnalysis();
        SkinPlate existingPlate = givenPlate();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-dup", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-dup")).willReturn(Optional.of(555L));
        given(skinPlateRepository.findByFoodAnalysisIdAndUserId(555L, USER_ID))
                .willReturn(Optional.of(existingPlate));

        SkinPlateResponse response = skinPlateService.saveRecord(USER_ID, "token");

        assertThat(response.plateId()).isEqualTo(PLATE_ID);
        verify(skinPlateRepository, never()).save(any());
        verify(foodAnalysisRepository, never()).save(any());
        // 락은 "이미 저장돼 있다"는 답이 나오는 이 갈래에서도 잡혀야 한다 — 동시에 두 번
        // 누른 요청 중 하나가 바로 이 갈래로 떨어진다. 여기서 락을 건너뛰면 둘 다 jti
        // 조회를 먼저 통과해버려 멱등성이 무력화된다.
        verify(skinAnalysisRepository).findForUpdate(ANALYSIS_ID);
    }

    @Test
    @DisplayName("락을 잡는다 — findForUpdate → jti 조회 → save 순서로 호출된다")
    void saveRecord_locksSkinAnalysisRowForUpdate() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-lock", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-lock")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-lock")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        skinPlateService.saveRecord(USER_ID, "token");

        verify(skinAnalysisRepository).findForUpdate(ANALYSIS_ID);

        // 순서 자체가 멱등성의 전제다. 락보다 jti 조회가 먼저 오면 동시 요청 둘이
        // 모두 빈 결과를 보고 각각 저장한다 — 순서가 안 지켜지면 잠그는 의미가 없다.
        InOrder inOrder = inOrder(skinAnalysisRepository, foodAnalysisRepository, skinPlateRepository);
        inOrder.verify(skinAnalysisRepository).findForUpdate(ANALYSIS_ID);
        inOrder.verify(foodAnalysisRepository).findIdByUserIdAndJti(USER_ID, "jti-lock");
        inOrder.verify(skinPlateRepository).save(any());
    }

    @Test
    @DisplayName("재평가 — 토큰의 food 로 다시 계산된 점수가 응답에 실린다")
    void saveRecord_reEvaluatesScoreFromTokenFood() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-score", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-score")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-score")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        SkinPlateResponse response = skinPlateService.saveRecord(USER_ID, "token");

        // 문서의 시연 예시 그대로 — 같은 음식·같은 지표면 60점이 나와야 한다.
        assertThat(response.plateScore()).isEqualTo(60);
        // 점수만으로는 "무엇을" 평가했는지 알 수 없다 — 저장 경로가 토큰의 aiResult
        // 그대로를 toEntity 에 넘겼는지까지 확인해야 재평가 대상이 토큰의 food 임이 보장된다.
        verify(foodAnalysisService).toEntity(any(AppUser.class), eq(aiResult), eq("jti-score"));
    }

    @Test
    @DisplayName("남의 피부 분석 — 토큰의 skinAnalysisId 가 다른 사용자 것이면 403 이 아니라 404 다")
    void saveRecord_tokenPointsToOthersSkinAnalysis_returns404() {
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-other", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skinPlateService.saveRecord(USER_ID, "token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);

        verify(skinAnalysisRepository, never()).findForUpdate(any());
        verify(foodAnalysisService, never()).recognize(any());
    }

    @Test
    @DisplayName("simulateFromToken — 토큰의 food 로 계산한다, beforeScore 는 엔진의 before 결과와 같다")
    void simulateFromToken_evaluatesTokenFood() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());

        PlateAnalysisSimulateResponse response = simulateFromToken(PlateActionCode.HALVE_SOUP);

        assertThat(response.beforeScore()).isEqualTo(60);
        verify(foodAnalysisService).toEntity(null, aiResult);
    }

    @Test
    @DisplayName("simulateFromToken — 저장하지 않는다")
    void simulateFromToken_savesNothing() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());

        simulateFromToken(PlateActionCode.HALVE_SOUP);

        verify(foodAnalysisRepository, never()).save(any());
        verify(skinPlateRepository, never()).save(any());
    }

    @Test
    @DisplayName("simulateFromToken — AI 를 다시 부르지 않는다")
    void simulateFromToken_neverCallsAiAgain() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());

        simulateFromToken(PlateActionCode.HALVE_SOUP);

        verify(foodAnalysisService, never()).recognize(any());
    }

    @Test
    @DisplayName("simulateFromToken — 무대 숫자 그대로 움직인다: 60 → 국물 절반 68")
    void simulateFromToken_movesScoreLikeDemo() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());

        PlateAnalysisSimulateResponse response = simulateFromToken(PlateActionCode.HALVE_SOUP);

        assertThat(response.beforeScore()).isEqualTo(60);
        assertThat(response.afterScore()).isEqualTo(68);
        assertThat(response.afterScore()).isGreaterThan(response.beforeScore());
        assertThat(response.removedRules()).isNotEmpty();
    }

    @Test
    @DisplayName("simulateFromToken — 남의 피부 분석이면 403 이 아니라 404 다")
    void simulateFromToken_tokenPointsToOthersSkinAnalysis_returns404() {
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);
        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> simulateFromToken(PlateActionCode.HALVE_SOUP))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("simulateFromToken — beforeScore 가 analyze() 의 점수와 같다")
    void simulateFromToken_beforeScoreMatchesAnalyze() {
        givenSkinAnalysis();
        // 원본(2200)은 표준값(1850)과 다르다 — toEntity 를 타야만 표준화된 givenFood() 로
        // 수렴해 60점이 나온다. 원본을 그대로 쓰면 R04 초과분이 늘어 59점으로 갈라진다.
        OpenAiFoodResult aiResult = givenAiResult(2200);
        given(foodAnalysisService.recognize(any())).willReturn(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(analysisTokenProvider.issue(USER_ID, ANALYSIS_ID, aiResult)).willReturn("signed-token");

        PlateAnalysisResponse analyzeResponse = skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        givenAnalysisTokenPayload(aiResult);
        PlateAnalysisSimulateResponse simulateResponse = simulateFromToken(PlateActionCode.HALVE_SOUP);

        // toEntity 를 우회해 aiResult 로 FoodAnalysis 를 직접 만들면 이 등식이 깨진다 —
        // 표준 영양값 덮어쓰기·trim 이 analyze 에만 적용되고 시뮬레이션에는 빠지기 때문이다.
        assertThat(simulateResponse.beforeScore()).isEqualTo(analyzeResponse.plateScore());
    }

    // ---- 픽스처 ----

    private PlateSimulateResponse simulate(PlateActionCode... actions) {
        return skinPlateService.simulate(USER_ID, PLATE_ID, List.of(actions));
    }

    private PlateAnalysisSimulateResponse simulateFromToken(PlateActionCode... actions) {
        return skinPlateService.simulateFromToken(USER_ID, "token", List.of(actions));
    }

    /** simulateFromToken() 이 parse() 로 되받는 토큰 페이로드. jti 는 시뮬레이션에서 안 쓰인다. */
    private AnalysisTokenPayload givenAnalysisTokenPayload(OpenAiFoodResult aiResult) {
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-sim", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        return payload;
    }

    /** 문서의 시연 예시 그대로 — 지표 38/52/64/25/78 + 돼지고기 김치찌개 = 60점. */
    private SkinPlate givenPlate() {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        FoodAnalysis food = FoodAnalysis.create(user, "돼지고기 김치찌개", "한식/찌개",
                Nutrition.of(520, new BigDecimal("28.5"), new BigDecimal("24.0"),
                        new BigDecimal("32.0"), 1850, new BigDecimal("6.2")),
                CookingMethod.BOILED, true, "{}");
        List.of(new String[]{"돼지고기", "ETC"}, new String[]{"김치", "PROBIOTIC"},
                new String[]{"두부", "ETC"}, new String[]{"고춧가루", "CAPSAICIN"})
                .forEach(pair -> food.addIngredient(
                        FoodIngredient.of(pair[0], IngredientTag.valueOf(pair[1]))));

        SkinPlate plate = SkinPlate.create(user, analysis, food, 60, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", PLATE_ID);

        given(skinPlateRepository.findByIdAndUserId(PLATE_ID, USER_ID))
                .willReturn(Optional.of(plate));
        return plate;
    }

    /** analyze() 전용 픽스처. 지표는 givenPlate() 와 같은 시연 값 — 같은 음식이면 같은 60점이 나와야 한다. */
    private SkinAnalysis givenSkinAnalysis() {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(analysis));
        return analysis;
    }

    /** saveRecord() 의 save() 경로가 찾는 사용자. create() 경로 테스트에는 필요 없어 여기서만 쓴다. */
    private AppUser givenUser() {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    /** VisionClient 가 돌려줬다고 가정하는 AI 원본. */
    private OpenAiFoodResult givenAiResult() {
        return givenAiResult(1850);
    }

    /**
     * sodiumMg 를 바꿔 받는 버전. toEntity 를 우회해 이 원본값을 그대로 쓰면 표준화
     * 이전 나트륨이 새어나가 R04 감점 폭이 갈라진다 — simulateFromToken_beforeScoreMatchesAnalyze
     * 가 그 우회를 잡아내려면 aiResult 의 원본값이 표준값(1850)과 달라야 한다.
     */
    private OpenAiFoodResult givenAiResult(int sodiumMg) {
        return new OpenAiFoodResult(
                true, "돼지고기 김치찌개", "한식/찌개", "BOILED", true,
                List.of(new OpenAiFoodResult.Ingredient("돼지고기", "ETC"),
                        new OpenAiFoodResult.Ingredient("김치", "PROBIOTIC"),
                        new OpenAiFoodResult.Ingredient("두부", "ETC"),
                        new OpenAiFoodResult.Ingredient("고춧가루", "CAPSAICIN")),
                new OpenAiFoodResult.Nutrition(520, new BigDecimal("28.5"), new BigDecimal("24.0"),
                        new BigDecimal("32.0"), sodiumMg, new BigDecimal("6.2")));
    }

    /** foodAnalysisService.toEntity(null, aiResult) 가 돌려준다고 가정하는 결과 — user 는 null 이다. */
    private FoodAnalysis givenFood() {
        FoodAnalysis food = FoodAnalysis.create(null, "돼지고기 김치찌개", "한식/찌개",
                Nutrition.of(520, new BigDecimal("28.5"), new BigDecimal("24.0"),
                        new BigDecimal("32.0"), 1850, new BigDecimal("6.2")),
                CookingMethod.BOILED, true, "{}");
        List.of(new String[]{"돼지고기", "ETC"}, new String[]{"김치", "PROBIOTIC"},
                new String[]{"두부", "ETC"}, new String[]{"고춧가루", "CAPSAICIN"})
                .forEach(pair -> food.addIngredient(
                        FoodIngredient.of(pair[0], IngredientTag.valueOf(pair[1]))));
        return food;
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "food.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }
}
