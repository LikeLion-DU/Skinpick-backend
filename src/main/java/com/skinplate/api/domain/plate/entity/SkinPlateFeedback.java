package com.skinplate.api.domain.plate.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "skin_plate_feedback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinPlateFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_plate_id", nullable = false)
    private SkinPlate skinPlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type;

    @Column(nullable = false, length = 200)
    private String message;

    /**
     * "왜 이 판정인가" — 피부 지표와 음식 특성을 잇는 결정론 템플릿 문장 (V8 · NULL 허용).
     * message 는 리포트가 빈도로 집계하는 라벨이라 문장을 거기 넣을 수 없다.
     * ACTION 행은 행동 문구 자체가 설명이라 null 이다.
     */
    @Column(length = 300)
    private String reason;

    /** GOOD / CAUTION 행에서 사용 (± 점수) */
    @Column(nullable = false)
    private int scoreDelta;

    /** ACTION 행에서 사용 (행동 시 회복 점수) */
    @Column(nullable = false)
    private int expectedGain;

    @Column(length = 30)
    private String ruleCode;

    @Column(nullable = false)
    private int displayOrder;

    public static SkinPlateFeedback good(String ruleCode, String message, int scoreDelta, int order) {
        return good(ruleCode, message, null, scoreDelta, order);
    }

    public static SkinPlateFeedback good(String ruleCode, String message, String reason,
                                         int scoreDelta, int order) {
        return of(FeedbackType.GOOD, ruleCode, message, reason, scoreDelta, 0, order);
    }

    public static SkinPlateFeedback caution(String ruleCode, String message, int scoreDelta, int order) {
        return caution(ruleCode, message, null, scoreDelta, order);
    }

    public static SkinPlateFeedback caution(String ruleCode, String message, String reason,
                                            int scoreDelta, int order) {
        return of(FeedbackType.CAUTION, ruleCode, message, reason, scoreDelta, 0, order);
    }

    public static SkinPlateFeedback action(String ruleCode, String message, int expectedGain, int order) {
        return of(FeedbackType.ACTION, ruleCode, message, null, 0, expectedGain, order);
    }

    private static SkinPlateFeedback of(FeedbackType type, String ruleCode, String message,
                                        String reason, int scoreDelta, int expectedGain, int order) {
        SkinPlateFeedback feedback = new SkinPlateFeedback();
        feedback.type = type;
        feedback.ruleCode = ruleCode;
        feedback.message = message;
        feedback.reason = reason;
        feedback.scoreDelta = scoreDelta;
        feedback.expectedGain = expectedGain;
        feedback.displayOrder = order;
        return feedback;
    }

    void assignTo(SkinPlate skinPlate) {
        this.skinPlate = skinPlate;
    }
}
