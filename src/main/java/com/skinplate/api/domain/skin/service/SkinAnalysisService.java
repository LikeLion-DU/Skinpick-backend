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
import com.skinplate.api.global.common.Texts;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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

    /** ageAssessment 는 DB 컬럼이 아니라 화면 상한이다. summary 와 같은 값으로 맞춘다. */
    private static final int ASSESSMENT_MAX_LENGTH = 300;

    /**
     * evidence 개수 상한. 스키마로는 못 막는다 — strict 모드에 maxItems 가 없다.
     * 프롬프트가 지시하고 여기서 자른다. 막는 것은 화면이지 토큰이 아니다 —
     * 절삭은 응답을 다 받은 뒤라 그 토큰은 이미 생성됐고 과금도 끝났다.
     */
    private static final int METRIC_EVIDENCE_MAX = 2;
    private static final int AGE_EVIDENCE_MAX = 1;

    private static final int MIN_SKIN_AGE = 18;
    private static final int MAX_SKIN_AGE = 80;

    /**
     * 응답에 실리는 나이 축. AI 는 8개를 내지만 redness 는 빼고 내린다 —
     * metricDetails 에 이미 있어 화면에 붉은기 숫자가 둘이 되기 때문이다.
     * 아래 addAxis 호출과 이 목록이 같아야 한다.
     */
    private static final Map<String, Function<OpenAiSkinResult.SkinAgeAnalysis, OpenAiSkinResult.Axis>>
            RESPONSE_AGE_AXES = new LinkedHashMap<>() {{
                put("skinTexture",  OpenAiSkinResult.SkinAgeAnalysis::skinTexture);
                put("elasticity",   OpenAiSkinResult.SkinAgeAnalysis::elasticity);
                put("wrinkles",     OpenAiSkinResult.SkinAgeAnalysis::wrinkles);
                put("skinTone",     OpenAiSkinResult.SkinAgeAnalysis::skinTone);
                put("pores",        OpenAiSkinResult.SkinAgeAnalysis::pores);
                put("pigmentation", OpenAiSkinResult.SkinAgeAnalysis::pigmentation);
                put("blemishMarks", OpenAiSkinResult.SkinAgeAnalysis::blemishMarks);
            }};

    /** 위 축 중 "높을수록 나쁨". 나머지는 높을수록 좋다. */
    private static final Set<String> AGE_HIGHER_IS_WORSE =
            Set.of("wrinkles", "pores", "pigmentation", "blemishMarks");

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

        return toResponse(analysis, aiResult);
    }

    /**
     * highlights · skinType · skinTypeGap 은 저장하지 않고 조회할 때마다 지표에서 다시 만든다.
     * 파생값을 저장해두면 판정 규칙을 바꿨을 때 과거 기록과 어긋난다. (PRD §14.3 ⑤)
     *
     * 근거·피부 나이는 사정이 다르다 — 지표에서 다시 만들 수 없고, AI 를 한 번 더
     * 부르지 않는 한 복원되지 않는다. 그래서 이미 통째로 저장해 둔 원본 응답을 되읽는다.
     * 전용 컬럼을 따로 두면 같은 JSON 이 두 벌이 되고 마이그레이션이 하나 는다.
     */
    private SkinAnalysisResponse toResponse(SkinAnalysis analysis) {
        return toResponse(analysis, parseDetail(analysis.getRawAiResponse()));
    }

    private SkinAnalysisResponse toResponse(SkinAnalysis analysis, OpenAiSkinResult detail) {
        SkinMetrics metrics = analysis.getMetrics();

        return SkinAnalysisResponse.from(analysis,
                metricDetails(metrics, detail),
                skinType(metrics, detail),
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
     * metrics 필드와 metricDetails 가 서로 다른 숫자를 말할 일이 없고, 0~100 도 보장된다 —
     * 나이 축과 달리 여기서 범위를 다시 볼 필요가 없는 이유다.
     */
    private List<ScoredItemDto> metricDetails(SkinMetrics metrics, OpenAiSkinResult detail) {
        OpenAiSkinResult.MetricEvidence found = detail == null ? null : detail.metricEvidence();
        OpenAiSkinResult.MetricEvidence evidence =
                found != null ? found : OpenAiSkinResult.MetricEvidence.EMPTY;

        return List.of(
                ScoredItemDto.of("hydration", metrics.getHydration(), false, evidence.hydration(), METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("oil",       metrics.getOil(),       true,  evidence.oil(),       METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("redness",   metrics.getRedness(),   true,  evidence.redness(),   METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("trouble",   metrics.getTrouble(),   true,  evidence.trouble(),   METRIC_EVIDENCE_MAX),
                ScoredItemDto.of("barrier",   metrics.getBarrier(),   false, evidence.barrier(),   METRIC_EVIDENCE_MAX));
    }

    /**
     * 타입도 상태도 <b>지표에서 규칙으로 만든다</b> — AI 에게 묻지 않는다.
     *
     * 그래서 이 값은 {@code skinTypeGap.observed} 와 항상 같다. 예전에는 AI 관찰값이라
     * 둘이 갈릴 수 있었고, S05 는 제목과 칩에 서로 다른 타입을 나란히 그렸다.
     *
     * 피부결·색소만 나이 축에서 빌려 온다. 같은 것을 재는 Vision 값을 새로 만들지
     * 않으려는 것이다 — 확장 필드가 없던 시절의 기록이면 원본에 없어 null 이고,
     * 그때는 그 두 상태만 빠진다. 타입과 나머지 넷은 지표에서 나오므로 그대로 나온다.
     */
    private SkinTypeDto skinType(SkinMetrics metrics, OpenAiSkinResult detail) {
        OpenAiSkinResult.SkinAgeAnalysis age = detail == null ? null : detail.skinAgeAnalysis();

        return SkinTypeDto.of(
                SkinType.observe(metrics),
                SkinTrait.observe(metrics,
                        axisScore(age == null ? null : age.skinTexture()),
                        axisScore(age == null ? null : age.pigmentation())));
    }

    /**
     * 0~100 밖이면 없는 값으로 본다 — 나이 카드를 통째로 빼는 규칙과 같다.
     * clamp 하면 score 키가 빠진 응답(0)이 "피부결 거칢" 상태로 둔갑한다.
     */
    private static Integer axisScore(OpenAiSkinResult.Axis axis) {
        return axis != null && ScoredItemDto.isUsableScore(axis.score()) ? axis.score() : null;
    }

    /**
     * 나이 축 redness 는 응답에 넣지 않는다. 상태 지표에 이미 redness 가 있어서 둘 다
     * 내리면 화면에 붉은기 숫자가 둘이 되고, 값이 다를 때 사용자가 어느 쪽을 믿을지 알 수 없다.
     * AI 판단과 ageAssessment 근거에는 그대로 반영되고 원본은 raw_ai_response 에 남는다.
     */
    private SkinAgeDto skinAge(OpenAiSkinResult detail) {
        if (detail == null || detail.skinAgeAnalysis() == null) return null;
        OpenAiSkinResult.SkinAgeAnalysis age = detail.skinAgeAnalysis();

        // 축을 목록에서 만든다. 개수만 상수로 들고 있으면 사본이 하나 더 생긴 것뿐이라,
        // 축을 늘리고 상수를 안 고치면 모든 응답에서 카드가 사라지고 로그는 "AI 가 8/7개를
        // 줬다"로 찍혀 조사가 OpenAI 쪽으로 간다. 키를 오타 내도 개수는 맞아 통과한다.
        List<ScoredItemDto> axes = new ArrayList<>();
        RESPONSE_AGE_AXES.forEach((key, axis) ->
                addAxis(axes, key, axis.apply(age), AGE_HIGHER_IS_WORSE.contains(key)));

        // 축이 하나라도 빠지거나 나이가 범위 밖이면 통째로 없는 것으로 본다.
        //
        // 일부만 채워 내보내면 안 된다 — 계약이 axes[7] 이라 앱이 인덱스로 그리면
        // 피부톤 라벨 밑에 모공 숫자가 찍힌다. clamp 로 살리는 것도 안 된다.
        // estimatedSkinAge 가 빠진 응답(0)이 18 로 둔갑해서 화면에 "피부 나이 18세"
        // 라는 없는 데이터가 그려진다. skinType() 과 같은 규칙 — 의심스러우면 뺀다.
        if (axes.size() != RESPONSE_AGE_AXES.size()
                || age.estimatedSkinAge() < MIN_SKIN_AGE || age.estimatedSkinAge() > MAX_SKIN_AGE) {
            log.warn("피부 나이 분석을 쓸 수 없다 — 나이 {} · 축 {}/{}개",
                    age.estimatedSkinAge(), axes.size(), RESPONSE_AGE_AXES.size());
            return null;
        }

        // 설명이 비었다고 카드를 버리지는 않는다. 점수 일곱 개는 멀쩡한데 문장 하나
        // 때문에 다 버리면 그게 더 큰 손실이다. 키를 생략하면(non_null) 앱이 설명
        // 블록만 접는다.
        String assessment = Texts.ellipsize(age.ageAssessment(), ASSESSMENT_MAX_LENGTH);
        if (assessment != null && assessment.isBlank()) {
            log.warn("피부 나이 설명이 비어 있다 — 축 {}개는 그대로 내린다", axes.size());
            assessment = null;
        }

        return new SkinAgeDto(age.estimatedSkinAge(), List.copyOf(axes), assessment);
    }

    /**
     * 점수가 0~100 밖이면 축을 넣지 않는다. 그러면 아래 개수 검사에서 걸려 카드가
     * 통째로 빠진다 — clamp 로 살리면 score 키가 빠진 응답(0)이 "피부결 0점 · SEVERE"
     * 라는 없는 등급으로 화면에 그려진다. estimatedSkinAge 를 clamp 하지 않는 것과 같다.
     */
    private static void addAxis(List<ScoredItemDto> axes, String key,
                                OpenAiSkinResult.Axis axis, boolean higherIsWorse) {
        if (axis == null || !ScoredItemDto.isUsableScore(axis.score())) return;
        axes.add(ScoredItemDto.of(key, axis.score(), higherIsWorse, axis.evidence(), AGE_EVIDENCE_MAX));
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
     * 프롬프트의 "1~3문장, 200자 이내"는 권고일 뿐이라 AI 가 길게 답할 수 있다.
     * 300자를 넘기면 저장에서 터지는데(summary 컬럼이 VARCHAR(300)), 그 시점엔
     * 유료 호출이 이미 끝나 있어 되돌릴 방법이 없다. 잘라서라도 결과를 돌려준다.
     */
    private String trimSummary(String summary) {
        return Texts.truncate(summary, SUMMARY_MAX_LENGTH);
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
