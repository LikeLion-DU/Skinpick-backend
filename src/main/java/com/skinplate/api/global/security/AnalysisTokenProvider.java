package com.skinplate.api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 분석 결과 서명 토큰. 인증 토큰({@link JwtTokenProvider})과 같은 JWT_SECRET 에서
 * 파생하되 별도 키로 서명한다 — JwtAuthenticationFilter 는 aud 를 보지 않으므로,
 * 같은 키를 쓰면 이 토큰이 Authorization 헤더로도 인증을 통과해 버린다.
 */
@Component
public class AnalysisTokenProvider {

    private static final String ISSUER = "skinplate";
    private static final String AUDIENCE = "plate-record";

    private final SecretKey key;
    private final long validitySeconds;
    private final ObjectMapper objectMapper;

    public AnalysisTokenProvider(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.analysis-token.validity-seconds}") long validitySeconds,
            ObjectMapper objectMapper) {

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "app.auth.jwt.secret 은 32바이트 이상이어야 합니다. 현재 " + secretBytes.length + "바이트");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] derived = mac.doFinal("analysis".getBytes(StandardCharsets.UTF_8));
            this.key = Keys.hmacShaKeyFor(derived);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("분석 토큰 서명 키 파생에 실패했습니다.", e);
        }

        this.validitySeconds = validitySeconds;
        this.objectMapper = objectMapper;
    }

    public String issue(Long userId, Long skinAnalysisId, OpenAiFoodResult food) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .claim("skinAnalysisId", skinAnalysisId)
                .claim("food", objectMapper.convertValue(food, Map.class))
                .signWith(key)
                .compact();
    }

    /** 서명·만료·iss·aud·sub 를 전부 검증하고, sub 가 currentUserId 와 다르면 거부한다. */
    public AnalysisTokenPayload parse(String token, Long currentUserId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            if (!userId.equals(currentUserId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            Long skinAnalysisId = claims.get("skinAnalysisId", Number.class).longValue();
            OpenAiFoodResult food = objectMapper.convertValue(claims.get("food"), OpenAiFoodResult.class);

            return new AnalysisTokenPayload(userId, claims.getId(), skinAnalysisId, food);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.ANALYSIS_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
