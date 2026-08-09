package com.skinplate.api.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * 컨트롤러 파라미터에 인증된 사용자 ID(Long)를 주입한다.
 *
 *   public ResponseEntity<?> analyze(@CurrentUser Long userId, ...)
 *
 * SecurityConfig가 인증되지 않은 요청을 전부 차단하므로 null이 될 수 없다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {}
