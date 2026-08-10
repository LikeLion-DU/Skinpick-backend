package com.skinplate.api.domain.skin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 피부 분석 흐름 조율. 점수·요약 뱃지·갭 코멘트는 이미 완성된 세 컴포넌트가 계산하고,
 * 이 클래스는 순서와 트랜잭션 경계만 책임진다. (설계서 Part 4 #5)
 */
@Service
@RequiredArgsConstructor
public class SkinAnalysisService {

    /** 서버는 이미지를 저장하지 않는다. 형식만 보고 Base64 로 바꿔 보낸 뒤 버린다. (PRD §9.6) */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    private final AppUserRepository userRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;
    private final VisionClient visionClient;
    private final SkinScoreCalculator scoreCalculator;
    private final SkinHighlightBuilder highlightBuilder;
    private final SkinTypeGapAnalyzer skinTypeGapAnalyzer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 이 메서드에 @Transactional 을 달면 18초짜리 AI 대기가 트랜잭션 안에 들어가고,
     * 동시 요청 몇 건으로 커넥션 풀이 마른다. 그래서 AI 호출을 먼저 끝낸 뒤
     * 저장 구간만 TransactionTemplate 으로 감싼다.
     *
     * 같은 빈의 @Transactional 메서드를 직접 호출하면 프록시를 거치지 않아
     * 트랜잭션이 아예 열리지 않는다 — 그 함정을 피하려고 템플릿을 쓴다.
     */
    public SkinAnalysisResponse analyze(Long userId, MultipartFile image) {
        OpenAiSkinResult aiResult = visionClient.analyzeSkin(encodeBase64(image));

        // 얼굴이 아니면 저장하지 않는다. 남겨두면 /latest 가 얼굴 아닌 사진의 점수를 돌려준다.
        if (!aiResult.faceDetected()) {
            throw new BusinessException(ErrorCode.FACE_NOT_DETECTED);
        }

        return transactionTemplate.execute(status -> save(userId, aiResult));
    }

    @Transactional(readOnly = true)
    public SkinAnalysisResponse getLatest(Long userId) {
        return skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toResponse)
                .orElse(null);       // 아직 한 번도 안 찍은 사용자 → data: null (PRD §14.3 ⑥)
    }

    /** 타인의 id 면 403 이 아니라 404 다. 존재 여부 자체를 알려주지 않는다. */
    @Transactional(readOnly = true)
    public SkinAnalysisResponse get(Long userId, Long skinAnalysisId) {
        return skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));
    }

    private SkinAnalysisResponse save(Long userId, OpenAiSkinResult aiResult) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        SkinMetrics metrics = SkinMetrics.of(
                aiResult.hydration(), aiResult.oil(), aiResult.redness(),
                aiResult.trouble(), aiResult.barrier());

        SkinAnalysis analysis = skinAnalysisRepository.save(SkinAnalysis.create(
                user, metrics, scoreCalculator.calculate(metrics),
                aiResult.summary(), toJson(aiResult)));

        return SkinAnalysisResponse.from(analysis,
                highlightBuilder.build(metrics),
                skinTypeGapAnalyzer.analyze(user.getDeclaredSkinType(), metrics));
    }

    /**
     * highlights 와 skinTypeGap 은 저장하지 않고 조회할 때마다 지표에서 다시 만든다.
     * 파생값을 저장해두면 판정 규칙을 바꿨을 때 과거 기록과 어긋난다. (PRD §14.3 ⑤)
     */
    private SkinAnalysisResponse toResponse(SkinAnalysis analysis) {
        SkinMetrics metrics = analysis.getMetrics();

        return SkinAnalysisResponse.from(analysis,
                highlightBuilder.build(metrics),
                skinTypeGapAnalyzer.analyze(analysis.getUser().getDeclaredSkinType(), metrics));
    }

    private String encodeBase64(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        String contentType = image.getContentType();
        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE,
                    "JPEG 또는 PNG 이미지만 업로드할 수 있습니다.");
        }

        try {
            return Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, e);
        }
    }

    /** raw_ai_response 는 jsonb 다. 파싱된 결과를 다시 직렬화해 원본 형태로 남긴다. */
    private String toJson(OpenAiSkinResult aiResult) {
        try {
            return objectMapper.writeValueAsString(aiResult);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED, e);
        }
    }
}
