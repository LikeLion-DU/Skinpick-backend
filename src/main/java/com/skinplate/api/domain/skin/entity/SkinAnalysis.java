package com.skinplate.api.domain.skin.entity;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "skin_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int skinScore;

    @Embedded
    private SkinMetrics metrics;

    @Column(length = 300)
    private String summary;

    /** AI 원본 응답. 프롬프트를 바꿔도 과거 데이터를 재해석할 수 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static SkinAnalysis create(AppUser user,
                                      String imageUrl,
                                      SkinMetrics metrics,
                                      int skinScore,
                                      String summary,
                                      String rawAiResponse) {
        SkinAnalysis analysis = new SkinAnalysis();
        analysis.user = user;
        analysis.imageUrl = imageUrl;
        analysis.metrics = metrics;
        analysis.skinScore = Math.max(0, Math.min(100, skinScore));
        analysis.summary = summary;
        analysis.rawAiResponse = rawAiResponse;
        return analysis;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
