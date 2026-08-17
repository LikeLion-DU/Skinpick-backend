package com.skinplate.api.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * AI 가 사진에서 관찰한 음식 특성 5종 (스키마 v2 · V7).
 *
 * <p><b>전부 null 허용이고, null 은 UNKNOWN 으로 읽는다.</b> 세 경로가 null 을 만든다 —
 * V7 이전에 저장된 행, 배포 경계 30분 창의 구 analysisToken, 그리고 테스트 픽스처.
 * 셋 다 "이 정보가 없다"는 뜻이고 UNKNOWN 의 정의가 정확히 그것이라, getter 에서
 * 한 번에 흡수한다 — 호출부 어디에도 null 분기가 남지 않는다.
 *
 * <p>점수 입력은 spiciness·oiliness 뿐이다. UNKNOWN 이면 룰이 기존과 동일하게 동작한다
 * (하위 호환의 핵심 불변식). foodGroup·processingLevel 은 집계·패턴 분석용이고,
 * portionSize 는 리포트 영양 환산 전용이다.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodTraits {

    /** 신규 컬럼이 없던 시절 행의 대표값. 룰·리포트가 기존과 동일하게 동작한다. */
    public static final FoodTraits UNKNOWN = new FoodTraits(
            FoodGroup.ETC, PortionSize.UNKNOWN, Spiciness.UNKNOWN,
            Oiliness.UNKNOWN, ProcessingLevel.UNKNOWN);

    @Enumerated(EnumType.STRING)
    @Column(name = "food_group", length = 20)
    private FoodGroup foodGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "portion_size", length = 10)
    private PortionSize portionSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "spiciness", length = 10)
    private Spiciness spiciness;

    @Enumerated(EnumType.STRING)
    @Column(name = "oiliness", length = 10)
    private Oiliness oiliness;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_level", length = 20)
    private ProcessingLevel processingLevel;

    private FoodTraits(FoodGroup foodGroup, PortionSize portionSize, Spiciness spiciness,
                       Oiliness oiliness, ProcessingLevel processingLevel) {
        this.foodGroup = foodGroup;
        this.portionSize = portionSize;
        this.spiciness = spiciness;
        this.oiliness = oiliness;
        this.processingLevel = processingLevel;
    }

    public static FoodTraits of(FoodGroup foodGroup, PortionSize portionSize, Spiciness spiciness,
                                Oiliness oiliness, ProcessingLevel processingLevel) {
        return new FoodTraits(foodGroup, portionSize, spiciness, oiliness, processingLevel);
    }

    // getter 가 null 을 흡수한다 — 부분 null 행이 생겨도 NPE 대신 UNKNOWN 이다.

    public FoodGroup getFoodGroup() {
        return foodGroup == null ? FoodGroup.ETC : foodGroup;
    }

    public PortionSize getPortionSize() {
        return portionSize == null ? PortionSize.UNKNOWN : portionSize;
    }

    public Spiciness getSpiciness() {
        return spiciness == null ? Spiciness.UNKNOWN : spiciness;
    }

    public Oiliness getOiliness() {
        return oiliness == null ? Oiliness.UNKNOWN : oiliness;
    }

    public ProcessingLevel getProcessingLevel() {
        return processingLevel == null ? ProcessingLevel.UNKNOWN : processingLevel;
    }
}
