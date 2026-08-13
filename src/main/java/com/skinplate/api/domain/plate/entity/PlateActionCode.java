package com.skinplate.api.domain.plate.entity;

/**
 * 추천 행동을 실행했을 때 음식 정보가 어떻게 바뀌는지 정의한다.
 * 룰 엔진에 다시 넣기 위한 변환 규칙이며, 서버에 저장되지 않는다.
 */
public enum PlateActionCode {

    HALVE_SOUP    ("국물을 절반만 남기기"),
    LESS_SPICY    ("매운 양념 덜어내기"),
    NO_SUGAR_DRINK("단 음료 대신 물"),
    REMOVE_BATTER ("튀김옷 일부 제거");
    // LESS_RICE 는 넣지 않는다. R10(고열량)이 미구현이라 버튼이 붙을 카드가 없다.
    // R10 을 구현하면 그때 함께 추가한다.

    private final String label;

    PlateActionCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 이 행동이 룰 코드와 어떻게 대응되는지 (UI에서 버튼을 어느 카드에 붙일지 결정) */
    public String relatedRuleCode() {
        return switch (this) {
            case HALVE_SOUP     -> "R04";
            case LESS_SPICY     -> "R02";
            case NO_SUGAR_DRINK -> "R03";
            case REMOVE_BATTER  -> "R07";
        };
    }
}
