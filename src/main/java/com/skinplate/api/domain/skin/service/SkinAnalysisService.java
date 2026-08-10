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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

/**
 * 피부 분석 흐름 조율. 점수·요약 뱃지·갭 코멘트는 이미 완성된 세 컴포넌트가 계산하고,
 * 이 클래스는 순서와 트랜잭션 경계만 책임진다. (설계서 Part 4 #5)
 */
@Service
@RequiredArgsConstructor
public class SkinAnalysisService {

    /**
     * 서버는 이미지를 저장하지 않는다. 형식만 보고 Base64 로 바꿔 보낸 뒤 버린다. (PRD §9.6)
     *
     * 형식은 Content-Type 헤더가 아니라 실제 바이트로 판별한다. 헤더는 클라이언트가
     * 말하는 값이라, 모바일 갤러리가 application/octet-stream 을 보내면 멀쩡한 사진이
     * 400 으로 막히고, 반대로 헤더만 image/png 인 파일은 그대로 통과한다.
     */
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47};

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
     * 이 메서드에 @Transactional 을 달면 18초짜리 AI 대기가 트랜잭션 안에 들어가고,
     * 동시 요청 몇 건으로 커넥션 풀이 마른다. 그래서 AI 호출을 먼저 끝낸 뒤
     * 저장 구간만 TransactionTemplate 으로 감싼다.
     *
     * 같은 빈의 @Transactional 메서드를 직접 호출하면 프록시를 거치지 않아
     * 트랜잭션이 아예 열리지 않는다 — 그 함정을 피하려고 템플릿을 쓴다.
     */
    public SkinAnalysisResponse analyze(Long userId, MultipartFile image) {
        EncodedImage encoded = encode(image);
        OpenAiSkinResult aiResult = visionClient.analyzeSkin(encoded.base64(), encoded.mediaType());

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
     * 5MB 원본과 Base64 문자열을 AI 호출이 끝날 때까지 힙에 들고 있으므로
     * 요청 하나가 최대 12MB 쯤 쓴다. 시연 규모에서는 문제없지만, 동시 업로드가
     * 스무 건을 넘기면 작은 컨테이너에서는 OOM 이다. 그때는 업로드 상한을 낮춘다.
     */
    private EncodedImage encode(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        byte[] bytes = readBytes(image);
        String mediaType = detectMediaType(bytes);   // 인코딩 전에 막는다. 5MB 를 헛돌리지 않는다

        return new EncodedImage(Base64.getEncoder().encodeToString(bytes), mediaType);
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            // 업로드가 잘못된 게 아니라 서버가 파일을 못 읽은 것이다(임시 디렉터리 포화 등).
            // 400 으로 내리면 사용자는 멀쩡한 사진을 계속 다시 올리고,
            // 서버가 망가진 동안 5xx 지표(PRD §8.2)는 깨끗한 채로 남는다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    /** 판별한 타입은 OpenAI 의 data URI 에 그대로 선언된다. 내용과 어긋나면 400 이다. */
    private String detectMediaType(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) return MediaType.IMAGE_JPEG_VALUE;
        if (startsWith(bytes, PNG_MAGIC))  return MediaType.IMAGE_PNG_VALUE;

        throw new BusinessException(ErrorCode.INVALID_IMAGE,
                "JPEG 또는 PNG 이미지만 업로드할 수 있습니다.");
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        return bytes.length >= magic.length
                && Arrays.equals(bytes, 0, magic.length, magic, 0, magic.length);
    }

    /**
     * 프롬프트의 "40자 이내"는 권고일 뿐이라 AI 가 길게 답할 수 있다.
     * 300자를 넘기면 저장에서 터지는데, 그 시점엔 18초짜리 유료 호출이 이미 끝나 있어
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

    private record EncodedImage(String base64, String mediaType) {}

    /** raw_ai_response 는 jsonb 다. 파싱된 결과를 다시 직렬화해 원본 형태로 남긴다. */
    private String toJson(OpenAiSkinResult aiResult) {
        try {
            return objectMapper.writeValueAsString(aiResult);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED, e);
        }
    }
}
