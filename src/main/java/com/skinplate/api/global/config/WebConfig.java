package com.skinplate.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 설계서 §1.9 그대로. 스켈레톤에서 빠진 채로 넘어와 있었다 —
 * SecurityConfig 의 `.cors(withDefaults())` 는 CorsConfigurationSource 가 없으면
 * 빈 설정으로 풀려서 Access-Control-Allow-Origin 을 한 줄도 내보내지 않는다.
 * 그 상태로 배포하면 앱은 멀쩡하고 웹(Cloudflare Pages)만 전부 막힌다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")      // 해커톤: 전체 허용. 운영 시 도메인 지정
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
