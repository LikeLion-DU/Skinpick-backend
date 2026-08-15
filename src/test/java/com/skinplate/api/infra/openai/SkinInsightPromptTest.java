package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.service.InsightTopics.Topic;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.WaterIntake;
import com.skinplate.api.infra.openai.prompt.SkinInsightPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트의 경계를 고정한다 — 이 테스트가 깨지면 AI 가 보는 세계가 바뀐 것이다.
 */
class SkinInsightPromptTest {

    private static final List<Topic> TOPICS = List.of(
            new Topic(InsightCategory.REDNESS, "측정 지표가 취약 판정을 받음"),
            new Topic(InsightCategory.SLEEP, "자가 신고 생활 습관 — 수면: 부족해요"));

    @Test
    @DisplayName("지표에는 방향 설명이 붙는다 — 안 붙이면 유분 80 을 좋다고 읽는 날이 온다")
    void metricsCarryDirection() {
        String user = SkinInsightPrompt.user(analysis(38, 52, 64, 25, 78, 55), null, sleepless(), TOPICS);

        assertThat(user).contains("수분 38 (높을수록 좋음)");
        assertThat(user).contains("유분 52 (높을수록 과다)");
        assertThat(user).contains("붉어짐 64 (높을수록 주의)");
        assertThat(user).contains("종합 점수 55 (높을수록 좋음)");
    }

    @Test
    @DisplayName("다룰 주제는 순서 그대로 실리고 선정 근거가 붙는다 — AI 가 주제를 다시 고르지 않게 한다")
    void topicsCarryOrderAndReason() {
        String user = SkinInsightPrompt.user(analysis(38, 52, 64, 25, 78, 55), null, sleepless(), TOPICS);

        assertThat(user).contains("REDNESS (진정 관리) — 측정 지표가 취약 판정을 받음");
        assertThat(user).contains("SLEEP (수면 관리) — 자가 신고 생활 습관 — 수면: 부족해요");
        assertThat(user.indexOf("REDNESS")).isLessThan(user.indexOf("SLEEP"));
    }

    @Test
    @DisplayName("직전 분석이 없으면 변화량 블록 자체가 빠진다 — 첫 분석을 '그대로'라고 읽게 두지 않는다")
    void withoutPrevious_changeBlockIsAbsent() {
        String user = SkinInsightPrompt.user(analysis(38, 52, 64, 25, 78, 55), null, sleepless(), TOPICS);

        assertThat(user).doesNotContain("[직전 분석 대비 변화]");
    }

    @Test
    @DisplayName("직전 분석이 있으면 부호를 붙여 싣고, 측정 환경 차이를 함께 알린다")
    void withPrevious_changesAreSignedAndHedged() {
        String user = SkinInsightPrompt.user(
                analysis(38, 52, 64, 25, 78, 55),
                analysis(29, 52, 70, 25, 78, 60),
                sleepless(), TOPICS);

        assertThat(user).contains("수분 +9");
        assertThat(user).contains("붉어짐 -6");
        assertThat(user).contains("유분 +0");
        assertThat(user).contains("종합 점수 -5");
        assertThat(user).contains("측정 환경 차이가 있을 수 있음");
    }

    @Test
    @DisplayName("입력한 습관만 줄로 나간다 — 건너뛴 질문을 화면에서 다시 지적하지 않는다")
    void onlyAnsweredHabitsAppear() {
        String user = SkinInsightPrompt.user(analysis(38, 52, 64, 25, 78, 55), null, sleepless(), TOPICS);

        assertThat(user).contains("수면: 부족해요");
        assertThat(user).contains("수분 섭취: 부족해요");
        assertThat(user).doesNotContain("스트레스:");
        assertThat(user).doesNotContain("운동:");
    }

    @Test
    @DisplayName("개인 식별 정보는 프롬프트에 들어가지 않는다 — 외부 API 로 나가는 값이다")
    void carriesNoPersonalIdentity() {
        String user = SkinInsightPrompt.user(analysis(38, 52, 64, 25, 78, 55), null, sleepless(), TOPICS);

        assertThat(user).doesNotContain("test@skinplate.app");
        assertThat(user).doesNotContain("테스트유저");
    }

    @Test
    @DisplayName("시스템 프롬프트가 판단 금지와 인과 확정 금지를 함께 명시한다")
    void systemForbidsJudgementAndCausation() {
        assertThat(SkinInsightPrompt.SYSTEM).contains("점수·판정·우선순위를 새로 만들지 않는다");
        assertThat(SkinInsightPrompt.SYSTEM).contains("지어내지 않는다");
        assertThat(SkinInsightPrompt.SYSTEM).contains("인과를 확정하지 않는다");
        assertThat(SkinInsightPrompt.SYSTEM).contains("의학적 진단");
    }

    /**
     * 실제 OpenAI 호출로 드러난 드리프트다 — 규칙 3·6 이 전역으로만 적혀 있으니
     * 모델이 요약만 다른 문체로 다뤘다("영향을 주고 있어요" · "~입니다").
     * 요약에 다시 걸어주는 이 두 줄이 지워져도 나머지 테스트는 초록으로 남는다.
     */
    @Test
    @DisplayName("요약 규칙이 인과·문체 규칙을 다시 걸어준다 — 실호출에서 요약만 어긋났다")
    void systemRebindsToneRulesToSummary() {
        assertThat(SkinInsightPrompt.SYSTEM).contains("\"영향을 주고 있어요\"도 단정이다");
        assertThat(SkinInsightPrompt.SYSTEM).contains("summary 에도 3번과 6번이");
    }

    @Test
    @DisplayName("스키마의 category enum 은 13종 전부를 담는다 — 빠진 주제는 AI 가 답할 수 없다")
    void schemaEnumCoversEveryCategory() {
        assertThat(SkinInsightPrompt.SCHEMA.toString())
                .contains(java.util.Arrays.stream(InsightCategory.values()).map(Enum::name).toList()
                        .toArray(new String[0]));
    }

    // ---- 픽스처 ----

    private static SkinAnalysis analysis(int hydration, int oil, int redness,
                                         int trouble, int barrier, int skinScore) {
        return SkinAnalysis.create(null, SkinMetrics.of(hydration, oil, redness, trouble, barrier),
                skinScore, "요약", "{}");
    }

    /** 수면·수분만 답한 프로필. 스트레스·운동은 미입력이다. */
    private static AppUser sleepless() {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        user.changeSleepPattern(SleepPattern.LACKING);
        user.changeWaterIntake(WaterIntake.LACKING);
        return user;
    }
}
