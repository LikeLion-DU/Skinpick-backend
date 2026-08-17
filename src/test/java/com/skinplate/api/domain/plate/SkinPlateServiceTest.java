package com.skinplate.api.domain.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodGroup;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.FoodTraits;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.entity.Oiliness;
import com.skinplate.api.domain.food.entity.PortionSize;
import com.skinplate.api.domain.food.entity.ProcessingLevel;
import com.skinplate.api.domain.food.entity.Spiciness;
import com.skinplate.api.domain.food.repository.FoodAnalysisRepository;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.plate.dto.PlateAnalysisResponse;
import com.skinplate.api.domain.plate.dto.PlateAnalysisSimulateResponse;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.dto.SkinBasis;
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
import com.skinplate.api.global.common.DateRange;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.global.security.AnalysisTokenPayload;
import com.skinplate.api.global.security.AnalysisTokenProvider;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.PlateComments;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private VisionClient visionClient;
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
                new ProteinRule(), new VitaminRule(), new ProbioticRule(),
                new HighCalorieRule()));

        // 문장 생성은 이 테스트의 관심사가 아니다 — 실패해도 저장이 도는 게 계약이라
        // EMPTY 를 돌려주는 mock 으로 통과시킨다.
        visionClient = mock(VisionClient.class);
        given(visionClient.generateComments(any())).willReturn(PlateComments.EMPTY);

        skinPlateService = new SkinPlateService(
                userRepository, skinAnalysisRepository, foodAnalysisRepository,
                skinPlateRepository, foodAnalysisService, engine,
                new ObjectMapper(), transactionTemplate, analysisTokenProvider,
                visionClient);
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
    @DisplayName("before 는 지금 룰로 다시 센 점수다 — 저장 점수와 같은 한 값도 같다")
    void simulate_beforeScoreIsRecomputed() {
        givenPlate();

        PlateSimulateResponse response = simulate(PlateActionCode.HALVE_SOUP);

        assertThat(response.beforeScore()).isEqualTo(60);
        assertThat(response.plateId()).isEqualTo(PLATE_ID);
    }

    /**
     * 룰을 고친 뒤 옛 기록을 시뮬레이션하면 저장 점수와 재계산 점수가 갈린다.
     * 그때 저장값을 before 로 쓰면 한 응답이 두 규칙을 섞어 — 옛 룰의 70 옆에
     * 새 룰의 after·removedRules 가 붙어 — 설명할 수 없는 카드가 나온다.
     */
    @Test
    @DisplayName("저장 점수가 지금 룰과 어긋나도 before·after·removedRules 는 한 규칙으로 답한다")
    void simulate_neverMixesStoredAndRecomputedRules() {
        SkinPlate plate = givenPlate();
        // 옛 룰로 매겨진 것처럼 저장 점수만 어긋나게 둔다(엔진은 여전히 60을 낸다).
        ReflectionTestUtils.setField(plate, "plateScore", 70);

        PlateSimulateResponse response = simulate(PlateActionCode.HALVE_SOUP);

        assertThat(response.beforeScore()).isEqualTo(60);              // 저장값 70 이 아니다
        assertThat(response.afterScore()).isEqualTo(68);
        assertThat(response.afterScore() - response.beforeScore()).isPositive();
        assertThat(response.removedRules()).containsExactly("R04");
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
    @DisplayName("기록 삭제 — 내 것이면 지운다")
    void delete_removesOwnPlate() {
        SkinPlate plate = givenPlate();
        given(skinPlateRepository.findByIdAndUserId(PLATE_ID, USER_ID))
                .willReturn(Optional.of(plate));

        skinPlateService.delete(USER_ID, PLATE_ID);

        verify(skinPlateRepository).delete(plate);
    }

    @Test
    @DisplayName("남의 기록은 지워지지 않는다 — 403 이 아니라 404 다")
    void delete_otherUsersPlate_returns404() {
        given(skinPlateRepository.findByIdAndUserId(PLATE_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skinPlateService.delete(USER_ID, PLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PLATE_NOT_FOUND);

        verify(skinPlateRepository, never()).delete(any(SkinPlate.class));
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

    /**
     * 위 테스트와 분기가 다르다. 저쪽은 id 를 준 경우(없거나 남의 것)이고,
     * 이쪽은 id 를 생략해 최신 분석을 찾는 경로다 — 앱이 홈에서 음식만 찍고 들어올 때다.
     * 피부 분석을 한 번도 안 한 사용자가 여기로 들어오므로 20초 낭비가 실제로 일어나는 쪽이다.
     */
    @Test
    @DisplayName("피부 분석이 한 번도 없으면 — id 를 생략해도 AI 호출 전에 막는다")
    void analyze_withoutAnySkinAnalysis_failsBeforeCallingAi() {
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> skinPlateService.analyze(USER_ID, image(), null))
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

    // ---- 피부 기준 시점 (skinBasis) ----

    @Test
    @DisplayName("오늘 찍은 피부가 기준이면 skinBasis 가 TODAY 다")
    void analyze_todaySkin_basisIsToday() {
        SkinAnalysis analysis = givenSkinAnalysis();
        ReflectionTestUtils.setField(analysis, "createdAt",
                LocalDate.now(DateRange.KST).atTime(9, 0));
        givenAnalyzeStubs();

        PlateAnalysisResponse response = skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        assertThat(response.skinBasis()).isEqualTo(SkinBasis.TODAY);
        assertThat(response.skinMeasuredAt()).isEqualTo(LocalDate.now(DateRange.KST));
    }

    @Test
    @DisplayName("2주 전 피부가 기준이면 RECENT 와 측정일이 함께 내려간다 — '현재 피부'처럼 보이면 안 된다")
    void analyze_oldSkin_basisIsRecentWithMeasuredDate() {
        LocalDate measuredDate = LocalDate.now(DateRange.KST).minusDays(14);
        SkinAnalysis analysis = givenSkinAnalysis();
        ReflectionTestUtils.setField(analysis, "createdAt", measuredDate.atTime(9, 0));
        givenAnalyzeStubs();

        PlateAnalysisResponse response = skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID);

        assertThat(response.skinBasis()).isEqualTo(SkinBasis.RECENT);
        assertThat(response.skinMeasuredAt()).isEqualTo(measuredDate);
    }

    @Test
    @DisplayName("어제 자정 직전 측정도 RECENT 다 — 경계는 시간 간격이 아니라 KST 달력일이다")
    void analyze_yesterdayNightSkin_isRecent() {
        SkinAnalysis analysis = givenSkinAnalysis();
        ReflectionTestUtils.setField(analysis, "createdAt",
                LocalDate.now(DateRange.KST).minusDays(1).atTime(23, 59));
        givenAnalyzeStubs();

        assertThat(skinPlateService.analyze(USER_ID, image(), ANALYSIS_ID).skinBasis())
                .isEqualTo(SkinBasis.RECENT);
    }

    @Test
    @DisplayName("id 를 생략해 최신 분석으로 흐르는 경로에도 기준 시점이 붙는다")
    void analyze_withoutId_alsoCarriesBasis() {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        SkinAnalysis latest = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        ReflectionTestUtils.setField(latest, "id", ANALYSIS_ID);
        ReflectionTestUtils.setField(latest, "createdAt",
                LocalDate.now(DateRange.KST).minusDays(3).atTime(8, 0));
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.of(latest));
        givenAnalyzeStubs();

        PlateAnalysisResponse response = skinPlateService.analyze(USER_ID, image(), null);

        assertThat(response.skinBasis()).isEqualTo(SkinBasis.RECENT);
        assertThat(response.skinMeasuredAt())
                .isEqualTo(LocalDate.now(DateRange.KST).minusDays(3));
    }

    /**
     * 기준일이 "조회하는 오늘"이면 같은 기록이 열 때마다 라벨이 변한다 —
     * 과거 기록은 <b>기록 저장일</b> 대비로 판정해야 답이 고정된다.
     */
    @Test
    @DisplayName("저장된 기록의 기준 시점은 오늘이 아니라 기록 저장일 대비다")
    void get_basisIsRelativeToRecordDate() {
        LocalDate recordDate = LocalDate.of(2026, 8, 10);

        SkinPlate sameDay = givenPlate();
        ReflectionTestUtils.setField(sameDay.getSkinAnalysis(), "createdAt", recordDate.atTime(9, 0));
        ReflectionTestUtils.setField(sameDay, "createdAt", recordDate.atTime(12, 30));

        assertThat(skinPlateService.get(USER_ID, PLATE_ID).skinBasis()).isEqualTo(SkinBasis.TODAY);

        SkinPlate pastSkin = givenPlate();
        ReflectionTestUtils.setField(pastSkin.getSkinAnalysis(), "createdAt",
                recordDate.minusDays(2).atTime(9, 0));
        ReflectionTestUtils.setField(pastSkin, "createdAt", recordDate.atTime(12, 30));

        SkinPlateResponse response = skinPlateService.get(USER_ID, PLATE_ID);
        assertThat(response.skinBasis()).isEqualTo(SkinBasis.RECENT);
        assertThat(response.skinMeasuredAt()).isEqualTo(recordDate.minusDays(2));
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
                realMapper, transactionTemplate, analysisTokenProvider,
                visionClient);

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

    /**
     * "AI 가 죽어도 기록은 산다"가 이 경로의 계약이다. 그동안 mock 이 늘 EMPTY 를
     * 돌려줬던 탓에 catch 갈래는 한 번도 실검증된 적이 없었다.
     */
    @Test
    @DisplayName("AI 문장 생성이 터져도 저장은 끝난다 — 응답의 aiTip 만 비어 있다")
    void saveRecord_whenAiThrows_stillSavesWithoutComments() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-ai-down", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-ai-down")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-ai-down")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(visionClient.generateComments(any())).willThrow(new RuntimeException("AI down"));

        SkinPlateResponse response = skinPlateService.saveRecord(USER_ID, "token");

        assertThat(response.plateScore()).isEqualTo(60);
        assertThat(response.aiTip()).isNull();
        verify(skinPlateRepository).save(any());
    }

    /**
     * 배선 검증이 없으면 attachAiComments 호출 줄을 통째로 지워도 전 테스트가 통과한다 —
     * 나머지 테스트의 mock 이 전부 EMPTY 를 돌려주기 때문이다.
     */
    @Test
    @DisplayName("생성된 문장이 저장된 기록에 실린다 — attachAiComments 배선 검증")
    void saveRecord_attachesGeneratedComments() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-comment", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-comment")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-comment")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(visionClient.generateComments(any()))
                .willReturn(new PlateComments("팁 문장", "코멘트 문장"));

        SkinPlateResponse response = skinPlateService.saveRecord(USER_ID, "token");

        assertThat(response.aiTip()).isEqualTo("팁 문장");

        ArgumentCaptor<SkinPlate> plateCaptor = ArgumentCaptor.forClass(SkinPlate.class);
        verify(skinPlateRepository).save(plateCaptor.capture());
        assertThat(plateCaptor.getValue().getAiDailyComment()).isEqualTo("코멘트 문장");
    }

    /**
     * findInRange 는 최신순이다. 그대로 실으면 프롬프트의 [오늘의 기록 전체]가
     * 저녁→점심→아침으로 뒤집혀 나가고, "하루의 흐름을 한 문장으로" 요구받은 모델이
     * 흐름을 거꾸로 서술한다.
     */
    @Test
    @DisplayName("오늘의 기록은 시간순으로 프롬프트에 실린다 — 아침이 점심보다 앞이다")
    void saveRecord_putsTodaysRecordsInChronologicalOrder() {
        givenSkinAnalysis();
        givenUser();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-order", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-order")).willReturn(Optional.empty());
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(foodAnalysisService.toEntity(any(AppUser.class), eq(aiResult), eq("jti-order")))
                .willReturn(givenFood());
        given(foodAnalysisRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // 쿼리가 돌려주는 순서 그대로 — 점심(12:30)이 먼저, 아침(08:20)이 뒤다.
        LocalDate today = LocalDate.now(DateRange.KST);
        given(skinPlateRepository.findInRange(eq(USER_ID), any(), any())).willReturn(List.of(
                givenRecordedPlate(today.atTime(12, 30), "비빔밥"),
                givenRecordedPlate(today.atTime(8, 20), "그릭요거트")));

        skinPlateService.saveRecord(USER_ID, "token");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(visionClient).generateComments(promptCaptor.capture());
        String captured = promptCaptor.getValue();

        // 순서만 보면 "아침" 줄이 통째로 사라져도 -1 이 앞선다며 통과한다. 내용까지 붙여 고정한다.
        assertThat(captured).contains("아침 그릭요거트 78점\n점심 비빔밥 78점");
    }

    /**
     * 프롬프트의 "80자 이내"는 요청일 뿐 Structured Outputs 가 강제하지 않는다.
     * 길이 방어가 없으면 긴 문장 하나가 varchar(300) INSERT 를 깨서 기록까지 잃는다.
     */
    @Test
    @DisplayName("300자를 넘는 AI 문장은 잘려서 담긴다 — null 은 그대로 통과한다")
    void attachAiComments_clampsOverlongSentences() {
        SkinPlate plate = givenPlate();

        plate.attachAiComments("가".repeat(350), "나".repeat(350));

        assertThat(plate.getAiTip()).hasSize(300);
        assertThat(plate.getAiDailyComment()).hasSize(300);

        plate.attachAiComments(null, null);
        assertThat(plate.getAiTip()).isNull();
        assertThat(plate.getAiDailyComment()).isNull();
    }

    /**
     * 경계가 이모지 한가운데면 반쪽짜리 문자가 남고, Postgres 가 UTF-8 에서 거절한다 —
     * 막으려던 그 500 이 그대로 난다. 그래서 한 글자 덜 자른다.
     */
    @Test
    @DisplayName("경계가 이모지 한가운데면 한 글자 덜 자른다 — 반쪽 문자를 남기지 않는다")
    void attachAiComments_neverSplitsSurrogatePair() {
        SkinPlate plate = givenPlate();

        // 앞 299자 뒤에 🙂(2 char) — 300번째 char(index 299)가 high surrogate 다.
        String withEmojiOnBoundary = "가".repeat(299) + "🙂" + "나".repeat(10);
        assertThat(Character.isHighSurrogate(withEmojiOnBoundary.charAt(299))).isTrue();

        plate.attachAiComments(withEmojiOnBoundary, withEmojiOnBoundary);

        assertThat(plate.getAiTip()).hasSize(299);
        assertThat(Character.isHighSurrogate(
                plate.getAiTip().charAt(plate.getAiTip().length() - 1))).isFalse();
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

    /**
     * 멱등키의 존재 이유가 재시도인데, 재시도마다 25초짜리 유료 호출을 하고 버리면
     * 그 이유가 반쯤 사라진다. 저장 정확성은 트랜잭션 안의 재확인이 이미 맡고 있다.
     */
    @Test
    @DisplayName("멱등 — 같은 토큰의 재시도는 AI 문장 생성을 아예 건너뛴다")
    void saveRecord_idempotent_skipsAiCommentGeneration() {
        givenSkinAnalysis();
        SkinPlate existingPlate = givenPlate();
        OpenAiFoodResult aiResult = givenAiResult();
        AnalysisTokenPayload payload = new AnalysisTokenPayload(USER_ID, "jti-dup", ANALYSIS_ID, aiResult);
        given(analysisTokenProvider.parse("token", USER_ID)).willReturn(payload);
        given(foodAnalysisRepository.findIdByUserIdAndJti(USER_ID, "jti-dup")).willReturn(Optional.of(555L));
        given(skinPlateRepository.findByFoodAnalysisIdAndUserId(555L, USER_ID))
                .willReturn(Optional.of(existingPlate));
        // 문장 생성 경로가 끝까지 도달할 수 있게 해둔다 — 중간에서 예외로 삼켜지면
        // 호출을 건너뛴 것과 구분되지 않아 이 테스트가 엉뚱한 이유로 통과한다.
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());

        skinPlateService.saveRecord(USER_ID, "token");

        verify(visionClient, never()).generateComments(any());
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

    /**
     * detachedCopy 가 특성을 옮기는지는 점수로만 관찰된다 — HOT 이 복사에서 빠지면
     * before 가 60 으로, LESS_SPICY 가 spiciness 를 안 지우면 after 가 낮게 나온다.
     */
    @Test
    @DisplayName("simulateFromToken — HOT 매운맛은 R02 를 -16 으로 키우고, LESS_SPICY 가 통째로 되돌린다")
    void simulateFromToken_hotSpicinessDeepensR02() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);

        FoodAnalysis hotFood = givenFood();
        hotFood.assignTraits(FoodTraits.of(FoodGroup.SOUP_STEW, PortionSize.UNKNOWN,
                Spiciness.HOT, Oiliness.UNKNOWN, ProcessingLevel.UNKNOWN));
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(hotFood);

        PlateAnalysisSimulateResponse response = simulateFromToken(PlateActionCode.LESS_SPICY);

        // 70 +6(R05) +4(R09) -8(R04) -16(R02 홍조 64 × HOT 1.3) = 56 → R02 가 꺼져 72
        assertThat(response.beforeScore()).isEqualTo(56);
        assertThat(response.afterScore()).isEqualTo(72);
        assertThat(response.removedRules()).contains("R02");
    }

    @Test
    @DisplayName("simulate — LESS_RICE 는 열량을 3/4 로 줄여 R10 을 끈다 (65 → 70)")
    void simulateFromToken_lessRiceTurnsOffHighCalorie() {
        givenSkinAnalysis();
        OpenAiFoodResult aiResult = givenAiResult();
        givenAnalysisTokenPayload(aiResult);

        // 시연 지표(38/52/64/25/78)에서 R10 만 걸리는 음식 — 맵지 않고 싱겁고 단백질도 낮다.
        FoodAnalysis heavyFood = FoodAnalysis.create(null, "짜장면 곱빼기", "중식",
                Nutrition.of(1000, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1000, BigDecimal.ONE),
                CookingMethod.ETC, false, "{}");
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(heavyFood);

        PlateAnalysisSimulateResponse response = simulateFromToken(PlateActionCode.LESS_RICE);

        assertThat(response.beforeScore()).isEqualTo(65);   // 70 - 5(R10)
        assertThat(response.afterScore()).isEqualTo(70);    // 1000 × 0.75 = 750 → R10 해제
        assertThat(response.removedRules()).containsExactly("R10");
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

    /** analyze() 공통 스텁 — 인식·변환·토큰 발급. 피부 분석 스텁은 각 테스트가 직접 잡는다. */
    private void givenAnalyzeStubs() {
        OpenAiFoodResult aiResult = givenAiResult();
        given(foodAnalysisService.recognize(any())).willReturn(aiResult);
        given(foodAnalysisService.toEntity(null, aiResult)).willReturn(givenFood());
        given(analysisTokenProvider.issue(USER_ID, ANALYSIS_ID, aiResult)).willReturn("signed-token");
    }

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

    /**
     * "오늘의 기록" 한 줄이 될 이미 저장된 기록. 끼니 라벨은 createdAt 에서 파생하는데
     * BaseTimeEntity 가 채우는 값이라 생성자로는 줄 수 없어 리플렉션으로 심는다.
     */
    private SkinPlate givenRecordedPlate(LocalDateTime recordedAt, String foodName) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식",
                Nutrition.of(400, new BigDecimal("20.0"), new BigDecimal("10.0"),
                        new BigDecimal("50.0"), 600, new BigDecimal("5.0")),
                CookingMethod.BOILED, false, "{}");

        SkinPlate plate = SkinPlate.create(user, analysis, food, 78, "요약", "[]");
        ReflectionTestUtils.setField(plate, "createdAt", recordedAt);
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

    /** saveRecord() 의 save() 경로가 찾는 사용자. 저장하지 않는 경로에는 필요 없어 여기서만 쓴다. */
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
                        new BigDecimal("32.0"), sodiumMg, new BigDecimal("6.2")),
                "SOUP_STEW", "MEDIUM", "MEDIUM", "MEDIUM", "MINIMALLY_PROCESSED");
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
