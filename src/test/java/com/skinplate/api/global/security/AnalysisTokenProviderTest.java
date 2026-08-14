package com.skinplate.api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.user.entity.Role;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTokenProviderTest {

    private static final String SECRET = "skinplate-hackathon-secret-key-32bytes-or-more";
    private static final long THIRTY_MINUTES = 1800L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisTokenProvider provider = new AnalysisTokenProvider(SECRET, THIRTY_MINUTES, objectMapper);

    @Test
    @DisplayName("발급한 토큰을 파싱하면 userId·skinAnalysisId·food 가 그대로 복원된다")
    void roundTrip() {
        OpenAiFoodResult food = sampleFood();

        String token = provider.issue(42L, 7L, food);
        AnalysisTokenPayload payload = provider.parse(token, 42L);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.skinAnalysisId()).isEqualTo(7L);
        assertThat(payload.food().ingredients()).isEqualTo(food.ingredients());
        assertThat(payload.food().nutrition()).isEqualTo(food.nutrition());
    }

    @Test
    @DisplayName("같은 입력으로 두 번 발급해도 jti 는 매번 다르다")
    void jtiIsUniquePerIssue() {
        OpenAiFoodResult food = sampleFood();

        String firstJti = provider.parse(provider.issue(42L, 7L, food), 42L).jti();
        String secondJti = provider.parse(provider.issue(42L, 7L, food), 42L).jti();

        assertThat(firstJti).isNotEqualTo(secondJti);
    }

    @Test
    @DisplayName("서명이 위조된 토큰은 거부한다")
    void rejectsTamperedSignature() {
        String token = provider.issue(42L, 7L, sampleFood());
        // 마지막 글자는 건드리지 않는다 — HS256 서명 32바이트(256비트)를 43글자 base64url 로
        // 인코딩하면 마지막 글자는 유효 비트가 4개뿐이고 나머지 2비트는 버려진다(canonical
        // 인코딩에서 0으로 고정). jjwt 의 Decoders.BASE64URL 은 이 dangling 비트를 무시하므로,
        // 마지막 글자를 상위 4비트가 같은 다른 글자로 바꾸면(예: "Y" <-> "a") 디코딩된 서명
        // 바이트가 우연히 똑같아져 위조가 감지되지 않는다 — 16번에 1번꼴로 이 테스트가
        // 거짓으로 통과(정확히는 실패)한다. 뒤에서 두 번째 글자는 6비트가 전부 유효해
        // 항상 실제로 다른 바이트로 디코딩된다.
        int tamperIndex = token.length() - 2;
        char targetChar = token.charAt(tamperIndex);
        String tampered = token.substring(0, tamperIndex)
                + (targetChar == 'a' ? 'b' : 'a')
                + token.substring(tamperIndex + 1);

        assertThatThrownBy(() -> provider.parse(tampered, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("인증 토큰(JwtTokenProvider 발급)은 분석 토큰으로 받아들이지 않는다")
    void rejectsAuthKeySignedToken() {
        String authToken = new JwtTokenProvider(SECRET, THIRTY_MINUTES).createToken(42L, Role.USER);

        assertThatThrownBy(() -> provider.parse(authToken, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("분석 토큰은 인증 경로(JwtTokenProvider.parseUserId)를 통과하지 못한다")
    void analysisTokenCannotAuthenticate() {
        // JwtAuthenticationFilter 는 aud 를 읽지 않는다 — 같은 키로 서명했다면 이 토큰이
        // Authorization 헤더로도 인증을 통과해 버린다. 파생 키를 쓰기 때문에 여기서 막혀야 한다.
        String analysisToken = provider.issue(42L, 7L, sampleFood());
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, THIRTY_MINUTES);

        assertThatThrownBy(() -> jwtTokenProvider.parseUserId(analysisToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료된 분석 토큰은 ANALYSIS_EXPIRED 로 구분된다")
    void rejectsExpiredToken() {
        AnalysisTokenProvider alreadyExpired = new AnalysisTokenProvider(SECRET, -1L, objectMapper);
        String token = alreadyExpired.issue(42L, 7L, sampleFood());

        assertThatThrownBy(() -> alreadyExpired.parse(token, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_EXPIRED);
    }

    @Test
    @DisplayName("발급 대상과 다른 사용자가 조회하면 거부한다")
    void rejectsSubjectMismatch() {
        String token = provider.issue(1L, 7L, sampleFood());

        assertThatThrownBy(() -> provider.parse(token, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("issuer 가 다른 토큰은 거부한다")
    void rejectsForeignIssuer() throws Exception {
        String token = Jwts.builder()
                .subject("42")
                .issuer("someone-else")
                .audience().add("plate-record").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(THIRTY_MINUTES)))
                .signWith(deriveAnalysisKey())
                .compact();

        assertThatThrownBy(() -> provider.parse(token, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("audience 가 다른 토큰은 거부한다")
    void rejectsForeignAudience() throws Exception {
        String token = Jwts.builder()
                .subject("42")
                .issuer("skinplate")
                .audience().add("something-else").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(THIRTY_MINUTES)))
                .signWith(deriveAnalysisKey())
                .compact();

        assertThatThrownBy(() -> provider.parse(token, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("서명은 유효해도 skinAnalysisId·food 클레임이 없으면 거부한다")
    void rejectsTokenWithoutRequiredClaims() throws Exception {
        String token = Jwts.builder()
                .subject("42")
                .issuer("skinplate")
                .audience().add("plate-record").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(THIRTY_MINUTES)))
                .signWith(deriveAnalysisKey())
                .compact();

        assertThatThrownBy(() -> provider.parse(token, 42L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static OpenAiFoodResult sampleFood() {
        return new OpenAiFoodResult(true, "돼지고기 김치찌개", "한식/찌개", "BOILED", true,
                List.of(new OpenAiFoodResult.Ingredient("돼지고기", "PROTEIN"),
                        new OpenAiFoodResult.Ingredient("김치", "VEGETABLE")),
                new OpenAiFoodResult.Nutrition(520, new BigDecimal("28.5"),
                        new BigDecimal("24.0"), new BigDecimal("32.0"), 1850, new BigDecimal("6.2")));
    }

    /** AnalysisTokenProvider 생성자와 같은 방식으로 파생 키를 다시 계산한다 (iss/aud 위조 테스트 전용). */
    private static SecretKey deriveAnalysisKey() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] derived = mac.doFinal("analysis".getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(derived);
    }
}
