package com.skinplate.api.domain.food.entity;

import java.math.BigDecimal;

/**
 * 사진에서 관찰한 섭취량 추정. AI 가 정밀한 gram 을 지어내지 못하게
 * 세 단계 + UNKNOWN 으로만 받는다.
 *
 * <p><b>점수 계산에는 쓰지 않는다.</b> 표준 음식 테이블이 확보한 "같은 사진 = 같은 점수"
 * 재현성과 충돌하기 때문이다 — SMALL 판정 하나로 나트륨이 1500 임계 아래로 내려가면
 * 같은 김치찌개가 8점씩 흔들린다. 점수는 1인분 기준 결정론을 유지하고, 이 값은
 * 저장·표시와 <b>일일 리포트 영양 합산</b>에서만 환산 계수로 쓴다.
 * "덜 먹기"의 점수 효과는 이미 시뮬레이션 행동(HALVE_SOUP 등)이 결정론적으로 맡는다.
 */
public enum PortionSize {

    SMALL(new BigDecimal("0.7")),
    MEDIUM(BigDecimal.ONE),
    LARGE(new BigDecimal("1.3")),

    /** 사진만으로 모르겠다 — 기존 1인분(×1.0) 동작으로 떨어진다. */
    UNKNOWN(BigDecimal.ONE);

    /** 리포트 영양 합산용 환산 계수. 1인분 영양값에 곱한다. */
    private final BigDecimal factor;

    PortionSize(BigDecimal factor) {
        this.factor = factor;
    }

    public BigDecimal getFactor() {
        return factor;
    }
}
