package com.skinplate.api.global.common;

/**
 * AI 가 돌려준 문자열을 상한에 맞춰 자른다.
 *
 * 프롬프트가 길이를 지시해도 그건 권고일 뿐이다. 상한을 넘긴 문장이 오는 곳이 셋
 * (summary · evidence · ageAssessment)인데, 자르는 로직을 셋으로 복사하면
 * 아래 서로게이트 처리가 한 곳에서만 고쳐지는 날이 온다.
 */
public final class Texts {

    private Texts() {}

    /**
     * 경계가 이모지 한가운데면 한 글자 덜 자른다. 반쪽짜리 char 가 남으면
     * Postgres 가 UTF-8 인코딩에서 거절해, 막으려던 500 이 그대로 난다.
     *
     * @return null 이면 null 그대로. 상한 이하면 원본 그대로
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;

        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }
}
