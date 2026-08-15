package com.skinplate.api.domain.plate;

import com.skinplate.api.domain.plate.entity.MealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MealTypeTest {

    private static MealType at(int hour, int minute) {
        return MealType.from(LocalDateTime.of(2026, 8, 14, hour, minute));
    }

    @Test
    @DisplayName("시안에 그려진 세 기록이 각각 아침·점심·저녁으로 나뉜다")
    void 시안_예시() {
        assertThat(at(8, 20)).isEqualTo(MealType.BREAKFAST);
        assertThat(at(12, 15)).isEqualTo(MealType.LUNCH);
        assertThat(at(20, 20)).isEqualTo(MealType.DINNER);
    }

    @Test
    @DisplayName("경계 시각에서 한 칸씩 넘어간다")
    void 경계값() {
        assertThat(at(10, 59)).isEqualTo(MealType.BREAKFAST);
        assertThat(at(11, 0)).isEqualTo(MealType.LUNCH);
        assertThat(at(16, 59)).isEqualTo(MealType.LUNCH);
        assertThat(at(17, 0)).isEqualTo(MealType.DINNER);
    }

    @Test
    @DisplayName("새벽 야식은 아침이 아니라 저녁이다 — 사용자가 자기 기록을 못 믿게 된다")
    void 자정_넘김() {
        assertThat(at(23, 30)).isEqualTo(MealType.DINNER);
        assertThat(at(2, 0)).isEqualTo(MealType.DINNER);
        assertThat(at(3, 59)).isEqualTo(MealType.DINNER);

        // 새벽 네 시부터는 아침으로 본다. 여기가 하루가 갈리는 지점이다.
        assertThat(at(4, 0)).isEqualTo(MealType.BREAKFAST);
    }
}
