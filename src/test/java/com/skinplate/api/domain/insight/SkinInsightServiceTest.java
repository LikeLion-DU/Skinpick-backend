package com.skinplate.api.domain.insight;

import com.skinplate.api.domain.insight.dto.SkinInsightResponse;
import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.entity.SkinInsight;
import com.skinplate.api.domain.insight.entity.SkinInsightItem;
import com.skinplate.api.domain.insight.repository.SkinInsightRepository;
import com.skinplate.api.domain.insight.service.SkinInsightService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import com.skinplate.api.infra.openai.exception.OpenAiClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이 서비스에서 위험한 지점은 둘이다.
 *
 * 하나는 <b>유료 호출을 언제 하느냐</b> — 이미 만들어진 인사이트나 할 말이 없는 사용자에게
 * Vision 모델을 부르면 조회할 때마다 과금이 붙는다.
 *
 * 다른 하나는 <b>실패를 저장하지 않는 것</b>이다. 인사이트는 분석당 1회 고정이라,
 * 반쪽짜리 결과를 한 번 저장하면 그 분석은 영원히 그 화면을 갖는다.
 */
class SkinInsightServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ANALYSIS_ID = 100L;

    /** 시연 지표 — 붉어짐(64) · 건조(수분 38) 두 주제가 뽑힌다. */
    private static final SkinMetrics DEMO = SkinMetrics.of(38, 52, 64, 25, 78);
    private static final SkinMetrics HEALTHY = SkinMetrics.of(95, 5, 5, 5, 95);

    private SkinAnalysisRepository skinAnalysisRepository;
    private SkinInsightRepository skinInsightRepository;
    private VisionClient visionClient;
    private SkinInsightService skinInsightService;

    @BeforeEach
    void setUp() {
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        skinInsightRepository = mock(SkinInsightRepository.class);
        visionClient = mock(VisionClient.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));

        given(skinInsightRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        skinInsightService = new SkinInsightService(
                skinAnalysisRepository, skinInsightRepository, visionClient, transactionTemplate);
    }

    @Test
    @DisplayName("시연 조합 — 측정 2 + 습관 1 이 우선순위 순서 그대로 카드가 된다")
    void demoCombination_buildsThreePrioritisedCards() {
        SkinAnalysis analysis = givenAnalysis(DEMO, user -> user.changeSleepPattern(SleepPattern.LACKING));
        givenSentences(InsightCategory.REDNESS, InsightCategory.DRY, InsightCategory.SLEEP);

        SkinInsightResponse response = skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.skinAnalysisId()).isEqualTo(analysis.getId());
        assertThat(response.insights()).extracting(SkinInsightResponse.Insight::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY, InsightCategory.SLEEP);
        assertThat(response.insights()).extracting(SkinInsightResponse.Insight::priority)
                .containsExactly("HIGH", "MEDIUM", "LOW");

        // 오늘의 행동은 카테고리에 고정된 문구다 — AI 가 만들지 않는다
        assertThat(response.todayActions()).extracting(SkinInsightResponse.TodayAction::title)
                .containsExactly(InsightCategory.REDNESS.getActionTitle(),
                                 InsightCategory.DRY.getActionTitle(),
                                 InsightCategory.SLEEP.getActionTitle());
    }

    @Test
    @DisplayName("이미 만들어진 인사이트가 있으면 AI 를 부르지 않는다 — 조회마다 과금되지 않는다")
    void existingInsight_skipsAiCall() {
        SkinAnalysis analysis = givenAnalysis(DEMO, user -> {});
        SkinInsight existing = SkinInsight.create(analysis.getUser(), analysis, "이미 만든 요약",
                SkinInsight.ProfileSnapshot.of(analysis.getUser()),
                List.of(SkinInsightItem.of(InsightCategory.DRY, "이미 만든 문장", 0)));
        given(skinInsightRepository.findBySkinAnalysisIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(existing));

        SkinInsightResponse response = skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.summary()).isEqualTo("이미 만든 요약");
        verify(visionClient, never()).generateSkinInsight(anyString());
        verify(skinInsightRepository, never()).save(any());
    }

    @Test
    @DisplayName("다룰 주제가 없으면 AI 도 저장도 없다 — 빈 인사이트를 저장하면 1회 고정이 족쇄가 된다")
    void noTopic_returnsWithoutAiAndWithoutSaving() {
        SkinAnalysis analysis = givenAnalysis(HEALTHY, user -> {});

        SkinInsightResponse response = skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.skinAnalysisId()).isEqualTo(analysis.getId());
        assertThat(response.insights()).isEmpty();
        assertThat(response.todayActions()).isEmpty();
        assertThat(response.summary()).isNotBlank();
        assertThat(response.generatedAt()).isNotNull();   // 저장 시각이 없으니 조회 시각이다

        verify(visionClient, never()).generateSkinInsight(anyString());
        verify(skinInsightRepository, never()).save(any());
        verify(skinAnalysisRepository, never()).findForUpdate(anyLong());
    }

    @Test
    @DisplayName("주제 없이 한 번 열어봤어도, 프로필을 채우고 다시 열면 그때 만들어진다")
    void noTopicThenProfileFilled_createsInsightOnNextCall() {
        AppUser user = givenAnalysis(HEALTHY, profile -> {}).getUser();

        assertThat(skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID).insights()).isEmpty();

        // 사용자가 이제야 습관을 입력했다. 앞선 조회가 빈 인사이트를 저장해 뒀다면
        // 이 시점부터 무엇을 입력해도 화면이 영원히 그대로다.
        user.changeSleepPattern(SleepPattern.LACKING);
        givenSentences(InsightCategory.SLEEP);

        SkinInsightResponse response = skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.insights()).extracting(SkinInsightResponse.Insight::category)
                .containsExactly(InsightCategory.SLEEP);
        verify(skinInsightRepository).save(any());
    }

    @Test
    @DisplayName("AI 가 실패하면 저장하지 않고 그대로 던진다 — 폴백을 저장하면 영원히 폴백이다")
    void aiFailure_savesNothingAndPropagates() {
        givenAnalysis(DEMO, user -> {});
        given(visionClient.generateSkinInsight(anyString()))
                .willThrow(new OpenAiClientException(ErrorCode.AI_TIMEOUT, null));

        assertThatThrownBy(() -> skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID))
                .isInstanceOf(OpenAiClientException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);

        verify(skinInsightRepository, never()).save(any());
        verify(skinAnalysisRepository, never()).findForUpdate(anyLong());
    }

    @Test
    @DisplayName("요청한 주제 중 하나라도 문장이 빠지면 실패다 — 설명 없는 카드를 영구히 남기지 않는다")
    void missingTopicSentence_failsWithoutSaving() {
        givenAnalysis(DEMO, user -> {});
        givenSentences(InsightCategory.REDNESS);          // DRY 문장이 없다

        assertThatThrownBy(() -> skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);

        verify(skinInsightRepository, never()).save(any());
        verify(skinAnalysisRepository, never()).findForUpdate(anyLong());
    }

    @Test
    @DisplayName("요청하지 않은 주제가 섞여 와도 무시한다 — Mock 은 13종을 전부 보낸다")
    void extraTopics_areIgnored() {
        givenAnalysis(DEMO, user -> {});
        givenSentences(InsightCategory.values());

        SkinInsightResponse response = skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        assertThat(response.insights()).extracting(SkinInsightResponse.Insight::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY);
    }

    @Test
    @DisplayName("타인의 분석 id 는 404 다 — 존재 여부 자체를 알려주지 않는다")
    void otherUsersAnalysis_is404() {
        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("AI 를 기다리는 사이 프로필이 바뀌어도 스냅샷은 선정 시점 값이다")
    void snapshotIsTakenWhenTopicsAreChosen_notWhenSaved() {
        AppUser user = givenAnalysis(DEMO, profile -> profile.changeSleepPattern(SleepPattern.LACKING))
                .getUser();

        // AI 호출은 최대 25초다. 그 사이에 사용자가 PATCH /auth/me 로 수면을 바꾼다.
        // 저장 시점의 user 에서 스냅샷을 뽑으면 "수면이 부족하다"는 문장 옆에 ENOUGH 가 남는다.
        given(visionClient.generateSkinInsight(anyString())).willAnswer(invocation -> {
            user.changeSleepPattern(SleepPattern.ENOUGH);
            user.updateSkinConcerns(Set.of(SkinConcern.PUFFINESS));
            return sentencesOf(InsightCategory.REDNESS, InsightCategory.DRY, InsightCategory.SLEEP);
        });

        skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID);

        ArgumentCaptor<SkinInsight> saved = ArgumentCaptor.forClass(SkinInsight.class);
        verify(skinInsightRepository).save(saved.capture());

        assertThat(saved.getValue().getSnapshotSleepPattern()).isEqualTo(SleepPattern.LACKING);
        assertThat(saved.getValue().getSnapshotConcerns()).isEmpty();
    }

    @Test
    @DisplayName("첫 분석이면 changes 가 없다 — 0 으로 채우면 '변화 없음'과 구분이 사라진다")
    void firstAnalysis_hasNoChanges() {
        givenAnalysis(HEALTHY, user -> {});

        assertThat(skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID).changes()).isNull();
    }

    @Test
    @DisplayName("직전 분석이 있으면 변화량을 부호 그대로 계산한다")
    void previousAnalysis_yieldsSignedChanges() {
        SkinAnalysis analysis = givenAnalysis(HEALTHY, user -> {});
        SkinAnalysis previous = SkinAnalysis.create(
                analysis.getUser(), SkinMetrics.of(86, 15, 12, 9, 80), 70, "직전", "{}");
        ReflectionTestUtils.setField(previous, "id", ANALYSIS_ID - 1);
        given(skinAnalysisRepository.findTopByUserIdAndIdLessThanOrderByIdDesc(USER_ID, ANALYSIS_ID))
                .willReturn(Optional.of(previous));

        SkinInsightResponse.Changes changes =
                skinInsightService.getOrCreate(USER_ID, ANALYSIS_ID).changes();

        assertThat(changes.hydration()).isEqualTo(9);      // 95 - 86
        assertThat(changes.oil()).isEqualTo(-10);          // 5 - 15
        assertThat(changes.skinScore()).isEqualTo(-15);    // 55 - 70
    }

    // ---- 픽스처 ----

    private SkinAnalysis givenAnalysis(SkinMetrics metrics, Consumer<AppUser> profile) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        profile.accept(user);

        SkinAnalysis analysis = SkinAnalysis.create(user, metrics, 55, "요약", "{}");
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);

        given(skinAnalysisRepository.findByIdAndUserId(ANALYSIS_ID, USER_ID))
                .willReturn(Optional.of(analysis));
        given(skinAnalysisRepository.findForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
        return analysis;
    }

    private void givenSentences(InsightCategory... categories) {
        given(visionClient.generateSkinInsight(anyString())).willReturn(sentencesOf(categories));
    }

    /** 주어진 카테고리에만 문장이 있는 AI 응답. 나머지는 응답에 아예 없다. */
    private static SkinInsightSentences sentencesOf(InsightCategory... categories) {
        List<SkinInsightSentences.Topic> topics = java.util.Arrays.stream(categories)
                .map(category -> new SkinInsightSentences.Topic(
                        category.name(), category.getTitle() + " 설명 문장이에요."))
                .toList();

        return new SkinInsightSentences("오늘의 요약이에요.", topics);
    }
}
