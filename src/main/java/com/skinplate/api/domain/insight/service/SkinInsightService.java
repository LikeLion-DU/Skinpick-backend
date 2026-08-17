package com.skinplate.api.domain.insight.service;

import com.skinplate.api.domain.insight.dto.SkinInsightResponse;
import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.entity.SkinInsight;
import com.skinplate.api.domain.insight.entity.SkinInsight.ProfileSnapshot;
import com.skinplate.api.domain.insight.entity.SkinInsightItem;
import com.skinplate.api.domain.insight.repository.SkinInsightRepository;
import com.skinplate.api.domain.insight.service.InsightTopics.Topic;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import com.skinplate.api.infra.openai.prompt.SkinInsightPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 개인화 피부 인사이트. (PRD §18.10)
 *
 * 분석 1건당 <b>한 번만</b> 만든다. 프로필을 나중에 바꿔도 이미 만들어진 인사이트는
 * 다시 계산되지 않는다 — 화면에 남은 문장과 그 근거가 어긋나지 않게 하려는 것이다.
 * 단, <b>다룰 주제가 없으면 저장 자체를 하지 않는다</b> — 빈 인사이트를 저장하면 1회 고정이
 * 족쇄가 되어, 그 뒤에 고민·습관을 채워도 그 분석은 영영 빈 화면이다.
 * (RecommendationService.createOnce 의 {@code if (built.isEmpty()) return;} 과 같은 판단)
 *
 * 주제 선정은 {@link InsightTopics}, 문장은 AI. RecommendationService 와 같은 분리다.
 * 다른 점은 <b>AI 실패를 삼키지 않는다</b>는 것 하나다 — 1회 고정 정책이라 정적 폴백을
 * 저장하면 그 폴백이 영원히 그 분석의 인사이트가 된다. 저장하지 않고 던지면 다음 조회가
 * 그대로 재시도가 된다.
 */
@Service
@RequiredArgsConstructor
public class SkinInsightService {

    /**
     * 다룰 주제가 하나도 없을 때. AI 를 부르지도, 저장하지도 않는다 —
     * 할 말이 없는데 문장을 사 오지 않고, 할 말이 없다는 사실을 영구히 굳히지도 않는다.
     */
    private static final String HEALTHY_SUMMARY =
            "지금은 주요 지표가 모두 안정적이에요. 지금의 관리 습관을 그대로 이어가 보세요.";

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final SkinInsightRepository skinInsightRepository;
    private final VisionClient visionClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 인사이트가 아직 없으면 이 호출 안에서 만든다(lazy 동기 · 추천과 같은 방식).
     *
     * 트랜잭션이 두 번 열리고 그 사이에 AI 호출이 있다. @Transactional 하나로 감싸면
     * OpenAI 응답을 기다리는 12초 내내 커넥션을 쥐고 있게 된다.
     *
     * (읽기 구간도 같은 TransactionTemplate 을 쓴다. readOnly 플래그가 아니라
     *  "Response.from 이 트랜잭션 안에서 돈다"가 지켜야 할 불변식이고 — open-in-view 가
     *  false 라 LAZY 컬렉션이 밖에서 터진다 — 쓰기가 없는 구간에 템플릿을 하나 더 두는
     *  값은 크지 않다.)
     */
    public SkinInsightResponse getOrCreate(Long userId, Long skinAnalysisId) {
        Draft draft = transactionTemplate.execute(status -> load(userId, skinAnalysisId));

        // 이미 있거나, 다룰 주제가 없어 만들 것이 없는 경우다. 둘 다 AI 호출도 저장도 없다.
        if (draft.response() != null) return draft.response();

        SkinInsightSentences sentences = visionClient.generateSkinInsight(draft.userContext());

        // 매핑 실패는 저장 트랜잭션에 들어가기 전에 끝낸다 — 롤백할 것을 만들자고
        // 분석 행에 쓰기 락을 잡을 이유가 없다.
        String summary = requireSentence(sentences.summary());
        List<SkinInsightItem> items = toItems(draft.topics(), sentences);

        return transactionTemplate.execute(status ->
                save(userId, skinAnalysisId, summary, draft.snapshot(), items));
    }

    /**
     * 읽기 구간. 이미 있거나 만들 것이 없으면 응답을 완성해 돌려주고,
     * 만들 것이 있으면 AI 에 보낼 재료만 챙긴다.
     *
     * 프롬프트 문자열과 프로필 스냅샷을 여기서 다 만들어 나가는 것이 핵심이다 —
     * user·metrics 를 들고 나가면 트랜잭션 밖에서 LAZY 를 건드리게 되고, 스냅샷을
     * 저장 구간에서 다시 뽑으면 그 사이 12초 동안의 프로필 변경이 끼어든다.
     */
    private Draft load(Long userId, Long skinAnalysisId) {
        // 타인의 id 면 403 이 아니라 404 다. 존재 여부 자체를 알려주지 않는다.
        SkinAnalysis analysis = skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));

        SkinAnalysis previous = previousOf(userId, skinAnalysisId);

        Optional<SkinInsight> existing =
                skinInsightRepository.findBySkinAnalysisIdAndUserId(skinAnalysisId, userId);
        if (existing.isPresent()) {
            return new Draft(
                    SkinInsightResponse.from(analysis, existing.get(), previous), List.of(), null, null);
        }

        AppUser user = analysis.getUser();
        List<Topic> topics = InsightTopics.select(analysis.getMetrics(), user);

        // 지표가 전부 양호하고 신고 고민도 나쁜 습관도 없다 — 심사위원이 본인 얼굴로 찍어
        // 보는 경로가 정확히 이것이다. 그때그때 만들어 돌려주고 저장은 하지 않는다.
        if (topics.isEmpty()) {
            return new Draft(
                    SkinInsightResponse.healthy(analysis, previous, HEALTHY_SUMMARY), List.of(), null, null);
        }

        return new Draft(null, topics,
                SkinInsightPrompt.user(analysis, previous, user, topics),
                ProfileSnapshot.of(user));
    }

    /**
     * 저장 구간. 분석 행에 락을 잡고 <b>다시 확인한 뒤</b> 만든다.
     *
     * "있나 보고 없으면 넣는다"만으로는 부족하다. READ COMMITTED 에서 동시 요청 둘이
     * 모두 "없다"를 보고 둘 다 넣을 수 있다 — 화면을 두 번 열거나 느린 첫 응답에
     * 클라이언트가 재시도하면 그렇게 된다. 락으로 줄을 세우고, 마지막 방어선으로
     * V6 의 skin_analysis_id UNIQUE 가 뒤를 받친다.
     */
    private SkinInsightResponse save(Long userId, Long skinAnalysisId, String summary,
                                     ProfileSnapshot snapshot, List<SkinInsightItem> items) {
        SkinAnalysis analysis = skinAnalysisRepository.findByIdAndUserId(skinAnalysisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKIN_ANALYSIS_NOT_FOUND));

        SkinAnalysis previous = previousOf(userId, skinAnalysisId);

        skinAnalysisRepository.findForUpdate(skinAnalysisId);

        Optional<SkinInsight> existing =
                skinInsightRepository.findBySkinAnalysisIdAndUserId(skinAnalysisId, userId);
        if (existing.isPresent()) {
            // 락을 기다리는 동안 앞선 요청이 만들었다. 방금 산 문장은 버린다 —
            // 화면에 두 벌이 뜨는 것보다 낫다.
            return SkinInsightResponse.from(analysis, existing.get(), previous);
        }

        // 스냅샷은 여기서 다시 뽑지 않는다 — 인자로 받은 것이 주제를 고르고 문장을 만든
        // 그 시점의 프로필이다. user 는 연관관계용으로만 쓴다.
        SkinInsight insight = skinInsightRepository.save(
                SkinInsight.create(analysis.getUser(), analysis, summary, snapshot, items));

        return SkinInsightResponse.from(analysis, insight, previous);
    }

    /**
     * 선정된 주제 순서대로 AI 문장을 붙인다. 순서는 여기서 다시 정해지지 않는다 —
     * topics 의 인덱스가 그대로 displayOrder(=우선순위)다.
     *
     * <b>요청한 주제 중 하나라도 문장이 없으면 전체를 실패로 본다.</b> 빈 카드를 섞어
     * 내보내면 "3개 중 2개만 설명이 있는" 화면이 되고, 1회 고정이라 그 화면이 영구히 남는다.
     * 요청하지 않은 category 가 섞여 오면 조용히 무시한다 — Mock 이 13종을 전부 보낸다.
     */
    private static List<SkinInsightItem> toItems(List<Topic> topics, SkinInsightSentences sentences) {
        List<SkinInsightSentences.Topic> answered =
                sentences.topics() == null ? List.of() : sentences.topics();

        List<SkinInsightItem> items = new ArrayList<>();

        for (int order = 0; order < topics.size(); order++) {
            InsightCategory category = topics.get(order).category();

            String description = answered.stream()
                    .filter(answer -> category.name().equals(answer.category()))
                    .map(SkinInsightSentences.Topic::description)
                    .filter(sentence -> sentence != null && !sentence.isBlank())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_ANALYSIS_FAILED));

            items.add(SkinInsightItem.of(category, description, order));
        }

        return items;
    }

    /** summary 는 NOT NULL 이다. 빈 문장을 저장하면 그 분석은 영영 빈 요약을 갖는다. */
    private static String requireSentence(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        return summary;
    }

    /** 변화량의 기준이 될 직전 분석. 첫 분석이면 없다 — 그때는 changes 가 통째로 빠진다. */
    private SkinAnalysis previousOf(Long userId, Long skinAnalysisId) {
        return skinAnalysisRepository
                .findTopByUserIdAndIdLessThanOrderByIdDesc(userId, skinAnalysisId)
                .orElse(null);
    }

    /**
     * 읽기 구간이 넘겨주는 것. 둘 중 하나만 채워진다 —
     * response 가 있으면 더 만들 것이 없고(이미 있거나 다룰 주제가 없다),
     * 없으면 나머지 셋이 AI 호출과 저장의 재료다.
     */
    private record Draft(SkinInsightResponse response,
                         List<Topic> topics,
                         String userContext,
                         ProfileSnapshot snapshot) {}
}
