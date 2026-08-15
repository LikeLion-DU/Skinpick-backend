package com.skinplate.api.domain.user.entity;

/**
 * 자가 신고 피부 고민 (목업 "피부설정" 복수 선택 9종).
 * 표시·추천 보완 전용 — 점수 계산(PlateContext)에는 넣지 않는다.
 */
public enum SkinConcern {

    ACNE        ("여드름"),
    REDNESS     ("민감/홍조"),
    DARK_CIRCLE ("다크서클"),
    DRYNESS     ("건조/각질"),
    OILINESS    ("피지/유분"),
    TEXTURE     ("피부결"),
    PIGMENTATION("색조침착"),
    ELASTICITY  ("탄력 저하"),
    PUFFINESS   ("부기");

    private final String label;

    SkinConcern(String label) { this.label = label; }

    public String getLabel() { return label; }
}
