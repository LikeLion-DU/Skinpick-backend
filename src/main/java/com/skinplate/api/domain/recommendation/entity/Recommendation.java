package com.skinplate.api.domain.recommendation.entity;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "recommendation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_analysis_id", nullable = false)
    private SkinAnalysis skinAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationType type;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false)
    private int displayOrder;

    public static Recommendation of(AppUser user,
                                    SkinAnalysis skinAnalysis,
                                    RecommendationType type,
                                    String foodName,
                                    String reason,
                                    int displayOrder) {
        Recommendation recommendation = new Recommendation();
        recommendation.user = user;
        recommendation.skinAnalysis = skinAnalysis;
        recommendation.type = type;
        recommendation.foodName = foodName;
        recommendation.reason = reason;
        recommendation.displayOrder = displayOrder;
        return recommendation;
    }
}
