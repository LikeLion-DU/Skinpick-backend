package com.skinplate.api.domain.plate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.FoodIngredient;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.food.repository.FoodAnalysisRepository;
import com.skinplate.api.domain.food.service.FoodAnalysisService;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.engine.PlateRuleEngine;
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
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    private SkinAnalysisRepository skinAnalysisRepository;
    private SkinPlateRepository skinPlateRepository;
    private FoodAnalysisService foodAnalysisService;
    private SkinPlateService skinPlateService;

    @BeforeEach
    void setUp() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        FoodAnalysisRepository foodAnalysisRepository = mock(FoodAnalysisRepository.class);
        skinPlateRepository = mock(SkinPlateRepository.class);
        foodAnalysisService = mock(FoodAnalysisService.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));

        PlateRuleEngine engine = new PlateRuleEngine(List.of(
                new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
                new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
                new ProteinRule(), new VitaminRule(), new ProbioticRule()));

        skinPlateService = new SkinPlateService(
                userRepository, skinAnalysisRepository, foodAnalysisRepository,
                skinPlateRepository, foodAnalysisService, engine,
                new ObjectMapper(), transactionTemplate);
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
    @DisplayName("기준 피부 분석이 없으면 유료 호출 전에 막는다 — 20초 기다린 뒤 404 를 보지 않는다")
    void create_withoutSkinAnalysis_failsBeforeCallingAi() {
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.empty());

        MultipartFile image = new MockMultipartFile("image", "food.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        assertThatThrownBy(() -> skinPlateService.create(USER_ID, image, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);

        verify(foodAnalysisService, never()).recognize(any());
    }

    // ---- 픽스처 ----

    private PlateSimulateResponse simulate(PlateActionCode... actions) {
        return skinPlateService.simulate(USER_ID, PLATE_ID, List.of(actions));
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
}
