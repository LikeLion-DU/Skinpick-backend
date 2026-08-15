package com.skinplate.api.domain.user.entity;

/** 수면 패턴 (자가 신고). NULL = 미선택 — declaredSkinType 과 같은 의미론이다. */
public enum SleepPattern {

    LACKING("부족해요"),
    NORMAL ("보통이에요"),
    ENOUGH ("충분해요");

    private final String label;

    SleepPattern(String label) { this.label = label; }

    public String getLabel() { return label; }
}
