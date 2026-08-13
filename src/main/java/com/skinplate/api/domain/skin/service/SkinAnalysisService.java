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
import com.skinplate.api.global.image.ImageEncoder;
import com.skinplate.api.global.image.ImageEncoder.EncodedImage;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.FacePhotoType;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 피부 분석 흐름 조율. 점수·요약 뱃지·갭 코멘트는 이미 완성된 세 컴포넌트가 계산하고,
 * 이 클래스는 순서와 트랜잭션 경계만 책임진다. (설계서 Part 4 #5)
 */
@Service
@RequiredArgsConstructor
public class SkinAnalysisService {

    /** summary 컬럼이 VARCHAR(300) 이다. */
    private static final int SUMMARY_MAX_LENGTH = 300;

    private final AppUserRepository userRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;
    private final VisionClient visionClient;
    private final SkinScoreCalculator scoreCalculator;
    private final SkinHighlightBuilder highlightBuilder;
    private final SkinTypeGapAnalyzer skinTypeGapAnalyzer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 이 메서드에 @Transactional 을 달면 25초짜리 AI 대기가 트랜잭션 안에 들어가고,
     * 동시 요청 몇 건으로 커넥션 풀이 마른다. 그래서 AI 호출을 먼저 끝낸 뒤
     * 저장 구간만 TransactionTemplate 으로 감싼다.
     *
     * 같은 빈의 @Transactional 메서드를 직접 호출하면 프록시를 거치지 않아
     * 트랜잭션이 아예 열리지 않는다 — 그 함정을 피하려고 템플릿을 쓴다.
     */
    public SkinAnalysisResponse analyze(Long userId,
                                        MultipartFile front,
                                        MultipartFile left,
                                        MultipartFile right) {

        // 세 장을 한 번에 보내 하나의 결과를 받는다. 방향은 파라미터 자리에서 정해지므로
        // 클라이언트가 보낸 순서를 신뢰할 일이 없다. (지시서 §5 · §8)
        List<FacePhoto> photos = List.of(
                encode(FacePhotoType.FRONT, front),
                encode(FacePhotoType.LEFT, left),
                encode(FacePhotoType.RIGHT, right));

        OpenAiSkinResult aiResult = visionClient.analyzeSkin(photos);

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
                trimSummary(aiResult.summary()), toJson(aiResult)));

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

    /**
     * 검증·Base64 변환은 ImageEncoder 가 한다 — 음식 경로와 같은 규칙이라
     * 복붙해 두면 한쪽만 고쳐지는 날이 온다.
     *
     * 방향 라벨을 같이 넘긴다. 세 장 중 하나만 잘못됐을 때 "이미지 형식이
     * 올바르지 않습니다" 만 뜨면 사용자는 셋 다 다시 찍는다.
     */
    private FacePhoto encode(FacePhotoType type, MultipartFile image) {
        EncodedImage encoded = ImageEncoder.encode(image, type.getLabel());
        return new FacePhoto(type, encoded.base64(), encoded.mediaType());
    }

    /**
     * 프롬프트의 "40자 이내"는 권고일 뿐이라 AI 가 길게 답할 수 있다.
     * 300자를 넘기면 저장에서 터지는데, 그 시점엔 25초짜리 유료 호출이 이미 끝나 있어
     * 되돌릴 방법이 없다. 잘라서라도 결과를 돌려준다.
     */
    private String trimSummary(String summary) {
        if (summary == null || summary.length() <= SUMMARY_MAX_LENGTH) return summary;

        // 경계가 이모지 한가운데면 반쪽짜리 문자가 남고, Postgres 가 UTF-8 인코딩에서 거절한다.
        // 막으려던 그 500 이 그대로 난다.
        int end = Character.isHighSurrogate(summary.charAt(SUMMARY_MAX_LENGTH - 1))
                ? SUMMARY_MAX_LENGTH - 1
                : SUMMARY_MAX_LENGTH;

        return summary.substring(0, end);
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
