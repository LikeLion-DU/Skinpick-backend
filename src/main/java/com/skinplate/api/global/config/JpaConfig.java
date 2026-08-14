package com.skinplate.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaConfig {

    /**
     * 저장 시각은 실행 환경이 아니라 서비스 기준(KST)을 따른다.
     *
     * created_at 은 timestamp without time zone 이라 값 자체에 시간대가 없다.
     * 기본 제공자는 LocalDateTime.now() 를 JVM 기본 시간대로 부르는데,
     * 배포 이미지는 UTC 이고 개발 맥은 KST 다. 그대로 두면 같은 컬럼이
     * 환경마다 다른 뜻이 되고, 리포트의 "오늘" 경계가 아홉 시간 어긋난다.
     */
    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
