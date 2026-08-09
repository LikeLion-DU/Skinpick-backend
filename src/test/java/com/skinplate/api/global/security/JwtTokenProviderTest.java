package com.skinplate.api.global.security;

import com.skinplate.api.domain.user.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "skinplate-hackathon-secret-key-32bytes-or-more";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-32bytes-long";
    private static final long WEEK = 604800L;

    @Test
    @DisplayName("발급한 토큰에서 userId 를 그대로 다시 꺼낸다")
    void roundTrip() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, WEEK);

        String token = provider.createToken(42L, Role.USER);

        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("32바이트 미만 시크릿은 첫 요청이 아니라 기동 시점에 거부된다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", WEEK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 받아들이지 않는다")
    void rejectsForeignSignature() {
        String token = new JwtTokenProvider(SECRET, WEEK).createToken(42L, Role.USER);
        JwtTokenProvider other = new JwtTokenProvider(OTHER_SECRET, WEEK);

        assertThatThrownBy(() -> other.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException 으로 구분된다")
    void rejectsExpiredToken() {
        // 필터가 TOKEN_EXPIRED 와 UNAUTHORIZED 를 나눠 내리려면 이 예외가 구분돼서 올라와야 한다.
        JwtTokenProvider alreadyExpired = new JwtTokenProvider(SECRET, -1L);
        String token = alreadyExpired.createToken(42L, Role.USER);

        assertThatThrownBy(() -> alreadyExpired.parseUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
