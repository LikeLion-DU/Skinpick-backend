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

    /** userId 는 토큰에서 온다. 필터가 못 넣으면 여기 도달하지 못하므로 null 이 아니다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SkinAnalysisResponse>> analyze(
            @CurrentUser Long userId,
            @RequestPart("image") MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(skinAnalysisService.analyze(userId, image)));
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
