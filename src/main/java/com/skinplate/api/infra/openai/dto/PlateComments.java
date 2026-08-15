package com.skinplate.api.infra.openai.dto;

/**
 * 문장 생성 호출의 응답. 판단이 아니라 문장이다 — 숫자 필드가 없는 것이 의도다.
 */
public record PlateComments(String aiTip, String dailyComment) {

    /** AI 실패 시 대체값. 문장 없이도 저장은 진행돼야 한다. */
    public static final PlateComments EMPTY = new PlateComments(null, null);
}
