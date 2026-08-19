package com.skinplate.api.global.common;

/**
 * 조사 선택. 앞 낱말의 <b>종성 유무</b>로 갈린다.
 *
 * <p>이 유틸이 생긴 이유는 실제 버그다. 갭 문장이 {@code declared.getLabel() + "이라고"} 로
 * 조사를 붙박이로 두고 있었는데, 그때까지 자가 신고 타입 라벨이 전부 자음으로 끝나서
 * (건성·지성·복합성·민감성) 아무 문제가 없었다. <b>'수부지'</b> 를 선택지에 넣는 순간
 * "수부지이라고 생각하셨지만" 이 된다.
 *
 * <p>한 곳에 두는 이유도 같다 — 라벨을 문장에 끼우는 자리가 늘어날 때마다 각자
 * 조사를 고르면, 모음으로 끝나는 이름이 하나 들어오는 날 그중 하나만 깨진다.
 */
public final class KoreanParticle {

    private KoreanParticle() {}

    /** 한글 음절의 시작(가). 종성 계산의 기준점이다. */
    private static final char SYLLABLE_BASE = 0xAC00;

    /** 한글 음절의 끝(힣). */
    private static final char SYLLABLE_LAST = 0xD7A3;

    /** 한 초성당 음절 수 = 중성 21 × 종성 28. */
    private static final int FINAL_CONSONANT_COUNT = 28;

    /**
     * 마지막 글자에 종성이 있는가.
     *
     * <p>한글이 아니거나 빈 문자열이면 {@code false} 다 — 영문·숫자로 끝나는 라벨은
     * 지금 없지만, 있을 때 예외를 던지는 것보다 조사 하나가 어색한 편이 낫다.
     */
    public static boolean hasFinalConsonant(String word) {
        if (word == null || word.isEmpty()) return false;

        char last = word.charAt(word.length() - 1);
        if (last < SYLLABLE_BASE || last > SYLLABLE_LAST) return false;

        return (last - SYLLABLE_BASE) % FINAL_CONSONANT_COUNT != 0;
    }

    /** "민감성<b>이라고</b>" · "수부지<b>라고</b>" */
    public static String iRago(String word) {
        return word + (hasFinalConsonant(word) ? "이라고" : "라고");
    }
}
