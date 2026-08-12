package com.skinplate.api.domain.recommendation.service;

import com.skinplate.api.domain.recommendation.dto.RecommendationResponse;
import com.skinplate.api.domain.recommendation.entity.Recommendation;
import com.skinplate.api.domain.recommendation.entity.RecommendationType;
import com.skinplate.api.domain.recommendation.repository.RecommendationRepository;
import com.skinplate.api.domain.recommendation.service.RecommendationCandidates.Candidates;
import com.skinplate.api.domain.recommendation.service.RecommendationCandidates.Concern;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 피부 기반 음식 추천.
 *
 * 없으면 이 API 안에서 그 자리에 만든다(lazy 동기). 피부 분석 직후 비동기로 미리
 * 만들어두는 쪽이 이론상 빠르지만, 스레드풀·트랜잭션 경계·실패 폴백을 다 세팅하고도
 * AI 가 8초 늦으면 발표 클라이맥스 직전에 S08 이 빈 화면이 된다. (PRD §14.3 ⑧)
 *
 * 지금은 이유 문구를 코드에서 만든다. AI 문장 생성은 그 위에 얹는다 —
 * 문서가 정한 축소 경로(G4)가 "추천을 정적 문구로 대체"이므로, 그 경로를
 * 먼저 동작하게 두면 AI 가 실패해도 화면이 비지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** 취약 항목을 몇 개까지 볼 것인가. 늘리면 추천 목록이 길어져 화면을 넘긴다. */
    private static final int TOP_CONCERN_COUNT = 2;

    private static final Map<Concern, String> RECOMMEND_REASON = Map.of(
            Concern.DRY,          "수분과 좋은 지방이 건조한 피부를 채우는 데 도움이 됩니다.",
            Concern.REDNESS,      "항산화 성분이 붉어진 피부의 자극을 줄여줍니다.",
            Concern.TROUBLE,      "비타민과 식이섬유가 트러블 진정에 도움이 됩니다.",
            Concern.OILY,         "기름기가 적어 유분이 많은 피부에 부담이 적습니다.",
            Concern.BARRIER_WEAK, "오메가3와 단백질이 피부 장벽 회복을 돕습니다.");

    private static final Map<Concern, String> AVOID_REASON = Map.of(
            Concern.DRY,          "수분을 빼앗아 건조를 더 심하게 만들 수 있습니다.",
            Concern.REDNESS,      "자극이 강해 홍조를 더 붉게 만들 수 있습니다.",
            Concern.TROUBLE,      "당류가 많아 트러블을 악화시킬 수 있습니다.",
            Concern.OILY,         "기름기가 많아 유분 과다를 부추길 수 있습니다.",
            Concern.BARRIER_WEAK, "가공도가 높아 장벽 회복에 도움이 되지 않습니다.");

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final RecommendationRepository recommendationRepository;

    @Transactional
    public RecommendationResponse getOrCreate(Long userId, Long skinAnalysisId) {
        SkinAnalysis analysis = skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));

        if (!recommendationRepository.existsBySkinAnalysisId(skinAnalysisId)) {
            recommendationRepository.saveAll(build(analysis));
        }

        return RecommendationResponse.from(skinAnalysisId,
                recommendationRepository
                        .findBySkinAnalysisIdAndUserIdOrderByDisplayOrderAsc(skinAnalysisId, userId));
    }

    /**
     * 음식 선정은 규칙, 문장 생성만 AI 몫이다. LLM 이 매번 다른 음식을 고르면
     * 데모마다 결과가 달라져 설명할 수 없다. (PRD §18.9)
     */
    private List<Recommendation> build(SkinAnalysis analysis) {
        List<Concern> concerns =
                RecommendationCandidates.topConcerns(analysis.getMetrics(), TOP_CONCERN_COUNT);

        List<Recommendation> recommendations = new ArrayList<>();
        int order = 0;

        // 취약 항목 두 개가 같은 음식을 추천할 수 있다(연어는 건조·장벽 양쪽에 나온다).
        // 화면에 같은 이름이 두 번 뜨지 않도록 순서를 지키면서 걸러낸다.
        Set<String> seen = new LinkedHashSet<>();

        for (Concern concern : concerns) {
            Candidates candidates = RecommendationCandidates.of(concern);

            for (String foodName : candidates.recommend()) {
                if (!seen.add(foodName)) continue;
                recommendations.add(Recommendation.of(analysis.getUser(), analysis,
                        RecommendationType.RECOMMEND, foodName,
                        RECOMMEND_REASON.get(concern), order++));
            }
        }

        for (Concern concern : concerns) {
            Candidates candidates = RecommendationCandidates.of(concern);

            for (String foodName : candidates.avoid()) {
                if (!seen.add(foodName)) continue;
                recommendations.add(Recommendation.of(analysis.getUser(), analysis,
                        RecommendationType.AVOID, foodName,
                        AVOID_REASON.get(concern), order++));
            }
        }

        return recommendations;
    }
}
