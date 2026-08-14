package com.skinplate.api.domain.plate.controller;

import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.dto.PlateSimulateRequest;
import com.skinplate.api.domain.plate.dto.PlateSimulateResponse;
import com.skinplate.api.domain.plate.dto.SkinPlateResponse;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import com.skinplate.api.domain.plate.service.SkinPlateService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/plates")
@RequiredArgsConstructor
public class SkinPlateController {

    // @RequiredArgsConstructor 가 필드 선언 순서로 생성자를 만들기 때문에 순서를 바꾸면 테스트의 new SkinPlateController(...) 인자 순서가 깨진다
    private final SkinPlateService skinPlateService;
    private final PlateHistoryService plateHistoryService;

    /**
     * skinAnalysisId 는 선택이다. 생략하면 서버가 최신 피부 분석을 쓴다 —
     * 앱이 홈에서 바로 음식만 찍고 들어오는 경로가 있기 때문이다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SkinPlateResponse>> create(
            @CurrentUser Long userId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "skinAnalysisId", required = false) Long skinAnalysisId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(skinPlateService.create(userId, image, skinAnalysisId)));
    }

    @GetMapping("/{plateId}")
    public ApiResponse<SkinPlateResponse> get(@CurrentUser Long userId,
                                              @PathVariable Long plateId) {
        return ApiResponse.ok(skinPlateService.get(userId, plateId));
    }

    /**
     * 추천 행동을 실행했다고 가정하고 점수를 다시 계산한다. 저장하지 않으므로 200 이다.
     * 무대에서 60 → 68 이 움직이는 그 버튼이 이 엔드포인트를 부른다.
     */
    @PostMapping("/{plateId}/simulate")
    public ApiResponse<PlateSimulateResponse> simulate(
            @CurrentUser Long userId,
            @PathVariable Long plateId,
            @Valid @RequestBody PlateSimulateRequest request) {

        return ApiResponse.ok(skinPlateService.simulate(userId, plateId, request.actions()));
    }

    /**
     * from·to 는 KST 달력일이고 to 도 포함한다.
     * 2026-08-08 ~ 2026-08-14 는 8월 15일 자정 직전까지다.
     *
     * 둘 다 필수다 — 없으면 한 번의 호출이 사용자의 전체 기록을 메모리로 끌어올린다.
     */
    @GetMapping
    public ApiResponse<PlateHistoryResponse> history(
            @CurrentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ApiResponse.ok(plateHistoryService.get(userId, from, to));
    }
}
