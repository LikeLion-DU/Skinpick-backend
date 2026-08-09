package com.skinplate.api.domain.auth.controller;

import com.skinplate.api.domain.auth.dto.AuthResponse;
import com.skinplate.api.domain.auth.dto.LoginRequest;
import com.skinplate.api.domain.auth.dto.MeResponse;
import com.skinplate.api.domain.auth.dto.SignupRequest;
import com.skinplate.api.domain.auth.dto.TestLoginRequest;
import com.skinplate.api.domain.auth.dto.UpdateProfileRequest;
import com.skinplate.api.domain.auth.service.AuthService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 본문 전체가 선택 사항이다. {} 이거나 생략하면 슬롯 1로 로그인된다. */
    @PostMapping("/test-login")
    public ApiResponse<AuthResponse> testLogin(
            @Valid @RequestBody(required = false) TestLoginRequest request) {
        return ApiResponse.ok(authService.testLogin(TestLoginRequest.slotOf(request)));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@CurrentUser Long userId) {
        return ApiResponse.ok(authService.me(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateProfile(@CurrentUser Long userId,
                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(userId, request));
    }
}
