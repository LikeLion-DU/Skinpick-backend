package com.skinplate.api.domain.report.dto;

import com.skinplate.api.global.common.DateRange;

/** 리포트 화면의 기간 토글. 기준일은 서버의 오늘(KST) 이다. */
public enum ReportPeriod {

    TODAY { @Override public DateRange range() { return DateRange.today(); } },
    WEEK  { @Override public DateRange range() { return DateRange.lastDays(7); } };

    public abstract DateRange range();
}
