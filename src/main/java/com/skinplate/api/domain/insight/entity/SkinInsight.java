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
 * snapshot_* 는 생성 당시 프로필이다. 프로필을 나중에 바꿔도 과거 인사이트의 근거는
 * 그대로여야 한다 — 문장은 "수면이 부족하다"고 말하고 있는데 근거만 "충분해요"로
 * 바뀌어 보이면 그 화면은 설명할 수 없는 화면이 된다.
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
     * 프로필 스냅샷은 인자로 받지 않고 여기서 user 에서 뽑는다 — "생성 시점의 프로필"이
     * 스냅샷의 정의이므로, 호출부가 다른 값을 실어 보낼 여지 자체를 없앤다.
     *
     * user 의 LAZY 컬렉션을 읽으므로 트랜잭션 안에서만 부를 수 있다.
     */
    public static SkinInsight create(AppUser user,
                                     SkinAnalysis skinAnalysis,
                                     String summary,
                                     List<SkinInsightItem> items) {
        SkinInsight insight = new SkinInsight();
        insight.user = user;
        insight.skinAnalysis = skinAnalysis;
        insight.summary = clamp(summary);
        insight.snapshotSleepPattern = user.getSleepPattern();
        insight.snapshotStressLevel = user.getStressLevel();
        insight.snapshotExerciseHabit = user.getExerciseHabit();
        insight.snapshotWaterIntake = user.getWaterIntake();
        insight.snapshotConcerns = user.getSkinConcerns().stream()
                .sorted()                                    // Set 이라 입력 순서가 없다 — 선언 순으로 고정
                .map(SkinConcern::name)
                .collect(Collectors.joining(","));
        items.forEach(insight::addItem);
        return insight;
    }

    private void addItem(SkinInsightItem item) {
        items.add(item);
        item.assignTo(this);
    }

    /** SkinPlate.clamp 와 같은 이유·같은 방식이다. */
    private static String clamp(String sentence) {
        if (sentence == null || sentence.length() <= SUMMARY_MAX_LENGTH) return sentence;

        int end = Character.isHighSurrogate(sentence.charAt(SUMMARY_MAX_LENGTH - 1))
                ? SUMMARY_MAX_LENGTH - 1
                : SUMMARY_MAX_LENGTH;

        return sentence.substring(0, end);
    }
}
