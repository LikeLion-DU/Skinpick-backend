package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.infra.openai.prompt.PlateCommentPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트의 경계를 고정한다 — 이 테스트가 깨지면 AI 가 보는 세계가 바뀐 것이다.
 */
class PlateCommentPromptTest {

    private static final SkinMetrics METRICS = SkinMetrics.of(38, 52, 64, 25, 78);

    private static String demoUser() {
        return PlateCommentPrompt.user(
                METRICS,
                "돼지고기 김치찌개",
                60,
                List.of(
                        SkinPlateFeedback.good("R05", "단백질 충분", 5, 1),
                        SkinPlateFeedback.caution("R04", "나트륨 과다", -8, 2)),
                List.of("아침 그릭요거트 78점"));
    }

    @Test
    @DisplayName("판단의 근거가 아니라 결과가 실린다 — 점수·평가·부호가 문장 재료다")
    void carriesVerdictsNotRawMaterial() {
        String user = demoUser();

        // 룰 엔진이 끝낸 판정이 그대로 실려야 한다.
        assertThat(user).contains("60점");
        assertThat(user).contains("좋은 점: 단백질 충분");
        assertThat(user).contains("주의할 점: 나트륨 과다");
        assertThat(user).contains("아침 그릭요거트 78점");
    }

    @Test
    @DisplayName("지표에는 방향 설명이 붙는다 — 안 붙이면 유분 80 을 좋다고 읽는 날이 온다")
    void metricsCarryDirection() {
        String user = demoUser();

        assertThat(user).contains("수분 38 (높을수록 좋음)");
        assertThat(user).contains("유분 52 (높을수록 과다)");
        assertThat(user).contains("붉어짐 64 (높을수록 주의)");
    }

    @Test
    @DisplayName("첫 기록이면 그렇게 말한다 — 빈 목록을 침묵으로 두지 않는다")
    void firstRecordIsExplicit() {
        String user = PlateCommentPrompt.user(METRICS, "라면", 55, List.of(), List.of());

        assertThat(user).contains("오늘의 첫 기록입니다");
    }

    @Test
    @DisplayName("시스템 프롬프트가 판단 금지를 명시한다")
    void systemForbidsJudgement() {
        assertThat(PlateCommentPrompt.SYSTEM).contains("점수·등급·판정을 새로 만들지 않는다");
        assertThat(PlateCommentPrompt.SYSTEM).contains("지어내지 않는다");
    }
}
