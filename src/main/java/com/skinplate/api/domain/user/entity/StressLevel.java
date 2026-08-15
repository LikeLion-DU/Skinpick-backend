package com.skinplate.api.domain.user.entity;

/** 스트레스 정도 (자가 신고). NULL = 미선택. */
public enum StressLevel {

    LOW   ("낮음"),
    NORMAL("보통"),
    HIGH  ("높음");

    private final String label;

    StressLevel(String label) { this.label = label; }

    public String getLabel() { return label; }
}
