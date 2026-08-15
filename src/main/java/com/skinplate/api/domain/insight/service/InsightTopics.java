package com.skinplate.api.domain.insight.service;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.recommendation.service.RecommendationCandidates;
import com.skinplate.api.domain.recommendation.service.RecommendationCandidates.Concern;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import com.skinplate.api.domain.user.entity.WaterIntake;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인사이트 주제 선정. (PRD §18.10)
 *
 * <b>여기서 결정이 전부 끝난다.</b> 무엇을 다룰지, 어느 것이 더 급한지를 규칙이 정하고
 * AI 는 그 뒤에 문장만 붙인다 — LLM 이 주제를 고르면 같은 사진에서 매일 다른 인사이트가
 * 나오고, "왜 이 이야기가 나왔나"를 아무도 설명할 수 없게 된다. (§18.9 와 같은 분리)
 *
 * 임계값을 여기에 다시 적지 않는다. 측정 판정은 {@link RecommendationCandidates#topConcerns}
 * 를 그대로 부른다 — 같은 뜻의 숫자가 두 곳에 생기면 추천은 "건조하다"고 보고 인사이트만
 * "괜찮다"고 보는 날이 온다.
 */
public final class InsightTopics {

    private InsightTopics() {}

    /** 화면이 감당하는 카드 수. 늘리면 우선순위(HIGH/MEDIUM/LOW)의 의미가 흐려진다. */
    public static final int MAX_TOPICS = 3;

    /** 측정 슬롯 상한. 추천(§18.9)과 같은 2 다. */
    private static final int MEASURED_SLOT = 2;

    /** @param reason 왜 이 주제가 뽑혔는지. 프롬프트에 그대로 실린다 — 화면에는 안 나간다. */
    public record Topic(InsightCategory category, String reason) {}

    /**
     * 슬롯: 측정 최대 2 + 습관 최대 1 + 자가 신고 최대 1, 합쳐서 최대 3.
     *
     * <b>순서가 곧 우선순위다</b>(0=HIGH · 1=MEDIUM · 2=LOW). 습관을 신고보다 앞에 두는
     * 이유는 생활 연계가 이 기능의 본체이기 때문이다 — 신고 고민은 추천 화면이 이미 다룬다.
     *
     * 네 슬롯이 다 차면 마지막 하나(신고)가 잘린다. 상한이 4가 아니라 3인 것이 그 선택이다.
     */
    public static List<Topic> select(SkinMetrics metrics, AppUser user) {
        List<Topic> topics = new ArrayList<>();

        for (Concern concern : RecommendationCandidates.topConcerns(metrics, MEASURED_SLOT)) {
            topics.add(new Topic(map(concern), "측정 지표가 취약 판정을 받음"));
        }

        habitTopic(user).ifPresent(topics::add);
        declaredTopic(user, topics).ifPresent(topics::add);

        return topics.stream().limit(MAX_TOPICS).toList();
    }

    /**
     * 나쁜 값만 트리거. 우선순위는 수면 > 스트레스 > 운동 > 수분 — 하나만 뽑는다.
     * (RecommendationService.habitConcern 과 같은 순서에 수분이 붙었다. 그쪽은 건드리지 않는다 —
     *  추천 축에는 수분 후보 음식 표가 없어서 여기서만 늘린다.)
     */
    private static Optional<Topic> habitTopic(AppUser user) {
        if (user.getSleepPattern() == SleepPattern.LACKING) {
            return Optional.of(habit(InsightCategory.SLEEP, "수면", user.getSleepPattern().getLabel()));
        }
        if (user.getStressLevel() == StressLevel.HIGH) {
            return Optional.of(habit(InsightCategory.STRESS, "스트레스", user.getStressLevel().getLabel()));
        }
        if (user.getExerciseHabit() == ExerciseHabit.NONE) {
            return Optional.of(habit(InsightCategory.EXERCISE, "운동", user.getExerciseHabit().getLabel()));
        }
        if (user.getWaterIntake() == WaterIntake.LACKING) {
            return Optional.of(habit(InsightCategory.WATER, "수분 섭취", user.getWaterIntake().getLabel()));
        }
        return Optional.empty();
    }

    private static Topic habit(InsightCategory category, String name, String label) {
        return new Topic(category, "자가 신고 생활 습관 — " + name + ": " + label);
    }

    /** 이미 뽑힌 축이면 건너뛰고 다음 고민을 본다 — 같은 이야기를 두 카드로 하지 않는다. */
    private static Optional<Topic> declaredTopic(AppUser user, List<Topic> chosen) {
        Set<InsightCategory> taken = chosen.stream()
                .map(Topic::category)
                .collect(Collectors.toSet());

        return user.getSkinConcerns().stream()
                .sorted()                                    // Set 이라 입력 순서가 없다 — 선언 순으로 고정
                .filter(concern -> !taken.contains(map(RecommendationCandidates.mapDeclared(concern))))
                .findFirst()
                .map(concern -> new Topic(map(RecommendationCandidates.mapDeclared(concern)),
                        "자가 신고 피부 고민 — " + concern.getLabel()));
    }

    /**
     * 추천 축 → 인사이트 주제.
     * switch 가 전사라서 Concern 에 값을 추가하고 여기를 빠뜨리면 컴파일이 깨진다.
     */
    private static InsightCategory map(Concern concern) {
        return switch (concern) {
            case DRY           -> InsightCategory.DRY;
            case OILY          -> InsightCategory.OILY;
            case REDNESS       -> InsightCategory.REDNESS;
            case TROUBLE       -> InsightCategory.TROUBLE;
            case BARRIER_WEAK  -> InsightCategory.BARRIER_WEAK;
            case DARK_CIRCLE   -> InsightCategory.DARK_CIRCLE;
            case PIGMENTATION  -> InsightCategory.PIGMENTATION;
            case ELASTICITY    -> InsightCategory.ELASTICITY;
            case PUFFINESS     -> InsightCategory.PUFFINESS;
            case SLEEP_LACK    -> InsightCategory.SLEEP;
            case STRESS_HIGH   -> InsightCategory.STRESS;
            case EXERCISE_NONE -> InsightCategory.EXERCISE;
        };
    }
}
