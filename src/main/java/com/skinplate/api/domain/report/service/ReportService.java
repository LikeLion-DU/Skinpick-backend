package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.MealDto;
import com.skinplate.api.domain.report.dto.PenaltyDto;
import com.skinplate.api.domain.report.dto.ReportPeriod;
import com.skinplate.api.domain.report.dto.ReportResponse;
import com.skinplate.api.domain.report.dto.TrendPointDto;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.global.common.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 저장된 것만 센다. 새 점수도 새 규칙도 만들지 않는다.
 *
 * 집계 입력은 사용자·기간으로 이미 좁혀진 Plate 목록이다.
 * skin_plate_feedback 을 직접 조회하지 않는다 — 그 테이블에는 user_id 가 없어서
 * 직접 집계하면 모든 사용자의 행이 섞인다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 화면이 감당하는 줄 수. 늘리면 반복 감점이 목록으로 보이기 시작한다. */
    private static final int TOP_PENALTY_COUNT = 3;
    private static final int TOP_FOOD_COUNT = 3;

    private final SkinPlateRepository skinPlateRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;

    @Transactional(readOnly = true)
    public ReportResponse get(Long userId, ReportPeriod period) {
        DateRange range = period.range();

        List<SkinPlate> plates =
                skinPlateRepository.findInRange(userId, range.from(), range.toExclusive());
        List<SkinAnalysis> analyses = skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, range.from(), range.toExclusive());

        boolean week = period == ReportPeriod.WEEK;

        return new ReportResponse(
                period, range.fromDate(), range.toDate(),
                analyses.isEmpty() ? null : analyses.get(0).getSkinScore(),
                week ? trend(analyses) : List.of(),
                plates.size(),
                averageScore(plates),
                penalties(plates),
                week ? List.of() : meals(plates));
    }

    /** 0건이면 null 이다. 0 을 내려보내면 화면이 "0점"으로 그린다. */
    private Integer averageScore(List<SkinPlate> plates) {
        if (plates.isEmpty()) return null;

        return (int) Math.round(plates.stream()
                .mapToInt(SkinPlate::getPlateScore).average().orElseThrow());
    }

    /** 기록이 없는 날은 배열에 넣지 않는다. 있는 날만 그린다. */
    private List<TrendPointDto> trend(List<SkinAnalysis> analyses) {
        Map<LocalDate, SkinAnalysis> latestPerDay = new LinkedHashMap<>();

        // 입력이 createdAt 내림차순이므로 각 날짜의 첫 등장이 그날의 최신이다.
        for (SkinAnalysis analysis : analyses) {
            latestPerDay.putIfAbsent(analysis.getCreatedAt().toLocalDate(), analysis);
        }

        return latestPerDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TrendPointDto(entry.getKey(), entry.getValue().getSkinScore()))
                .toList();
    }

    private List<MealDto> meals(List<SkinPlate> plates) {
        return plates.stream()
                .sorted(Comparator.comparing(SkinPlate::getCreatedAt).reversed()
                                  .thenComparing(Comparator.comparing(SkinPlate::getId).reversed()))
                .map(plate -> new MealDto(plate.getId(), plate.getFoodAnalysis().getFoodName(),
                        plate.getPlateScore(), plate.getCreatedAt()))
                .toList();
    }

    /**
     * type 만으로 거르지 않는다. ACTION 행은 delta 가 0 이고 message 가 긴 문장이라,
     * 섞이면 5글자 칩 자리에 안내문이 들어간다.
     */
    private List<PenaltyDto> penalties(List<SkinPlate> plates) {
        record Hit(String ruleCode, String label, int delta, String foodName) {}

        List<Hit> hits = plates.stream()
                .flatMap(plate -> plate.getFeedbacks().stream()
                        .filter(feedback -> feedback.getType() == FeedbackType.CAUTION)
                        .filter(feedback -> feedback.getScoreDelta() < 0)
                        .map(feedback -> new Hit(feedback.getRuleCode(), feedback.getMessage(),
                                feedback.getScoreDelta(), plate.getFoodAnalysis().getFoodName())))
                .toList();

        return hits.stream()
                .collect(Collectors.groupingBy(Hit::ruleCode))
                .values().stream()
                .map(group -> new PenaltyDto(
                        group.get(0).ruleCode(),
                        // 같은 룰의 라벨은 하나다. 아무거나 집으면 전제가 깨졌을 때
                        // 호출마다 라벨이 달라지므로 사전순 최솟값으로 고정한다.
                        group.stream().map(Hit::label).min(Comparator.naturalOrder()).orElseThrow(),
                        group.size(),
                        group.stream().mapToInt(Hit::delta).sum(),
                        topFoods(group.stream().map(Hit::foodName).toList())))
                .sorted(Comparator.comparingInt(PenaltyDto::count).reversed()
                                  .thenComparingInt(PenaltyDto::totalDelta)
                                  .thenComparing(PenaltyDto::ruleCode))
                .limit(TOP_PENALTY_COUNT)
                .toList();
    }

    /**
     * 음식 종류가 아니라 그 룰이 걸린 끼니 수를 센다. 같은 음식을 두 번 먹으면 2 다.
     *
     * 정렬 규칙은 일일·주간 리포트와 한 벌을 쓴다 — 두 벌이면 한쪽만 바뀌었을 때
     * 같은 목록이 화면마다 다른 순서로 뜬다.
     */
    private List<String> topFoods(List<String> foodNames) {
        return DailyReportAssembler.topCounts(foodNames.stream(), TOP_FOOD_COUNT).stream()
                .map(Map.Entry::getKey)
                .toList();
    }
}
