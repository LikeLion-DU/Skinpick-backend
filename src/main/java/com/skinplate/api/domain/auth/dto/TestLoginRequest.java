package com.skinplate.api.domain.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 본문 전체가 선택 사항이다. {} 이거나 아예 생략해도 슬롯 1로 로그인된다.
 * 컨트롤러에서 @RequestBody(required = false)로 받고 slot()을 호출한다.
 */
public record TestLoginRequest(

        @Min(value = 1, message = "slot은 1~3 사이여야 합니다.")
        @Max(value = 3, message = "slot은 1~3 사이여야 합니다.")
        Integer slot
) {
    public static final int DEFAULT_SLOT = 1;

    /** null-safe 접근자. request 자체가 null일 수도 있으므로 정적 메서드로도 제공한다. */
    public int slotOrDefault() {
        return slot == null ? DEFAULT_SLOT : slot;
    }

    public static int slotOf(TestLoginRequest request) {
        return request == null ? DEFAULT_SLOT : request.slotOrDefault();
    }
}
