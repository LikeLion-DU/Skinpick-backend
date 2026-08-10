package com.skinplate.api.domain.skin.dto;

/** 결과 화면(S05) 상단에 3줄로 보여주는 요약 뱃지. */
public record HighlightDto(String label, HighlightStatus status) {

    public static HighlightDto good(String label)    { return new HighlightDto(label, HighlightStatus.GOOD); }
    public static HighlightDto warn(String label)    { return new HighlightDto(label, HighlightStatus.WARN); }
    public static HighlightDto caution(String label) { return new HighlightDto(label, HighlightStatus.CAUTION); }

    public enum HighlightStatus {
        GOOD,       // 초록  — "피부 장벽 양호"
        WARN,       // 노랑  — "약간 건조"
        CAUTION     // 빨강  — "홍조 주의"
    }
}
