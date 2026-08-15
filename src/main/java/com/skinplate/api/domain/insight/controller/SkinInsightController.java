package com.skinplate.api.domain.insight.controller;

import com.skinplate.api.domain.insight.dto.SkinInsightResponse;
import com.skinplate.api.domain.insight.service.SkinInsightService;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skin-insights")
@RequiredArgsConstructor
public class SkinInsightController {

    private final SkinInsightService skinInsightService;

    /** 인사이트가 아직 없으면 이 호출 안에서 만들어서 돌려준다. 앱은 조회 한 번으로 끝난다. */
    @GetMapping
    public ApiResponse<SkinInsightResponse> get(@CurrentUser Long userId,
                                                @RequestParam Long skinAnalysisId) {
        return ApiResponse.ok(skinInsightService.getOrCreate(userId, skinAnalysisId));
    }
}
