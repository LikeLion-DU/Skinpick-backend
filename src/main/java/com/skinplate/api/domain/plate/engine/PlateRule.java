package com.skinplate.api.domain.plate.engine;

/**
 * 룰 하나를 추가하려면 이 인터페이스를 구현한 @Component 클래스를 만들면 끝이다.
 * 엔진 코드도, 기존 룰도 건드리지 않는다.
 */
public interface PlateRule {

    /** "R04" 같은 고유 코드. 응답의 appliedRules와 피드백 추적에 쓰인다. */
    String code();

    /** 낮을수록 먼저 평가된다. 화면 노출 우선순위와 같다. */
    default int priority() { return 100; }

    /** 이 룰이 적용되는 상황인가 */
    boolean supports(PlateContext context);

    /** supports가 true일 때만 호출된다 */
    RuleResult apply(PlateContext context);
}
