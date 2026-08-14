package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    @DisplayName("to 날짜도 포함한다 — 끝 경계는 다음 날 자정 직전까지")
    void toDateIsInclusive() {
        DateRange range = DateRange.of(LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 14));

        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 8, 8, 0, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
    }

    @Test
    @DisplayName("하루짜리 구간도 다음 날 자정까지다 — BETWEEN 이면 자정 기록이 두 날에 겹친다")
    void singleDay() {
        DateRange range = DateRange.of(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14));

        assertThat(range.from()).isEqualTo(LocalDateTime.of(2026, 8, 14, 0, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
    }

    @Test
    @DisplayName("최근 7일은 오늘을 포함해 7일이다")
    void lastDaysIncludesToday() {
        DateRange range = DateRange.lastDays(7);

        assertThat(range.toDate()).isEqualTo(LocalDate.now(DateRange.KST));
        assertThat(range.fromDate()).isEqualTo(LocalDate.now(DateRange.KST).minusDays(6));
    }

    @Test
    @DisplayName("오늘은 KST 기준이다 — 시스템 시간대를 따르지 않는다")
    void todayIsKst() {
        assertThat(DateRange.today().fromDate()).isEqualTo(LocalDate.now(DateRange.KST));
    }

    @Test
    @DisplayName("from 이 to 보다 뒤면 400 이다 — 조용히 빈 배열을 주지 않는다")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 8)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("90일을 넘으면 400 이다 — 한 번의 호출이 전체 기록을 메모리로 끌어올린다")
    void tooWideRangeIsRejected() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> DateRange.of(from, from.plusDays(90)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("정확히 90일은 통과한다")
    void exactlyMaxRangeIsAllowed() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(DateRange.of(from, from.plusDays(89)).toDate()).isEqualTo(from.plusDays(89));
    }
}
