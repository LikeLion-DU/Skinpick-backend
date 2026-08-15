package com.skinplate.api.domain.recommendation;

import com.skinplate.api.domain.recommendation.dto.RecommendationResponse;
import com.skinplate.api.domain.recommendation.dto.RecommendedFoodDto;
import com.skinplate.api.domain.recommendation.entity.Recommendation;
import com.skinplate.api.domain.recommendation.repository.RecommendationRepository;
import com.skinplate.api.domain.recommendation.service.RecommendationService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S08 이 실제로 뭘 그리게 되는지를 본다. 심사위원이 직접 보는 화면이고 영상에도 나간다.
 */
class RecommendationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 100L;

    private SkinAnalysisRepository skinAnalysisRepository;
    private RecommendationRepository recommendationRepository;
    private RecommendationService recommendationService;
    private final List<Recommendation> saved = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        recommendationRepository = mock(RecommendationRepository.class);
        recommendationService = new RecommendationService(
                skinAnalysisRepository, recommendationRepository);

        given(recommendationRepository.existsBySkinAnalysisId(ANALYSIS_ID)).willReturn(false);
        // 저장한 것을 조회가 그대로 돌려주게 한다. 이렇게 해야 테스트가 "무엇을 만들었나"가
        // 아니라 getOrCreate 가 실제로 돌려주는 응답을 본다 — 정렬·타입 분리·generatedAt
        // 같은 읽기 경로의 회귀가 여기서 잡힌다.
        given(recommendationRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willAnswer(invocation -> {
                    saved.addAll(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });
        given(recommendationRepository.findBySkinAnalysisIdAndUserIdOrderByDisplayOrderAsc(
                ANALYSIS_ID, USER_ID)).willAnswer(invocation -> saved);
    }

    @Test
    @DisplayName("시연 지표 → 추천 7장이 전부 다른 문구를 단다 — 같은 문장이 줄줄이 뜨지 않는다")
    void demoMetrics_giveEveryFoodItsOwnReason() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        List<RecommendedFoodDto> recommend = response.recommend();
        assertThat(recommend).hasSize(7);
        assertThat(recommend).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토", "연어", "아보카도", "오이", "견과류");
        assertThat(recommend).extracting(RecommendedFoodDto::reason)
                .doesNotHaveDuplicates()
                .allSatisfy(reason -> assertThat(reason).isNotBlank());
    }

    @Test
    @DisplayName("피부가 멀쩡하면 아무것도 추천하지 않는다 — 없는 걱정을 만들어 팔지 않는다")
    void healthySkin_recommendsNothing() {
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).isEmpty();
        assertThat(response.avoid()).isEmpty();
        verify(skinAnalysisRepository, never()).findForUpdate(anyLong());
    }

    @Test
    @DisplayName("같은 이름이 추천과 주의 양쪽 후보에 있어도 한쪽만 사라지지 않는다")
    void dedupIsPerType() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        // 술은 홍조·건조 양쪽 주의 후보라 한 번만, 커피는 건조 쪽에서 한 번.
        assertThat(response.avoid()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("매운 음식", "술", "커피");
    }

    @Test
    @DisplayName("시연 seed 조합 — 측정 2 + 신고 1 + 습관 1 네 원천이 전부 화면에 나온다")
    void demoSeed_showsAllFourSources() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78), user -> {
            user.updateSkinConcerns(Set.of(SkinConcern.DARK_CIRCLE));
            user.changeSleepPattern(SleepPattern.LACKING);
        });

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토",          // 측정: 홍조
                                 "연어", "아보카도", "오이", "견과류",   // 측정: 건조
                                 "시금치", "달걀",                       // 신고: 다크서클
                                 "바나나", "우유");                      // 습관: 수면 부족
        assertThat(response.avoid()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("매운 음식", "술", "커피");
    }

    @Test
    @DisplayName("신고 고민이 측정과 전부 겹치면 신고 슬롯은 비워둔다 — 같은 축을 두 번 세지 않는다")
    void declaredOverlappingMeasured_leavesSlotEmpty() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78), user ->
                user.updateSkinConcerns(Set.of(SkinConcern.REDNESS, SkinConcern.DRYNESS)));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토", "연어", "아보카도", "오이", "견과류");
    }

    @Test
    @DisplayName("신고 고민이 일부만 겹치면 — 겹친 것을 건너뛰고 다음 신고가 슬롯을 쓴다")
    void declaredPartialOverlap_skipsToNextConcern() {
        // 측정: 홍조·건조. 신고 {민감/홍조, 다크서클} 중 홍조는 겹쳐 스킵, 다크서클이 신고 슬롯
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78), user ->
                user.updateSkinConcerns(Set.of(SkinConcern.REDNESS, SkinConcern.DARK_CIRCLE)));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("브로콜리", "녹차", "토마토", "연어", "아보카도", "오이", "견과류",
                                 "시금치", "달걀");
    }

    @Test
    @DisplayName("피부가 멀쩡해도 신고 고민·나쁜 습관이 있으면 그 근거로 추천이 생긴다")
    void healthySkinWithProfile_stillRecommends() {
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.updateSkinConcerns(Set.of(SkinConcern.ACNE));
            user.changeStressLevel(StressLevel.HIGH);
        });

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.recommend()).extracting(RecommendedFoodDto::foodName)
                .containsExactly("키위", "고구마", "견과류",   // 신고: 여드름 → TROUBLE
                                 "녹차", "연어");               // 습관: 스트레스 (견과류는 중복 제거)
    }

    @Test
    @DisplayName("습관이 좋은 값이거나 여러 개 나빠도 — 슬롯은 비우거나 수면>스트레스>운동 하나만")
    void habitSlot_goodValuesEmpty_priorityPicksOne() {
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.changeSleepPattern(SleepPattern.ENOUGH);
            user.changeStressLevel(StressLevel.LOW);
            user.changeExerciseHabit(ExerciseHabit.REGULAR);
        });
        assertThat(recommendationService.getOrCreate(USER_ID, ANALYSIS_ID).recommend()).isEmpty();

        saved.clear();
        givenAnalysis(SkinMetrics.of(95, 5, 5, 5, 95), user -> {
            user.changeSleepPattern(SleepPattern.LACKING);
            user.changeStressLevel(StressLevel.HIGH);
            user.changeExerciseHabit(ExerciseHabit.NONE);
        });

        assertThat(recommendationService.getOrCreate(USER_ID, ANALYSIS_ID).recommend())
                .extracting(RecommendedFoodDto::foodName)
                .containsExactly("바나나", "우유");   // SLEEP_LACK 만 — 스트레스·운동은 밀린다
    }

    // ---- 픽스처 ----

    private void givenAnalysis(SkinMetrics metrics) {
        givenAnalysis(metrics, user -> {});
    }

    private void givenAnalysis(SkinMetrics metrics, Consumer<AppUser> profile) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        profile.accept(user);

        SkinAnalysis analysis = SkinAnalysis.create(user, metrics, 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(analysis));
        given(skinAnalysisRepository.findForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
    }

}
