package com.skinplate.api.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 저장 시각이 실행 환경의 시간대를 따라가면, 같은 컬럼이 개발 맥에서는 KST 로
 * 배포 컨테이너에서는 UTC 로 쓰인다. 그러면 자정부터 아침 아홉시까지의 기록이
 * 전날로 잡히는데 예외도 로그도 남지 않는다.
 */
class JpaConfigTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("저장 시각은 실행 환경이 아니라 KST 를 따른다")
    void providerReturnsKst() {
        DateTimeProvider provider = new JpaConfig().kstDateTimeProvider();

        LocalDateTime actual = LocalDateTime.from(provider.getNow().orElseThrow());

        assertThat(actual)
                .isCloseTo(LocalDateTime.now(KST), within(5, ChronoUnit.SECONDS));
    }
}
