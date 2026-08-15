package com.skinplate.api.domain.insight.entity;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.ExerciseHabit;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.entity.SleepPattern;
import com.skinplate.api.domain.user.entity.StressLevel;
import com.skinplate.api.domain.user.entity.WaterIntake;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 분석 1건당 하나. 생성 후 다시 계산하지 않는다. (PRD §18.10)
 *
 * snapshot_* 는 <b>주제를 고르고 문장을 만든 그 시점</b>의 프로필이다. 프로필을 나중에
 * 바꿔도 과거 인사이트의 근거는 그대로여야 한다 — 문장은 "수면이 부족하다"고 말하고
 * 있는데 근거만 "충분해요"로 바뀌어 보이면 그 화면은 설명할 수 없는 화면이 된다.
 */
@Entity
@Getter
@Table(name = "skin_insight")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinInsight extends BaseTimeEntity {

    /** V6 의 summary 길이와 묶인다. SkinInsightItem.DESCRIPTION_MAX_LENGTH 와 같은 이유다. */
    private static final int SUMMARY_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_analysis_id", nullable = false, unique = true)
    private SkinAnalysis skinAnalysis;

    @Column(nullable = false, length = SUMMARY_MAX_LENGTH)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_sleep_pattern", length = 20)
    private SleepPattern snapshotSleepPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_stress_level", length = 20)
    private StressLevel snapshotStressLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_exercise_habit", length = 20)
    private ExerciseHabit snapshotExerciseHabit;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_water_intake", length = 20)
    private WaterIntake snapshotWaterIntake;

    /** 신고 고민을 enum 이름으로 이어 붙인 것. 표시용이 아니라 "왜 이 주제였나"의 기록이다. */
    @Column(name = "snapshot_concerns", nullable = false, length = 200)
    private String snapshotConcerns;

    @OneToMany(mappedBy = "skinInsight", cascade = CascadeType.PERSIST)
    @OrderBy("displayOrder ASC")
    private List<SkinInsightItem> items = new ArrayList<>();

    /**
     * 주제를 고르고 문장을 만든 시점의 프로필.
     *
     * <b>저장 시점의 user 에서 다시 뽑으면 안 된다.</b> 그 사이에 최대 25초짜리 AI 호출이
     * 있고, 그동안 사용자가 PATCH /auth/me 로 수면을 "충분해요"로 바꾸면 "수면이 부족하다고
     * 기록되고 있어요" 라는 문장 옆에 ENOUGH 가 저장된다. 분석당 1회 고정이라 영구히 그 상태다.
     */
    public record ProfileSnapshot(SleepPattern sleepPattern,
                                  StressLevel stressLevel,
                                  ExerciseHabit exerciseHabit,
                                  WaterIntake waterIntake,
                                  String concerns) {

        /**
         * AppUser 하나에서만 만든다 — 호출부가 임의 값을 실어 보낼 여지 자체를 없앤다.
         * LAZY 컬렉션을 읽으므로 트랜잭션 안에서만 부를 수 있다.
         */
        public static ProfileSnapshot of(AppUser user) {
            return new ProfileSnapshot(
                    user.getSleepPattern(),
                    user.getStressLevel(),
                    user.getExerciseHabit(),
                    user.getWaterIntake(),
                    user.getSkinConcerns().stream()
                            .sorted()                        // Set 이라 입력 순서가 없다 — 선언 순으로 고정
                            .map(SkinConcern::name)
                            .collect(Collectors.joining(",")));
        }
    }

    /** user 연관관계는 저장 트랜잭션의 것을 쓰고, 스냅샷 <b>값</b>만 선정 시점에서 온다. */
    public static SkinInsight create(AppUser user,
                                     SkinAnalysis skinAnalysis,
                                     String summary,
                                     ProfileSnapshot snapshot,
                                     List<SkinInsightItem> items) {
        SkinInsight insight = new SkinInsight();
        insight.user = user;
        insight.skinAnalysis = skinAnalysis;
        insight.summary = clamp(summary, SUMMARY_MAX_LENGTH);
        insight.snapshotSleepPattern = snapshot.sleepPattern();
        insight.snapshotStressLevel = snapshot.stressLevel();
        insight.snapshotExerciseHabit = snapshot.exerciseHabit();
        insight.snapshotWaterIntake = snapshot.waterIntake();
        insight.snapshotConcerns = snapshot.concerns();
        items.forEach(insight::addItem);
        return insight;
    }

    private void addItem(SkinInsightItem item) {
        items.add(item);
        item.assignTo(this);
    }

    /**
     * SkinPlate.clamp 와 같은 이유·같은 방식이다 — Structured Outputs 는 프롬프트의 글자 수
     * 요청을 지켜주지 않고, 넘치는 문장 하나가 INSERT 를 깨서 인사이트 전체를 잃게 둘 수 없다.
     * 경계가 이모지 한가운데면 반쪽 문자가 남아 Postgres 가 거절한다.
     *
     * SkinInsightItem 도 이것을 쓴다. 자식 쪽에 두면 부모가 자식을 호출하게 되는데,
     * 이 애그리거트의 참조 방향(SkinInsightItem.assignTo)은 이미 자식 → 부모다.
     * 상한을 인자로 받는 이유는 두 컬럼이 다른 컬럼이기 때문이다 — 지금은 둘 다 300 이지만
     * 한쪽만 넓히는 마이그레이션이 오면 상수 하나로 묶여 있던 쪽이 조용히 어긋난다.
     */
    static String clamp(String sentence, int maxLength) {
        if (sentence == null || sentence.length() <= maxLength) return sentence;

        int end = Character.isHighSurrogate(sentence.charAt(maxLength - 1))
                ? maxLength - 1
                : maxLength;

        return sentence.substring(0, end);
    }
}
