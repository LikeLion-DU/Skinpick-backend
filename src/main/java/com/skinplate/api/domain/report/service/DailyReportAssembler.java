package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.user.entity.SkinConcern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 하루치 기록을 리포트 한 벌로 옮긴다. DB 도 Spring 도 모르는 순수 계산이라
 * 일일 조회와 주간 집계가 <b>같은 코드</b>를 탄다 — 두 화면의 하루 점수가 갈릴 길이 없다.
 *
 * <p>새 점수 규칙을 만들지 않는다. 저장된 {@code plate_score} 와 피드백의
 * {@code score_delta} 를 다시 셀 뿐이다.
 */
public final class DailyReportAssembler {

    private DailyReportAssembler() {}

    /** 화면이 감당하는 줄 수. 늘리면 반복되는 문구가 목록으로 보이기 시작한다. */
    private static final int TOP_MESSAGE_COUNT = 3;

    public static DailyReportResponse of(LocalDate date, List<SkinPlate> plates,
                                         Set<SkinConcern> concerns) {
        if (plates.isEmpty()) return DailyReportResponse.empty(date);

        // 최신이 위로 온다. 같은 시각이면 나중에 들어온 행(큰 id)이 먼저다.
        // (PlateHistoryService 와 같은 정렬 — 두 화면의 카드 순서가 같아야 한다)
        List<SkinPlate> sorted = plates.stream()
                .sorted(Comparator.comparing(SkinPlate::getCreatedAt).reversed()
                                  .thenComparing(Comparator.comparing(SkinPlate::getId).reversed()))
                .toList();

        int dailyScore = averageScore(sorted);

        return new DailyReportResponse(
                date,
                dailyScore,
                SkinLevel.of(dailyScore),
                sorted.size(),
                nutrition(sorted),
                concerns(sorted, concerns),
                sorted.stream().map(PlateHistoryItemDto::from).toList(),
                dailyComment(sorted),
                topMessages(sorted, FeedbackType.GOOD),
                topMessages(sorted, FeedbackType.CAUTION));
    }

    /**
     * 그날의 대표 점수. 반올림해서 정수로 낸다 — 시안이 소수점 없이 "72점"을 쓴다.
     *
     * <p>가중치를 두지 않는 이유는 히스토리와 같다. 끼니마다 양이 다르니 저녁을 더
     * 크게 쳐야 한다는 말이 나올 수 있는데, 그러려면 섭취량을 알아야 하고 사진만으로는
     * 알 수 없다. 근거 없는 가중치는 "왜 이 점수인가"를 설명 못 하게 만든다.
     */
    private static int averageScore(List<SkinPlate> plates) {
        return (int) Math.round(plates.stream()
                .mapToInt(SkinPlate::getPlateScore).average().orElseThrow());
    }

    /**
     * 그날의 문장. 최신 기록부터 훑어 처음 만나는 문장을 쓴다 — 최신 기록의 AI 생성이
     * 실패한 날에도 이전 문장이 있으면 카드가 비지 않는다. (PlateHistoryService 와 같은 규칙)
     */
    private static String dailyComment(List<SkinPlate> sorted) {
        return sorted.stream()
                .map(SkinPlate::getAiDailyComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 기록된 끼니의 영양 합계. 안 찍은 끼니는 세지 않는다 — 서버는 사용자가 굶은
     * 것인지 안 찍은 것인지 알 방법이 없고, 추정해서 채우면 그 순간 숫자가 거짓이 된다.
     */
    private static List<NutritionItemDto> nutrition(List<SkinPlate> plates) {
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal carb     = BigDecimal.ZERO;
        BigDecimal protein  = BigDecimal.ZERO;
        BigDecimal fat      = BigDecimal.ZERO;
        BigDecimal sodium   = BigDecimal.ZERO;
        BigDecimal sugar    = BigDecimal.ZERO;

        for (SkinPlate plate : plates) {
            Nutrition value = plate.getFoodAnalysis().getNutrition();
            calories = calories.add(BigDecimal.valueOf(value.getCaloriesKcal()));
            carb     = carb.add(value.getCarbG());
            protein  = protein.add(value.getProteinG());
            fat      = fat.add(value.getFatG());
            sodium   = sodium.add(BigDecimal.valueOf(value.getSodiumMg()));
            sugar    = sugar.add(value.getSugarG());
        }

        return List.of(
                NutritionItemDto.of(NutrientType.CALORIES, calories),
                NutritionItemDto.of(NutrientType.CARB, carb),
                NutritionItemDto.of(NutrientType.PROTEIN, protein),
                NutritionItemDto.of(NutrientType.FAT, fat),
                NutritionItemDto.of(NutrientType.SODIUM, sodium),
                NutritionItemDto.of(NutrientType.SUGAR, sugar));
    }

    /**
     * 고민별 점수. 끼니마다 "기준선 + 그 고민에 걸린 룰의 델타"를 내고 평균한다.
     *
     * <p>델타를 하루치로 한 번에 더하지 않는다. 그러면 같은 식단이라도 세 끼를 기록한
     * 날이 한 끼만 기록한 날보다 무조건 나쁘게 나온다 — 성실하게 기록할수록 점수가
     * 떨어지는 셈이다. 종합 점수(끼니 평균)와 같은 축으로 맞춘다.
     */
    private static List<ConcernScoreDto> concerns(List<SkinPlate> plates,
                                                  Set<SkinConcern> concerns) {
        return ConcernRules.scorable(concerns).stream()
                .map(concern -> ConcernScoreDto.of(concern, concernScore(plates, concern), null))
                .toList();
    }

    private static int concernScore(List<SkinPlate> plates, SkinConcern concern) {
        Set<String> ruleCodes = ConcernRules.of(concern);

        double average = plates.stream()
                .mapToInt(plate -> clamp(RuleConstants.BASE_SCORE + plate.getFeedbacks().stream()
                        // ACTION 행은 delta 가 0 이라 합계에 영향이 없지만, 명시적으로
                        // 거른다 — "점수에 쓰이는 행은 GOOD/CAUTION 뿐"이 규칙이다.
                        .filter(feedback -> feedback.getType() != FeedbackType.ACTION)
                        // rule_code 는 DDL 에서 NULL 을 허용한다(V1). Set.of(...) 는 크기와
                        // 무관하게 contains(null) 에서 NPE 라, 그런 행이 하나만 있어도
                        // 고민 점수가 아니라 리포트 전체가 500 이 된다.
                        .filter(feedback -> feedback.getRuleCode() != null)
                        .filter(feedback -> ruleCodes.contains(feedback.getRuleCode()))
                        .mapToInt(SkinPlateFeedback::getScoreDelta)
                        .sum()))
                .average()
                .orElseThrow();

        return (int) Math.round(average);
    }

    private static int clamp(int score) {
        return Math.max(RuleConstants.MIN_SCORE, Math.min(RuleConstants.MAX_SCORE, score));
    }

    /**
     * 그날 가장 자주 붙은 문구. 새로 쓰지 않고 저장된 피드백의 message 를 그대로 쓴다 —
     * 9개 룰이 모두 짧은 라벨을 리터럴로 돌려주고 그 값이 기록돼 있다.
     */
    private static List<String> topMessages(List<SkinPlate> plates, FeedbackType type) {
        return topCounts(plates.stream()
                        .flatMap(plate -> plate.getFeedbacks().stream())
                        .filter(feedback -> feedback.getType() == type)
                        .map(SkinPlateFeedback::getMessage),
                TOP_MESSAGE_COUNT)
                .stream().map(Map.Entry::getKey).toList();
    }

    /**
     * 빈도 상위 limit 개를 (값, 횟수) 로 돌려준다. 빈도 내림차순, 같으면 사전순 —
     * 호출마다 순서가 달라지지 않게 고정한다.
     *
     * <p>주간이 "자주 먹은 음식"을 셀 때도 이 메서드를 쓴다. 같은 집계를 두 벌 두면
     * 한쪽만 정렬 규칙이 바뀌었을 때 일일과 주간의 목록 순서가 조용히 갈린다.
     */
    static List<Map.Entry<String, Long>> topCounts(Stream<String> values, int limit) {
        return values.collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                 .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .toList();
    }
}
