package com.skinplate.api.domain.plate.entity;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "skin_plate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinPlate extends BaseTimeEntity {

    /**
     * @Column(length) 과 클램프 기준을 한 값으로 묶는다. 둘이 갈라지면 방어가 무의미해진다.
     *
     * DDL 은 V4 에 300 으로 동결돼 있다. 이 값을 바꾸려면 새 V{n} 마이그레이션이 먼저다 —
     * 상수가 실제로 묶는 것은 @Column(length) 와 클램프 둘뿐이고, validate 는 varchar 길이를 보지 않는다.
     */
    private static final int AI_COMMENT_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_analysis_id", nullable = false)
    private SkinAnalysis skinAnalysis;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_analysis_id", nullable = false, unique = true)
    private FoodAnalysis foodAnalysis;

    @Column(nullable = false)
    private int plateScore;

    @Column(length = 300)
    private String summary;

    /** 점수에 기여한 룰 코드 배열. "왜 87점인가"를 사후에 설명할 수 있게 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String appliedRules;

    /** "AI 맞춤 TIP". 문장 생성만 AI 가 한다 — 점수·판정은 여기 없다. NULL 이면 앱이 카드를 숨긴다. */
    @Column(length = AI_COMMENT_MAX_LENGTH)
    private String aiTip;

    /** "오늘의 AI 코멘트". 그날의 최신 기록이 그날의 문장을 쥔다(V4 주석 참조). */
    @Column(length = AI_COMMENT_MAX_LENGTH)
    private String aiDailyComment;

    @OneToMany(mappedBy = "skinPlate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<SkinPlateFeedback> feedbacks = new ArrayList<>();

    public static SkinPlate create(AppUser user,
                                   SkinAnalysis skinAnalysis,
                                   FoodAnalysis foodAnalysis,
                                   int plateScore,
                                   String summary,
                                   String appliedRulesJson) {
        SkinPlate plate = new SkinPlate();
        plate.user = user;
        plate.skinAnalysis = skinAnalysis;
        plate.foodAnalysis = foodAnalysis;
        plate.plateScore = Math.max(0, Math.min(100, plateScore));
        plate.summary = summary;
        plate.appliedRules = appliedRulesJson;
        return plate;
    }

    /**
     * AI 문장을 붙인다. 저장 전에 한 번만 불린다.
     * 생성 실패 시 null 이 들어와도 된다 — 문장이 없다고 기록을 잃을 수는 없다.
     */
    public void attachAiComments(String tip, String dailyComment) {
        this.aiTip = clamp(tip);
        this.aiDailyComment = clamp(dailyComment);
    }

    /**
     * 프롬프트의 길이 요청은 강제가 아니다 — Structured Outputs 는 maxLength 를 지켜주지 않는다.
     * 넘치는 문장 하나가 INSERT 를 깨서 기록까지 잃게 둘 수는 없으니 저장 경계에서 자른다.
     *
     * 경계가 이모지 한가운데면 반쪽짜리 문자가 남고 Postgres 가 UTF-8 에서 거절한다 —
     * 막으려던 그 500 이 그대로 난다. (SkinAnalysisService.trimSummary 와 같은 방식)
     */
    private static String clamp(String sentence) {
        if (sentence == null || sentence.length() <= AI_COMMENT_MAX_LENGTH) return sentence;

        int end = Character.isHighSurrogate(sentence.charAt(AI_COMMENT_MAX_LENGTH - 1))
                ? AI_COMMENT_MAX_LENGTH - 1
                : AI_COMMENT_MAX_LENGTH;

        return sentence.substring(0, end);
    }

    public void addFeedback(SkinPlateFeedback feedback) {
        feedbacks.add(feedback);
        feedback.assignTo(this);
    }

    public void addFeedbacks(List<SkinPlateFeedback> feedbackList) {
        feedbackList.forEach(this::addFeedback);
    }
}
