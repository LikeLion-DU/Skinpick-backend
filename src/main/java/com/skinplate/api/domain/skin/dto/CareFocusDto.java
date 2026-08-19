package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinCareFocus;

/**
 * 관리 축 하나. 칩 하나가 이 레코드 하나다.
 *
 * <p>enum 이름과 라벨을 함께 싣는 이유는 {@code ConcernScoreDto} 와 같다 — 앱은
 * {@code focus} 를 키로만 쓰고 표시는 {@code label} 로 한다. 그래야 서버가 축을
 * 늘리거나 문구를 다듬을 때 앱을 고치지 않는다.
 */
public record CareFocusDto(SkinCareFocus focus, String label) {

    public static CareFocusDto from(SkinCareFocus focus) {
        return new CareFocusDto(focus, focus.getLabel());
    }
}
