package com.skinplate.api.domain.plate.controller;

import com.skinplate.api.domain.plate.dto.PlateAnalysisResponse;
import com.skinplate.api.domain.plate.dto.PlateAnalysisSimulateRequest;
import com.skinplate.api.domain.plate.dto.PlateAnalysisSimulateResponse;
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.dto.PlateRecordRequest;
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
     * 분석만 하고 저장하지 않는다. 결과와 서명 토큰을 돌려주면 앱이 확인 후
     * 그 토큰을 POST /plates/records 로 되돌려 보내 저장을 확정한다.
     * 저장하지 않으므로 200 이다.
     */
    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PlateAnalysisResponse> analyze(
            @CurrentUser Long userId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "skinAnalysisId", required = false) Long skinAnalysisId) {

        return ApiResponse.ok(skinPlateService.analyze(userId, image, skinAnalysisId));
    }

    /**
     * analyze() 의 토큰을 되받아 기록을 확정한다. 실제로 행을 만드므로 201 이다 —
     * 멱등 재요청도 201 이고 같은 plateId 를 돌려준다(중복 저장은 되지 않는다).
     */
    @PostMapping("/records")
    public ResponseEntity<ApiResponse<SkinPlateResponse>> saveRecord(
            @CurrentUser Long userId,
            @Valid @RequestBody PlateRecordRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(skinPlateService.saveRecord(userId, request.analysisToken())));
    }

    @GetMapping("/{plateId}")
    public ApiResponse<SkinPlateResponse> get(@CurrentUser Long userId,
                                              @PathVariable Long plateId) {
        return ApiResponse.ok(skinPlateService.get(userId, plateId));
    }

    /** 기록 삭제. 되돌릴 수 없다 — 앱이 확인 창을 한 번 띄운 뒤 부른다. */
    @DeleteMapping("/{plateId}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId,
                                       @PathVariable Long plateId) {
        skinPlateService.delete(userId, plateId);
        return ResponseEntity.noContent().build();
    }

    /**
     * analyze() 의 토큰으로 저장 전에 시뮬레이션한다. 결과 화면에는 아직 plateId 가 없으므로
     * 토큰이 대상을 지목한다 — /plates/records 와 나란한 형태다. 저장하지 않으므로 200 이다.
     * 세그먼트 수가 달라 아래 /{plateId}/simulate 와 매핑이 갈라진다.
     */
    @PostMapping("/simulate")
    public ApiResponse<PlateAnalysisSimulateResponse> simulateFromToken(
            @CurrentUser Long userId,
            @Valid @RequestBody PlateAnalysisSimulateRequest request) {

        return ApiResponse.ok(skinPlateService.simulateFromToken(
                userId, request.analysisToken(), request.actions()));
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
