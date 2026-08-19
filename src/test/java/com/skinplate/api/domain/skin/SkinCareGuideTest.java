package com.skinplate.api.domain.skin;

import com.skinplate.api.domain.skin.dto.CareFocusDto;
import com.skinplate.api.domain.skin.entity.SkinCareFocus;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.skin.service.SkinCareGuide;
import com.skinplate.api.domain.skin.service.SkinTypeGapAnalyzer;
import com.skinplate.api.domain.user.entity.SkinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "지금 피부가 필요로 하는 관리"(careFocus · careMessage)와 자가 신고 전용 타입.
 *
 * <p>둘 다 <b>지표에서 규칙으로 도출</b>한다 — AI 를 부르지 않으므로 같은 지표면 언제
 * 조회해도 같은 값이고, 예전에 저장된 분석에도 나온다.
 */
class SkinCareGuideTest {

    private final SkinCareGuide guide = new SkinCareGuide();
    private final SkinTypeGapAnalyzer gapAnalyzer = new SkinTypeGapAnalyzer();

    // ─────────────────────────── ⑥ careFocus · careMessage ───────────────────────────

    @Test
    @DisplayName("건조하고 장벽이 약하면 수분·장벽과 건강한 지방이 함께 뜬다")
    void dryAndWeakBarrier() {
        // 수분 30(<40 건조) · 장벽 30(<40 장벽 약화) · 나머지는 정상
        SkinMetrics metrics = SkinMetrics.of(30, 40, 20, 20, 30);

        assertThat(guide.focus(metrics)).extracting(CareFocusDto::focus)
                .containsExactly(SkinCareFocus.HYDRATION, SkinCareFocus.HEALTHY_FAT);
        assertThat(guide.message(metrics))
                .contains("수분 유지에 도움이 되는 식습관")
                .contains("건강한 지방");
    }

    @Test
    @DisplayName("붉은기·트러블이 있으면 항산화와 식이섬유가 뜬다")
    void rednessAndTrouble() {
        // 붉은기 70(>60) · 트러블 70(>60) · 수분·장벽 정상
        SkinMetrics metrics = SkinMetrics.of(80, 40, 70, 70, 80);

        assertThat(guide.focus(metrics)).extracting(CareFocusDto::focus)
                .containsExactly(SkinCareFocus.ANTIOXIDANT, SkinCareFocus.FIBER);
    }

    @Test
    @DisplayName("지표가 모두 안정적이면 빈 배열이 아니라 '지금 균형 유지' 하나다")
    void allGoodFallsBackToBalance() {
        SkinMetrics metrics = SkinMetrics.of(80, 40, 20, 20, 80);

        assertThat(guide.focus(metrics)).extracting(CareFocusDto::focus)
                .containsExactly(SkinCareFocus.BALANCE);
        assertThat(guide.message(metrics)).contains("그대로 이어가");
    }

    @Test
    @DisplayName("균형 유지는 다른 축과 함께 오지 않는다 — 서로를 부정하는 두 칩이 된다")
    void balanceNeverMixes() {
        SkinMetrics dry = SkinMetrics.of(30, 40, 20, 20, 80);

        assertThat(guide.focus(dry)).extracting(CareFocusDto::focus)
                .doesNotContain(SkinCareFocus.BALANCE);
    }

    @Test
    @DisplayName("문단은 세 축까지만 싣지만 칩 배열은 자르지 않는다")
    void messageCapsButFocusDoesNot() {
        // 건조 + 장벽 약화 + 붉은기 + 트러블 + 유분 과다 → 다섯 축이 전부 걸린다
        SkinMetrics everything = SkinMetrics.of(30, 80, 70, 70, 30);

        assertThat(guide.focus(everything)).hasSize(5);
        // 네 번째 축(유분 관리)의 문장은 문단에 없다.
        assertThat(guide.message(everything)).doesNotContain("튀김과 기름진 조리");
    }

    @Test
    @DisplayName("라벨을 함께 실어 앱이 축 이름을 갖지 않게 한다")
    void carriesLabel() {
        assertThat(guide.focus(SkinMetrics.of(30, 40, 20, 20, 80)))
                .first()
                .extracting(CareFocusDto::label)
                .isEqualTo("수분·장벽");
    }

    // ─────────────────────────── ⑦ 수부지 (자가 신고 전용) ───────────────────────────

    @Test
    @DisplayName("수부지는 선택지에 있지만 관찰 타입으로는 나오지 않는다")
    void dehydratedOilyIsDeclaredOnly() {
        assertThat(SkinType.selectable()).contains(SkinType.DEHYDRATED_OILY);
        assertThat(SkinType.DEHYDRATED_OILY.isDeclaredOnly()).isTrue();

        // 어떤 지표를 넣어도 observe() 는 이 값을 내지 않는다 — 상태는 SkinTrait 의 몫이다.
        for (int hydration = 0; hydration <= 100; hydration += 10) {
            for (int oil = 0; oil <= 100; oil += 10) {
                SkinMetrics metrics = SkinMetrics.of(hydration, oil, 20, 20, 80);
                assertThat(SkinType.observe(metrics)).isNotEqualTo(SkinType.DEHYDRATED_OILY);
            }
        }
    }

    @Test
    @DisplayName("수부지 상태는 SkinTrait.DEHYDRATED 가 낸다 — 갭 문장과 같은 조건이어야 한다")
    void traitAndGapAgree() {
        // 수분 30(건조) + 유분 65(부분 상승) = 수부지 조건
        SkinMetrics dehydrated = SkinMetrics.of(30, 65, 20, 20, 80);

        assertThat(SkinTrait.observe(dehydrated, null, null))
                .contains(SkinTrait.DEHYDRATED);
        assertThat(gapAnalyzer.analyze(SkinType.DEHYDRATED_OILY, dehydrated).message())
                .contains("오늘도 수분이 부족한데 유분은 올라와 있습니다");
    }

    @Test
    @DisplayName("수부지를 골랐지만 오늘은 그 상태가 아니면 그 사실을 말한다")
    void gapWhenNotDehydratedToday() {
        SkinMetrics balanced = SkinMetrics.of(80, 40, 20, 20, 80);

        assertThat(gapAnalyzer.analyze(SkinType.DEHYDRATED_OILY, balanced).message())
                .contains("오늘은 수분과 유분이 그만큼 벌어져 있지 않습니다");
    }

    @Test
    @DisplayName("자가 신고 전용 타입은 matched 가 항상 false 다 — 비교할 같은 축이 없다")
    void declaredOnlyNeverMatches() {
        List<SkinType> declaredOnly = List.of(SkinType.SENSITIVE, SkinType.DEHYDRATED_OILY);
        SkinMetrics metrics = SkinMetrics.of(30, 65, 70, 20, 80);

        assertThat(declaredOnly)
                .allSatisfy(type ->
                        assertThat(gapAnalyzer.analyze(type, metrics).matched()).isFalse());
    }

    @Test
    @DisplayName("민감성 분기는 그대로다 — 수부지를 넣으면서 깨지지 않았다")
    void sensitiveStillWorks() {
        SkinMetrics red = SkinMetrics.of(80, 40, 70, 20, 80);

        assertThat(gapAnalyzer.analyze(SkinType.SENSITIVE, red).message())
                .startsWith("민감성이라고 하셨는데 오늘도 붉은기가 관찰됩니다");
    }

    @Test
    @DisplayName("조사가 라벨의 종성을 따라간다 — '수부지이라고' 가 되지 않는다")
    void particleFollowsLabel() {
        SkinMetrics metrics = SkinMetrics.of(30, 65, 20, 20, 80);

        assertThat(gapAnalyzer.analyze(SkinType.DEHYDRATED_OILY, metrics).message())
                .startsWith("수부지라고 하셨는데")
                .doesNotContain("수부지이라고");
    }

    @Test
    @DisplayName("폴백 문장도 같은 규칙을 쓴다 — 선택 타입과 관찰 타입이 다를 때")
    void fallbackParticle() {
        // 건성을 골랐는데 오늘은 지성으로 관찰된 경우 (SPECIAL 에 없는 조합은 폴백이다)
        SkinMetrics oily = SkinMetrics.of(80, 80, 20, 20, 80);

        assertThat(gapAnalyzer.analyze(SkinType.NORMAL, oily).message())
                .contains("보통이라고 생각하셨지만");
    }
}
