package com.skinplate.api.domain.skin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.skin.dto.ScoredItemDto;
import com.skinplate.api.domain.skin.dto.SkinAgeDto;
import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.dto.SkinTypeDto;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.entity.SkinTrait;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 피부 분석 흐름 조율. 점수·요약 뱃지·갭 코멘트는 이미 완성된 세 컴포넌트가 계산하고,
 * 이 클래스는 순서와 트랜잭션 경계만 책임진다. (설계서 Part 4 #5)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkinAnalysisService {

    /** summary 컬럼이 VARCHAR(300) 이다. */
    private static final int SUMMARY_MAX_LENGTH = 300;

    /**
     * evidence 개수 상한. 스키마로는 못 막는다 — strict 모드에 maxItems 가 없다.
     * 프롬프트가 지시하고 여기서 자른다. 안 자르면 토큰 예산과 S05 레이아웃이 같이 무너진다.
     */
    private static final int METRIC_EVIDENCE_MAX = 2;
    private static final int AGE_EVIDENCE_MAX = 1;

    private static final int MIN_SKIN_AGE = 18;
    private static final int MAX_SKIN_AGE = 80;

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

        warnIfSkinTypeContradicts(aiResult.skinType(), metrics);

        SkinAnalysis analysis = skinAnalysisRepository.save(SkinAnalysis.create(
                user, metrics, scoreCalculator.calculate(metrics),
                trimSummary(aiResult.summary()), toJson(aiResult)));

        return toResponse(analysis, aiResult);
    }

    /**
     * highlights 와 skinTypeGap 은 저장하지 않고 조회할 때마다 지표에서 다시 만든다.
     * 파생값을 저장해두면 판정 규칙을 바꿨을 때 과거 기록과 어긋난다. (PRD §14.3 ⑤)
     *
     * 근거·피부 타입·피부 나이는 사정이 다르다 — 지표에서 다시 만들 수 없고, AI 를
     * 한 번 더 부르지 않는 한 복원되지 않는다. 그래서 이미 통째로 저장해 둔 원본 응답을
     * 되읽는다. 전용 컬럼을 따로 두면 같은 JSON 이 두 벌이 되고 마이그레이션이 하나 는다.
     */
    private SkinAnalysisResponse toResponse(SkinAnalysis analysis) {
        return toResponse(analysis, parseDetail(analysis.getRawAiResponse()));
    }

    private SkinAnalysisResponse toResponse(SkinAnalysis analysis, OpenAiSkinResult detail) {
        SkinMetrics metrics = analysis.getMetrics();

        return SkinAnalysisResponse.from(analysis,
                metricDetails(metrics, detail),
                skinType(detail),
                skinAge(detail),
                highlightBuilder.build(metrics),
                skinTypeGapAnalyzer.analyze(analysis.getUser().getDeclaredSkinType(), metrics));
    }

    /**
     * 확장 필드가 생기기 전에 저장된 행은 그 필드들이 null 인 채로 파싱된다 — 그 행도
     * 점수·지표·뱃지는 그대로 나와야 한다. 스키마를 또 바꿔서 아예 못 읽게 되면 null 이고,
     * 그때도 조회는 실패하지 않는다. 저장된 값을 못 읽는다고 화면이 죽을 이유가 없다.
     */
    private OpenAiSkinResult parseDetail(String rawAiResponse) {
        if (rawAiResponse == null || rawAiResponse.isBlank()) return null;

        try {
            return objectMapper.readValue(rawAiResponse, OpenAiSkinResult.class);
        } catch (Exception e) {
            log.warn("저장된 AI 응답을 읽지 못했다 — 상세 없이 응답한다: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 점수는 저장된 {@link SkinMetrics} 에서 가져온다. AI 원본이 아니라 clamp 를 거친 값이라
     * metrics 필드와 metricDetails 가 서로 다른 숫자를 말할 일이 없다.
     */
    private List<ScoredItemDto> metricDetails(SkinMetrics metrics, OpenAiSkinResult detail) {
        OpenAiSkinResult.MetricEvidence found = detail == null ? null : detail.metricEvidence();
        OpenAiSkinResult.MetricEvidence evidence = found != null ? found
                : new OpenAiSkinResult.MetricEvidence(null, null, null, null, null);

        return List.of(
                ScoredItemDto.of("hydration", metrics.getHydration(), false, evidence.hydration(), METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("oil",       metrics.getOil(),       true,  evidence.oil(),       METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("redness",   metrics.getRedness(),   true,  evidence.redness(),   METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("trouble",   metrics.getTrouble(),   true,  evidence.trouble(),   METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("barrier",   metrics.getBarrier(),   false, evidence.barrier(),   METRIC_EVIDENCE_MAX));
    }

    /** 스키마가 enum 을 강제하지만 그건 OpenAI 쪽 약속이다. 모르는 값이 오면 버린다. */
    private SkinTypeDto skinType(OpenAiSkinResult detail) {
        if (detail == null || detail.skinType() == null) return null;

        SkinType primary = parseEnum(SkinType.class, detail.skinType().primary());
        if (primary == null) return null;

        List<String> names = detail.skinType().traits();
        List<SkinTrait> traits = names == null ? List.of()
                : names.stream()
                       .map(name -> parseEnum(SkinTrait.class, name))
                       .filter(Objects::nonNull)
                       .toList();

        return new SkinTypeDto(primary, traits);
    }

    /**
     * 나이 축 redness 는 응답에 넣지 않는다. 상태 지표에 이미 redness 가 있어서 둘 다
     * 내리면 화면에 붉은기 숫자가 둘이 되고, 값이 다를 때 사용자가 어느 쪽을 믿을지 알 수 없다.
     * AI 판단과 ageAssessment 근거에는 그대로 반영되고 원본은 raw_ai_response 에 남는다.
     */
    private SkinAgeDto skinAge(OpenAiSkinResult detail) {
        if (detail == null || detail.skinAgeAnalysis() == null) return null;
        OpenAiSkinResult.SkinAgeAnalysis age = detail.skinAgeAnalysis();

        List<ScoredItemDto> axes = new ArrayList<>();
        addAxis(axes, "skinTexture",  age.skinTexture(),  false);
        addAxis(axes, "elasticity",   age.elasticity(),   false);
        addAxis(axes, "wrinkles",     age.wrinkles(),     true);
        addAxis(axes, "skinTone",     age.skinTone(),     false);
        addAxis(axes, "pores",        age.pores(),        true);
        addAxis(axes, "pigmentation", age.pigmentation(), true);
        addAxis(axes, "blemishMarks", age.blemishMarks(), true);

        int estimated = Math.max(MIN_SKIN_AGE, Math.min(MAX_SKIN_AGE, age.estimatedSkinAge()));

        return new SkinAgeDto(estimated, List.copyOf(axes), age.ageAssessment());
    }

    private static void addAxis(List<ScoredItemDto> axes, String key,
                                OpenAiSkinResult.Axis axis, boolean higherIsWorse) {
        if (axis == null) return;
        axes.add(ScoredItemDto.of(key, axis.score(), higherIsWorse, axis.evidence(), AGE_EVIDENCE_MAX));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 명백한 모순만 로그로 남긴다. <b>재분류는 하지 않는다</b> — 갭 카드가 쓰는 observed 는
     * 여전히 규칙에서 나오므로(PRD §14.3), AI 가 틀려도 화면은 흔들리지 않는다.
     * 그래도 남기는 이유는 프롬프트가 언제부터 어긋났는지 알 방법이 이것뿐이라서다.
     */
    private void warnIfSkinTypeContradicts(OpenAiSkinResult.SkinTypeResult skinType, SkinMetrics metrics) {
        if (skinType == null || skinType.primary() == null) return;

        boolean contradicts = switch (skinType.primary()) {
            case "OILY" -> !metrics.isOily();     // 유분 임계(70)를 못 넘겼는데 지성이라 함
            case "DRY"  -> !metrics.isDry();      // 수분 임계(40) 이상인데 건성이라 함
            default     -> false;
        };

        if (contradicts) {
            log.warn("AI 피부 타입 {} 이 지표와 어긋난다 — 수분 {} 유분 {}",
                    skinType.primary(), metrics.getHydration(), metrics.getOil());
        }
    }

    /**
     * 검증·Base64 변환은 ImageEncoder 가 한다 — 음식 경로와 같은 규칙이라
     * 복붙해 두면 한쪽만 고쳐지는 날이 온다.
     *
     * 방향 라벨을 같이 넘긴다. 세 장 중 하나만 잘못됐을 때 "이미지 형식이
     * 올바르지 않습니다" 만 뜨면 사용자는 셋 다 다시 찍는다.
     */
    private FacePhoto encode(FacePhotoType type, MultipartFile image) {
        // 프롬프트용 label 이 아니라 subject 다 — label 은 뺨 설명까지 붙은 긴 문구라
        // 오류 메시지의 주어로 쓰면 문장이 깨진다.
        EncodedImage encoded = ImageEncoder.encode(image, type.getSubject());
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
