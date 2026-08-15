package com.skinplate.api.domain.user.entity;

/** 수분 섭취 (자가 신고). NULL = 미선택. */
public enum WaterIntake {

    LACKING("부족해요"),
    NORMAL ("보통이에요"),
    ENOUGH ("충분해요");

    private final String label;

    WaterIntake(String label) { this.label = label; }

    public String getLabel() { return label; }
}
