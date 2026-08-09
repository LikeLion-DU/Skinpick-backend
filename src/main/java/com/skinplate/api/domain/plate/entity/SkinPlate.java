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

    public void addFeedback(SkinPlateFeedback feedback) {
        feedbacks.add(feedback);
        feedback.assignTo(this);
    }

    public void addFeedbacks(List<SkinPlateFeedback> feedbackList) {
        feedbackList.forEach(this::addFeedback);
    }
}
