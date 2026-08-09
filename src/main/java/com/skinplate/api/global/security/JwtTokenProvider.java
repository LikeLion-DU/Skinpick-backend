package com.skinplate.api.global.security;

import com.skinplate.api.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;

    /** 토큰 유효기간(초). 응답의 expiresIn 필드에 그대로 사용된다. */
    @Getter
    private final long validitySeconds;

    public JwtTokenProvider(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.validity-seconds}") long validitySeconds) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "app.auth.jwt.secret 은 32바이트 이상이어야 합니다. 현재 " + bytes.length + "바이트");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validitySeconds = validitySeconds;
    }

    public String createToken(Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 userId 반환. 실패 시 JwtException 계열을 던진다. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
