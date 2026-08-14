package com.skinplate.api.global.security;

import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;

public record AnalysisTokenPayload(
        Long userId,
        String jti,
        Long skinAnalysisId,
        OpenAiFoodResult food
) {}
