package com.skinplate.api.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 설정은 한 번 통째로 빠진 적이 있다. 없어도 앱은 정상 기동하고 APK 도 멀쩡해서,
 * 웹을 열어 보기 전까지 아무도 모른다 — 그때는 이미 배포 후다.
 *
 * 프리플라이트를 실제로 태우려면 서블릿 컨텍스트가 필요한데, 여기서 지켜야 할 것은
 * "매핑이 존재하고 /api/** 를 덮는가" 뿐이라 등록된 설정을 직접 본다.
 */
class WebConfigTest {

    /** CorsRegistry.getCorsConfigurations() 가 protected 다. 열어 주기만 한다. */
    private static class ExposedRegistry extends CorsRegistry {
        @Override
        protected Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }

    private Map<String, CorsConfiguration> configurations() {
        ExposedRegistry registry = new ExposedRegistry();
        new WebConfig().addCorsMappings(registry);
        return registry.getCorsConfigurations();
    }

    @Test
    @DisplayName("/api/** 에 CORS 매핑이 등록된다 — 없으면 웹에서 모든 호출이 막힌다")
    void registersApiMapping() {
        assertThat(configurations()).containsKey("/api/**");
    }

    @Test
    @DisplayName("업로드에 쓰는 POST 와 프리플라이트 OPTIONS 가 허용된다")
    void allowsUploadMethods() {
        CorsConfiguration configuration = configurations().get("/api/**");

        assertThat(configuration.getAllowedMethods())
                .contains("POST", "OPTIONS", "PATCH", "GET", "DELETE");
        assertThat(configuration.getAllowedOriginPatterns()).contains("*");
    }

    @Test
    @DisplayName("자격증명은 켜지 않는다 — 토큰은 쿠키가 아니라 Authorization 헤더로 간다")
    void doesNotAllowCredentials() {
        // allowedOriginPatterns("*") 와 allowCredentials(true) 를 같이 켜면
        // 스프링이 기동 중에 예외를 던진다. 쿠키를 안 쓰므로 그 조합에 닿지 않는다.
        assertThat(configurations().get("/api/**").getAllowCredentials()).isNull();
    }
}
