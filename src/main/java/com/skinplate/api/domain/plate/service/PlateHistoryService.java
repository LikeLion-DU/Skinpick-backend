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
                sorted.get(sorted.size() - 1).getSkinAnalysis().getSkinScore());

        return new PlateHistoryDayDto(date, skinScore, sorted.stream()
                .map(plate -> new PlateHistoryItemDto(plate.getId(),
                        plate.getFoodAnalysis().getFoodName(),
                        plate.getPlateScore(), plate.getCreatedAt()))
                .toList());
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
