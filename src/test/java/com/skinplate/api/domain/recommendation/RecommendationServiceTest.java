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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

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

    @BeforeEach
    void setUp() {
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        recommendationRepository = mock(RecommendationRepository.class);
        recommendationService = new RecommendationService(
                skinAnalysisRepository, recommendationRepository);

        given(recommendationRepository.existsBySkinAnalysisId(ANALYSIS_ID)).willReturn(false);
        given(recommendationRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("시연 지표 → 추천 7장이 전부 다른 문구를 단다 — 같은 문장이 줄줄이 뜨지 않는다")
    void demoMetrics_giveEveryFoodItsOwnReason() {
        givenAnalysis(SkinMetrics.of(38, 52, 64, 25, 78));

        RecommendationResponse response = recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        List<RecommendedFoodDto> recommend = savedOf(response).recommend();
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

        recommendationService.getOrCreate(USER_ID, ANALYSIS_ID);

        List<Recommendation> saved = captureSaved();
        // 술은 홍조·건조 양쪽 주의 후보라 한 번만, 커피는 건조 쪽에서 한 번.
        assertThat(saved).filteredOn(r -> r.getType().name().equals("AVOID"))
                .extracting(Recommendation::getFoodName)
                .containsExactly("매운 음식", "술", "커피");
    }

    // ---- 픽스처 ----

    private void givenAnalysis(SkinMetrics metrics) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        SkinAnalysis analysis = SkinAnalysis.create(user, metrics, 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(analysis));
        given(skinAnalysisRepository.findForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
    }

    @SuppressWarnings("unchecked")
    private List<Recommendation> captureSaved() {
        ArgumentCaptor<List<Recommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendationRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    /**
     * 저장 직후 조회는 대역이 빈 목록을 돌려주므로, 방금 저장한 것으로 응답을 다시 만든다.
     * 검증 대상은 "무엇을 만들었는가"이지 리포지토리 대역의 동작이 아니다.
     */
    private RecommendationResponse savedOf(RecommendationResponse ignored) {
        return RecommendationResponse.from(ANALYSIS_ID, captureSaved());
    }
}
