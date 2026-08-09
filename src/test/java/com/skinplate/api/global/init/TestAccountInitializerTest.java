package com.skinplate.api.global.init;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAccountInitializerTest {

    @Test
    @DisplayName("원탭 로그인은 슬롯 계정만 대상으로 한다 — 개발 계정으로는 들어갈 수 없다")
    void slotLoginNeverReachesDevAccounts() {
        // 개발 계정은 로그인 폼(이메일·비밀번호)으로만 들어가야 한다.
        // 그래야 S01 폼 검증과 INVALID_CREDENTIALS 응답이 실제로 한 번은 돌아본다.
        for (int slot = 1; slot <= 3; slot++) {
            assertThat(TestAccountInitializer.emailOfSlot(slot)).doesNotStartWith("dev");
        }
    }

    @Test
    @DisplayName("슬롯 번호로 지정된 이메일이 나온다")
    void resolvesEachSlot() {
        assertThat(TestAccountInitializer.emailOfSlot(1)).isEqualTo("test@skinplate.app");
        assertThat(TestAccountInitializer.emailOfSlot(2)).isEqualTo("test2@skinplate.app");
        assertThat(TestAccountInitializer.emailOfSlot(3)).isEqualTo("test3@skinplate.app");
    }

    @Test
    @DisplayName("범위 밖 슬롯은 시연 계정(슬롯 1)으로 떨어진다")
    void fallsBackToDemoAccount() {
        assertThat(TestAccountInitializer.emailOfSlot(99)).isEqualTo("test@skinplate.app");
    }
}
