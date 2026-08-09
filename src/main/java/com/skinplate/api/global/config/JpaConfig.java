package com.skinplate.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing          // BaseTimeEntity의 @CreatedDate / @LastModifiedDate 활성화
public class JpaConfig {}
