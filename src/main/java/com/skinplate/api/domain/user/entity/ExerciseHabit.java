package com.skinplate.api.domain.user.entity;

/** 운동 습관 (자가 신고). NULL = 미선택. */
public enum ExerciseHabit {

    NONE   ("거의 안 함"),
    LIGHT  ("주 1-2회"),
    REGULAR("주 3회 이상");

    private final String label;

    ExerciseHabit(String label) { this.label = label; }

    public String getLabel() { return label; }
}
