package com.skinplate.api.domain.insight;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.service.InsightTopics;
import com.skinplate.api.domain.insight.service.InsightTopics.Topic;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import com.skinplate.api.domain.user.entity.WaterIntake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주제 선정이 결정적이라는 것을 고정한다 — 같은 지표·같은 프로필이면 언제나 같은 목록,
 * 같은 순서다. 여기가 흔들리면 AI 문장이 아무리 좋아도 "왜 이 이야기가 나왔나"를
 * 설명할 수 없게 된다.
 *
 * 임계값은 SkinMetrics 가 갖고 있다 — DRY<40 · OILY>70 · REDNESS>60 · TROUBLE>60 · BARRIER<40.
 */
class InsightTopicsTest {

    /** 시연 지표. 붉어짐 64 · 수분 38(심각도 62) 두 개가 걸린다. */
    private static final SkinMetrics DEMO = SkinMetrics.of(38, 52, 64, 25, 78);
    private static final SkinMetrics HEALTHY = SkinMetrics.of(95, 5, 5, 5, 95);

    @Test
    @DisplayName("측정 슬롯은 실제로 취약한 것만 심각한 순 2개 — 시연 지표는 붉어짐 다음 건조")
    void measuredSlotTakesTopTwoBySeverity() {
        List<Topic> topics = InsightTopics.select(DEMO, user(profile -> {}));

        assertThat(topics).extracting(Topic::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY);
    }

    @Test
    @DisplayName("지표가 전부 양호하고 프로필도 비면 주제가 없다 — 없는 걱정을 만들지 않는다")
    void healthySkinWithoutProfile_hasNoTopic() {
        assertThat(InsightTopics.select(HEALTHY, user(profile -> {}))).isEmpty();
    }

    @Test
    @DisplayName("습관 슬롯은 나쁜 값 하나만 — 우선순위 수면 > 스트레스 > 운동 > 수분")
    void habitSlotPicksOneByPriority() {
        AppUser allBad = user(profile -> {
            profile.changeSleepPattern(SleepPattern.LACKING);
            profile.changeStressLevel(StressLevel.HIGH);
            profile.changeExerciseHabit(ExerciseHabit.NONE);
            profile.changeWaterIntake(WaterIntake.LACKING);
        });

        assertThat(InsightTopics.select(HEALTHY, allBad)).extracting(Topic::category)
                .containsExactly(InsightCategory.SLEEP);

        // 앞의 셋이 좋은 값이면 그제서야 수분이 슬롯을 쓴다
        AppUser waterOnly = user(profile -> {
            profile.changeSleepPattern(SleepPattern.ENOUGH);
            profile.changeStressLevel(StressLevel.LOW);
            profile.changeExerciseHabit(ExerciseHabit.REGULAR);
            profile.changeWaterIntake(WaterIntake.LACKING);
        });

        assertThat(InsightTopics.select(HEALTHY, waterOnly)).extracting(Topic::category)
                .containsExactly(InsightCategory.WATER);
    }

    @Test
    @DisplayName("좋은 값만 골라뒀으면 습관 슬롯은 비운다 — 잘하고 있는 것을 지적하지 않는다")
    void goodHabits_leaveSlotEmpty() {
        AppUser good = user(profile -> {
            profile.changeSleepPattern(SleepPattern.ENOUGH);
            profile.changeStressLevel(StressLevel.LOW);
            profile.changeExerciseHabit(ExerciseHabit.REGULAR);
            profile.changeWaterIntake(WaterIntake.ENOUGH);
        });

        assertThat(InsightTopics.select(HEALTHY, good)).isEmpty();
    }

    @Test
    @DisplayName("운동을 자주 하는 사람도 습관 슬롯을 쓰지 않는다 — REGULAR 위에 FREQUENT 가 있다")
    void frequentExercise_leavesSlotEmpty() {
        AppUser frequent = user(profile -> profile.changeExerciseHabit(ExerciseHabit.FREQUENT));

        assertThat(InsightTopics.select(HEALTHY, frequent)).isEmpty();
    }

    @Test
    @DisplayName("신고 고민이 측정과 겹치면 건너뛰고 다음 고민이 슬롯을 쓴다")
    void declaredOverlappingMeasured_skipsToNext() {
        // 측정이 이미 붉어짐을 잡았다 — REDNESS 신고는 스킵되고 다크서클이 들어온다
        AppUser overlapping = user(profile ->
                profile.updateSkinConcerns(Set.of(SkinConcern.REDNESS, SkinConcern.DARK_CIRCLE)));

        assertThat(InsightTopics.select(DEMO, overlapping)).extracting(Topic::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY, InsightCategory.DARK_CIRCLE);

        // 전부 겹치면 신고 슬롯은 비운다
        AppUser allOverlapping = user(profile ->
                profile.updateSkinConcerns(Set.of(SkinConcern.REDNESS, SkinConcern.DRYNESS)));

        assertThat(InsightTopics.select(DEMO, allOverlapping)).extracting(Topic::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY);
    }

    @Test
    @DisplayName("네 슬롯이 다 차면 3개로 자른다 — 순서는 측정 · 습관 · 신고")
    void fourCandidates_areCutToThreeInSlotOrder() {
        AppUser full = user(profile -> {
            profile.updateSkinConcerns(Set.of(SkinConcern.DARK_CIRCLE));
            profile.changeSleepPattern(SleepPattern.LACKING);
        });

        List<Topic> topics = InsightTopics.select(DEMO, full);

        // 신고(다크서클)가 잘린 자리다. 습관이 앞선다 — 생활 연계가 이 기능의 본체고,
        // 신고 고민은 추천 화면(#30)이 이미 다룬다.
        assertThat(topics).hasSize(InsightTopics.MAX_TOPICS);
        assertThat(topics).extracting(Topic::category)
                .containsExactly(InsightCategory.REDNESS, InsightCategory.DRY, InsightCategory.SLEEP);
    }

    @Test
    @DisplayName("지표가 양호해도 신고 고민이 있으면 그 근거로 주제가 생긴다")
    void healthySkinWithDeclaredConcern_stillHasTopic() {
        AppUser declared = user(profile -> profile.updateSkinConcerns(Set.of(SkinConcern.PUFFINESS)));

        assertThat(InsightTopics.select(HEALTHY, declared)).extracting(Topic::category)
                .containsExactly(InsightCategory.PUFFINESS);
    }

    @Test
    @DisplayName("선정 근거는 비어 있지 않다 — 프롬프트의 '다룰 주제' 줄이 그대로 이 문자열이다")
    void everyTopicCarriesItsReason() {
        AppUser full = user(profile -> {
            profile.updateSkinConcerns(Set.of(SkinConcern.DARK_CIRCLE));
            profile.changeSleepPattern(SleepPattern.LACKING);
        });

        assertThat(InsightTopics.select(DEMO, full))
                .allSatisfy(topic -> assertThat(topic.reason()).isNotBlank());
    }

    // ---- 픽스처 ----

    private static AppUser user(Consumer<AppUser> profile) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        profile.accept(user);
        return user;
    }
}
