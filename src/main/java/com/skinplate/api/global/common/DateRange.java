package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * KST 달력일 구간. 조회는 half-open 이다 — from 이상, toExclusive 미만.
 *
 * BETWEEN 을 쓰면 끝 경계 자정에 찍힌 기록이 두 날에 겹쳐 잡힌다.
 * 리포트와 히스토리가 같은 날을 다르게 세는 순간 숫자를 설명할 수 없게 된다.
 */
public record DateRange(LocalDate fromDate, LocalDate toDate) {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한 번의 호출이 사용자의 전체 기록을 메모리로 끌어올리지 않게 막는다. */
    private static final int MAX_DAYS = 90;

    public static DateRange of(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간을 지정해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "조회 기간은 " + MAX_DAYS + "일을 넘을 수 없습니다.");
        }
        return new DateRange(fromDate, toDate);
    }

    /** 오늘을 포함해 days 일. days=7 이면 6일 전부터 오늘까지다. */
    public static DateRange lastDays(int days) {
        LocalDate today = LocalDate.now(KST);
        return new DateRange(today.minusDays(days - 1L), today);
    }

    public LocalDateTime from() {
        return fromDate.atStartOfDay();
    }

    public LocalDateTime toExclusive() {
        return toDate.plusDays(1).atStartOfDay();
    }
}
