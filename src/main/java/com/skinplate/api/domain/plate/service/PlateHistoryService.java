package com.skinplate.api.domain.plate.service;

import com.skinplate.api.domain.plate.dto.PlateHistoryDayDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryResponse;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
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

/** 날짜별 묶기를 서버가 한다. 앱은 받은 것을 그리기만 한다. */
@Service
@RequiredArgsConstructor
public class PlateHistoryService {

    /**
     * 시안의 "목표 80점". 아직 모두에게 같다.
     * 사용자별 목표가 생기면 이 상수 대신 사용자 설정을 읽는 자리다.
     */
    private static final int TARGET_SCORE = 80;

    private final SkinPlateRepository skinPlateRepository;
    private final SkinAnalysisRepository skinAnalysisRepository;

    @Transactional(readOnly = true)
    public PlateHistoryResponse get(Long userId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.of(from, to);

        List<SkinPlate> plates =
                skinPlateRepository.findInRange(userId, range.from(), range.toExclusive());
        Map<LocalDate, Integer> skinScorePerDay = skinScorePerDay(userId, range);

        return new PlateHistoryResponse(plates.stream()
                .collect(Collectors.groupingBy(plate -> plate.getCreatedAt().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<SkinPlate>>comparingByKey().reversed())
                .map(entry -> toDay(entry.getKey(), entry.getValue(), skinScorePerDay))
                .toList());
    }

    private PlateHistoryDayDto toDay(LocalDate date, List<SkinPlate> plates,
                                     Map<LocalDate, Integer> skinScorePerDay) {
        List<SkinPlate> sorted = plates.stream()
                .sorted(Comparator.comparing(SkinPlate::getCreatedAt).reversed()
                                  .thenComparing(Comparator.comparing(SkinPlate::getId).reversed()))
                .toList();

        // 그날 얼굴을 안 찍었어도 빈칸을 두지 않는다. 모든 기록에는 채점 기준이 된
        // 분석이 반드시 있고(skin_analysis_id NOT NULL), 화면이 "-" 를 띄우면
        // 같은 기록을 상세로 열었을 때 점수가 나오는 모순이 생긴다.
        Integer skinScore = skinScorePerDay.getOrDefault(date,
                // DESC 정렬의 마지막 = 그날 첫 기록. get(0) 으로 "고치면" 최신 기록이 돼 버린다.
                sorted.get(sorted.size() - 1).getSkinAnalysis().getSkinScore());

        return PlateHistoryDayDto.of(date, skinScore, averagePlateScore(sorted), TARGET_SCORE,
                dailyComment(sorted),
                sorted.stream().map(PlateHistoryItemDto::from).toList());
    }

    /**
     * 그날의 문장. 최신 기록부터 훑어 처음 만나는 문장을 쓴다 —
     * 최신 기록의 AI 생성이 실패한 날에도 이전 문장이 있으면 카드가 비지 않는다.
     * (입력은 시각 내림차순 정렬이라 첫 non-null 이 곧 최신 문장이다)
     */
    private String dailyComment(List<SkinPlate> sorted) {
        return sorted.stream()
                .map(SkinPlate::getAiDailyComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 그날의 대표 점수. 반올림해서 정수로 낸다 — 시안의 홈이 소수점 없이 "72점"을 쓴다.
     *
     * <p>가중치를 두지 않는다. 끼니마다 양이 다르니 저녁을 더 크게 쳐야 한다는 말이
     * 나올 수 있는데, 그러려면 섭취량을 알아야 하고 사진만으로는 알 수 없다.
     * 근거 없는 가중치는 "왜 이 점수인가"를 설명 못 하게 만든다.
     */
    private int averagePlateScore(List<SkinPlate> plates) {
        return (int) Math.round(plates.stream()
                .mapToInt(SkinPlate::getPlateScore)
                .average()
                // 이 메서드는 기록이 있는 날에만 불린다(그룹핑 결과라 빈 리스트가 없다).
                // 그래도 0 을 두는 것은 나중에 호출부가 바뀌어도 터지지 않게 하기 위함이다.
                .orElse(0));
    }

    /** 그날 찍은 분석 중 가장 늦은 것. 입력이 내림차순이라 첫 등장이 최신이다. */
    private Map<LocalDate, Integer> skinScorePerDay(Long userId, DateRange range) {
        Map<LocalDate, Integer> perDay = new LinkedHashMap<>();

        for (SkinAnalysis analysis : skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, range.from(), range.toExclusive())) {
            perDay.putIfAbsent(analysis.getCreatedAt().toLocalDate(), analysis.getSkinScore());
        }
        return perDay;
    }
}
