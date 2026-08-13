package com.skinplate.api.domain.skin.controller;

import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.service.SkinAnalysisService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/skin/analyses")
@RequiredArgsConstructor
public class SkinAnalysisController {

    private final SkinAnalysisService skinAnalysisService;

    /**
     * 정면·왼쪽·오른쪽 세 장이 하나의 분석을 이룬다. 결과도 하나다.
     *
     * 방향은 파트 이름이 정한다 — 배열 + type 필드로 받으면 중복 type·미지 type·
     * 순서 뒤바뀜을 전부 직접 검사해야 하는데, 파트 이름으로 두면 그 셋이 애초에
     * 표현되지 않는다. 한 장이라도 빠지면 Spring 이 MissingServletRequestPartException 을
     * 던지고 GlobalExceptionHandler 가 400(INVALID_INPUT)으로 받는다.
     *
     * userId 는 토큰에서 온다. 필터가 못 넣으면 여기 도달하지 못하므로 null 이 아니다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SkinAnalysisResponse>> analyze(
            @CurrentUser Long userId,
            @RequestPart("front") MultipartFile front,
            @RequestPart("left") MultipartFile left,
            @RequestPart("right") MultipartFile right) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(skinAnalysisService.analyze(userId, front, left, right)));
    }

    /** 홈(S02) "오늘의 Skin Score" 카드용. 기록이 없으면 data 가 비어 있다. */
    @GetMapping("/latest")
    public ApiResponse<SkinAnalysisResponse> latest(@CurrentUser Long userId) {
        return ApiResponse.ok(skinAnalysisService.getLatest(userId));
    }

    @GetMapping("/{skinAnalysisId}")
    public ApiResponse<SkinAnalysisResponse> detail(@CurrentUser Long userId,
                                                    @PathVariable Long skinAnalysisId) {
        return ApiResponse.ok(skinAnalysisService.get(userId, skinAnalysisId));
    }
}
