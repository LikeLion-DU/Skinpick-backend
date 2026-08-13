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

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final RecommendationRepository recommendationRepository;

    @Transactional
    public RecommendationResponse getOrCreate(Long userId, Long skinAnalysisId) {
        SkinAnalysis analysis = skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));

        if (!recommendationRepository.existsBySkinAnalysisId(skinAnalysisId)) {
            createOnce(analysis);
        }

        return RecommendationResponse.from(skinAnalysisId,
                recommendationRepository
                        .findBySkinAnalysisIdAndUserIdOrderByDisplayOrderAsc(skinAnalysisId, userId));
    }

    /**
     * 아직 없을 때만 부른다. 분석 행에 락을 잡고 <b>다시 확인한 뒤</b> 만든다.
     *
     * "있나 보고 없으면 넣는다" 만으로는 부족하다. READ COMMITTED 에서 동시 요청 둘이
     * 모두 "없다" 를 보고 둘 다 넣을 수 있다 — S08 을 두 번 누르거나 느린 첫 응답에
     * 클라이언트가 재시도하면 7건이 14건이 되고, 이후 조회마다 같은 음식이 두 번씩
     * 뜬다. 지우기 전까지 회복되지 않는다.
     *
     * 락으로 줄을 세우고, 마지막 방어선으로 V3 의 UNIQUE 제약이 뒤를 받친다.
     */
    private void createOnce(SkinAnalysis analysis) {
        List<Recommendation> built = build(analysis);

        // 취약 항목이 없으면 잠글 것도 저장할 것도 없다. 피부가 멀쩡한 사용자의
        // 조회마다 쓰기 락을 잡고 빈 저장을 하게 두면 락만 값을 치른다.
        // 이 경우 exists 는 계속 false 지만, build 는 지표에서 바로 나오는 순수 계산이다.
        if (built.isEmpty()) return;

        skinAnalysisRepository.findForUpdate(analysis.getId());

        if (recommendationRepository.existsBySkinAnalysisId(analysis.getId())) {
            return;             // 락을 기다리는 동안 앞선 요청이 만들었다
        }

        recommendationRepository.saveAll(built);
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
        //
        // 추천과 주의를 따로 센다. 하나로 합치면 어느 한쪽에 이미 나온 이름이
        // 다른 쪽에서 조용히 사라지는데, V3 의 UNIQUE 는 (분석, 타입, 음식)이라
        // DB 는 양쪽 공존을 허용한다 — 코드만 몰래 더 좁게 막고 있는 셈이다.
        Set<String> seenRecommend = new LinkedHashSet<>();
        Set<String> seenAvoid = new LinkedHashSet<>();

        for (Concern concern : concerns) {
            Candidates candidates = RecommendationCandidates.of(concern);

            for (String foodName : candidates.recommend()) {
                if (!seenRecommend.add(foodName)) continue;
                recommendations.add(Recommendation.of(analysis.getUser(), analysis,
                        RecommendationType.RECOMMEND, foodName,
                        RecommendationCandidates.reasonOf(foodName), order++));
            }
        }

        for (Concern concern : concerns) {
            Candidates candidates = RecommendationCandidates.of(concern);

            for (String foodName : candidates.avoid()) {
                if (!seenAvoid.add(foodName)) continue;
                recommendations.add(Recommendation.of(analysis.getUser(), analysis,
                        RecommendationType.AVOID, foodName,
                        RecommendationCandidates.reasonOf(foodName), order++));
            }
        }

        return recommendations;
    }
}
