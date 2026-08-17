package com.skinplate.api.infra.openai;

import com.skinplate.api.domain.plate.entity.PlateActionCode;
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

    // ---- ACTION 분류 (2026-08-17 버그 수정) ----
    //
    // ACTION 은 scoreDelta 가 0 이라, 부호로 영역을 가르면 좋은 점 쪽에 붙는다.
    // "좋은 점: 국물을 절반만 남기면 점수가 상승합니다" 가 AI 입력이 되는 셈이고,
    // 그걸 근거로 쓴 문장은 고칠 것을 칭찬으로 바꿔 말한다.

    /** 룰 엔진이 실제로 만드는 모양 — GOOD·CAUTION·ACTION 이 한 목록에 섞여 온다. */
    private static List<SkinPlateFeedback> mixedFeedbacks() {
        return List.of(
                SkinPlateFeedback.good("R05", "단백질 충분", 6, 0),
                SkinPlateFeedback.caution("R04", "나트륨 과다", -8, 1),
                SkinPlateFeedback.action("R04", "국물을 절반만 남기면 Skin Plate 점수가 상승합니다.", 8, 2),
                SkinPlateFeedback.action("R10", "밥이나 면 양을 조금 줄여보세요.", 5, 3));
    }

    private static String userWith(List<SkinPlateFeedback> feedbacks) {
        return PlateCommentPrompt.user(METRICS, "돼지고기 김치찌개", 60, feedbacks, List.of());
    }

    @Test
    @DisplayName("ACTION 은 좋은 점으로 가지 않는다 — scoreDelta 0 이 칭찬으로 새던 자리다")
    void actionNeverLandsInGoodSection() {
        String user = userWith(mixedFeedbacks());

        assertThat(user).doesNotContain("좋은 점: 국물을 절반만");
        assertThat(user).doesNotContain("좋은 점: 밥이나 면 양을");
        // 좋은 점 줄은 GOOD 하나뿐이어야 한다.
        assertThat(user.lines().filter(line -> line.startsWith("좋은 점: ")))
                .containsExactly("좋은 점: 단백질 충분");
    }

    @Test
    @DisplayName("ACTION 은 제 영역에 개선 행동으로 실린다 — LESS_RICE(R10) 포함")
    void actionLandsInItsOwnSection() {
        String user = userWith(mixedFeedbacks());

        assertThat(user).contains("이렇게 바꿔보세요: 국물을 절반만 남기면 Skin Plate 점수가 상승합니다.");
        assertThat(user).contains("이렇게 바꿔보세요: 밥이나 면 양을 조금 줄여보세요.");
    }

    @Test
    @DisplayName("모든 ACTION 코드가 같은 경로를 탄다 — 타입으로 가르므로 코드가 무엇이든 같다")
    void everyActionCodeIsClassifiedByType() {
        for (PlateActionCode action : PlateActionCode.values()) {
            String user = userWith(List.of(
                    SkinPlateFeedback.action(action.relatedRuleCode(), action.getLabel(), 5, 0)));

            assertThat(user).as("%s 는 개선 행동으로 실린다", action)
                    .contains("이렇게 바꿔보세요: " + action.getLabel());
            assertThat(user).as("%s 가 칭찬으로 새지 않는다", action)
                    .doesNotContain("좋은 점: " + action.getLabel());
        }
    }

    @Test
    @DisplayName("회귀 — GOOD·CAUTION 분류는 그대로고 영역끼리 섞이지 않는다")
    void goodAndCautionClassificationIsUnchanged() {
        String user = userWith(mixedFeedbacks());

        assertThat(user).contains("좋은 점: 단백질 충분");
        assertThat(user).contains("주의할 점: 나트륨 과다");
        assertThat(user).doesNotContain("주의할 점: 단백질 충분");
        assertThat(user).doesNotContain("좋은 점: 나트륨 과다");

        // 영역 순서도 고정한다 — 좋은 점 → 주의할 점 → 개선 행동.
        assertThat(user.indexOf("좋은 점: ")).isLessThan(user.indexOf("주의할 점: "));
        assertThat(user.indexOf("주의할 점: ")).isLessThan(user.indexOf("이렇게 바꿔보세요: "));
    }

    @Test
    @DisplayName("해당 타입이 없으면 라벨도 안 나온다 — 빈 자리를 모델이 채우게 두지 않는다")
    void emptySectionIsOmitted() {
        String user = userWith(List.of(SkinPlateFeedback.good("R05", "단백질 충분", 6, 0)));

        assertThat(user).contains("좋은 점: 단백질 충분");
        assertThat(user).doesNotContain("주의할 점:");
        assertThat(user).doesNotContain("이렇게 바꿔보세요:");
    }

    @Test
    @DisplayName("시스템 프롬프트가 개선 행동을 잘한 점으로 옮기지 말라고 못 박는다")
    void systemForbidsPromotingActionsToPraise() {
        assertThat(PlateCommentPrompt.SYSTEM).contains("잘한 점으로 옮겨 적지");
        assertThat(PlateCommentPrompt.SYSTEM).contains("새로운 행동을 지어내지도 않는다");
    }
}
