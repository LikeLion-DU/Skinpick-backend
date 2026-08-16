package com.skinplate.api.global.common;

/**
 * AI 가 돌려준 문자열을 상한에 맞춰 자른다.
 *
 * 프롬프트가 길이를 지시해도 그건 권고일 뿐이다. 피부 분석에서 상한을 넘긴 문장이
 * 오는 곳이 셋(summary · evidence · ageAssessment)이라 여기로 모았다.
 *
 * 다른 도메인에도 같은 모양의 코드가 남아 있다 — {@code SkinInsight.clamp} ·
 * {@code SkinPlate.clamp} · {@code FoodAnalysisService.trim}. 손대지 않은 것은
 * 기능 동결 직전이라 범위를 넓히지 않으려는 것이지, 저쪽이 옳아서가 아니다.
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

    /**
     * 사용자에게 그대로 보이는 문장용. 잘렸다는 표시를 남긴다.
     *
     * 말없이 끊으면 "볼과 입가에 부분적인 각질이 보이고 특히 광대 주변에서 건조함이 두드러지"
     * 처럼 문장이 그냥 멈춘 것으로 읽혀서, 사용자에게는 백엔드 버그로 보인다.
     */
    public static String ellipsize(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        // maxLength 가 1 이하면 truncate 가 charAt(-1) 로 터진다. 공유 유틸이라 막아 둔다.
        if (maxLength <= 1) return "…";

        return truncate(value, maxLength - 1) + "…";
    }
}
