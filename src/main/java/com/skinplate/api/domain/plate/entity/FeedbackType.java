package com.skinplate.api.domain.plate.entity;

public enum FeedbackType {
    GOOD,       // 좋은 점    — scoreDelta 사용 (+)
    CAUTION,    // 주의사항    — scoreDelta 사용 (−)
    ACTION      // 추천 행동   — expectedGain 사용 (+)
}
