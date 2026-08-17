package com.skinplate.api.infra.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발표장에서 네트워크가 끊겼을 때 켜는 스위치다.
 * v1.3 설계는 프로퍼티와 프로파일을 섞어 둬서, AI_MOCK=true 를 넣고 재기동해도
 * 아무 일이 일어나지 않았다. 백업 플랜이 무대 위에서 처음 실패하는 종류라
 * "기동은 된다"로 끝내지 않고 어느 구현이 주입되는지까지 고정한다.
 */
class VisionClientSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestBeans.class)
            .withPropertyValues("app.ai.model=gpt-5.6-luna", "app.ai.timeout-seconds=18",
                                "app.ai.skin-max-tokens=1400", "app.ai.skin-timeout-seconds=28",
                                "app.ai.view-timeout-seconds=12");

    @Test
    @DisplayName("app.ai.mock=true 면 Mock 이 주입된다")
    void mockWinsWhenEnabled() {
        runner.withPropertyValues("app.ai.mock=true").run(context ->
                assertThat(context.getBean(VisionClient.class))
                        .isInstanceOf(MockOpenAiVisionClient.class));
    }

    @Test
    @DisplayName("프로퍼티가 없으면 실제 클라이언트가 주입된다")
    void realClientByDefault() {
        runner.run(context ->
                assertThat(context.getBean(VisionClient.class))
                        .isInstanceOf(OpenAiVisionClient.class));
    }

    @Test
    @DisplayName("app.ai.mock=false 면 실제 클라이언트가 주입된다")
    void realClientWhenDisabled() {
        runner.withPropertyValues("app.ai.mock=false").run(context ->
                assertThat(context.getBean(VisionClient.class))
                        .isInstanceOf(OpenAiVisionClient.class));
    }

    /**
     * 반드시 컴포넌트 스캔으로 올린다. @Bean 메서드로 직접 등록하면
     * 클래스에 붙은 @ConditionalOnProperty 와 @Primary 가 적용되지 않아
     * 실제 배선과 다른 것을 검증하게 된다.
     */
    @Configuration
    @ComponentScan(basePackageClasses = VisionClient.class)
    static class TestBeans {
        @Bean WebClient openAiWebClient() { return WebClient.builder().build(); }
    }
}
