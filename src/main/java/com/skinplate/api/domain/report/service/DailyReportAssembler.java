package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.IngredientTag;
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

    /** 고민 카드 하나에 붙는 태그 수. 시안이 카드마다 두 개를 그린다. */
    private static final int CONCERN_TAG_COUNT = 2;

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
                skinNutrients(sorted),
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
     *
     * <p><b>섭취량 환산은 여기서만 한다.</b> 저장된 영양값은 1인분 기준이고(점수의
     * 재현성이 그 위에 서 있다), 리포트의 "얼마나 먹었나"만 portionSize 계수를 곱한다.
     * V7 이전 행·UNKNOWN 은 계수 1.0 이라 기존 합계와 동일하다.
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
            BigDecimal portion = plate.getFoodAnalysis().getTraits().getPortionSize().getFactor();
            calories = calories.add(BigDecimal.valueOf(value.getCaloriesKcal()).multiply(portion));
            carb     = carb.add(value.getCarbG().multiply(portion));
            protein  = protein.add(value.getProteinG().multiply(portion));
            fat      = fat.add(value.getFatG().multiply(portion));
            sodium   = sodium.add(BigDecimal.valueOf(value.getSodiumMg()).multiply(portion));
            sugar    = sugar.add(value.getSugarG().multiply(portion));
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
     * 피부 영양 포인트 3종. 시안이 영양 밸런스와 다른 카드로 그리는 묶음이다.
     *
     * <p><b>표준 음식표에 매칭된 끼니만 센다.</b> 비타민C·아연은 실측 컬럼(V9)이지만
     * 값이 없으면 0 으로 저장되고(그 규약이 룰에서는 "모르면 발동 안 함"으로 안전하다),
     * AI 추정만 있는 음식은 애초에 이 다섯 컬럼이 전부 0 이다. 그 0 을 합계에 넣으면
     * 화면이 "비타민C 부족"이라고 <b>단정</b>하는데 사실은 재지 못한 것이다.
     *
     * <p>매칭된 끼니가 하나도 없는 날은 두 항목을 {@code unmeasured} 로 낸다 — 앱이
     * 상태어 자리를 비우고 회색으로 그린다. 배열에서 빼지는 않는다(타일 수가 흔들린다).
     *
     * <p>오메가3만 셈이 다르다 — 양이 아니라 <b>OMEGA3 태그가 붙은 끼니 수</b>다.
     * 이유는 {@link NutrientType#OMEGA3} 에 적었다. 태그는 매칭 여부와 무관하게 실제
     * 관찰값이라 매칭된 끼니가 없어도 셀 수 있다.
     */
    private static List<NutritionItemDto> skinNutrients(List<SkinPlate> plates) {
        BigDecimal vitaminC = BigDecimal.ZERO;
        BigDecimal zinc     = BigDecimal.ZERO;
        int measuredMeals   = 0;
        int omega3Meals     = 0;

        for (SkinPlate plate : plates) {
            FoodAnalysis food = plate.getFoodAnalysis();

            if (hasOmega3(food)) omega3Meals++;

            if (!food.isStandardMatched()) continue;

            measuredMeals++;
            BigDecimal portion = food.getTraits().getPortionSize().getFactor();
            vitaminC = vitaminC.add(food.getNutrition().getVitaminCMg().multiply(portion));
            zinc     = zinc.add(food.getNutrition().getZincMg().multiply(portion));
        }

        return List.of(
                measuredMeals == 0
                        ? NutritionItemDto.unmeasured(NutrientType.VITAMIN_C)
                        : NutritionItemDto.of(NutrientType.VITAMIN_C, vitaminC),
                NutritionItemDto.of(NutrientType.OMEGA3, BigDecimal.valueOf(omega3Meals)),
                measuredMeals == 0
                        ? NutritionItemDto.unmeasured(NutrientType.ZINC)
                        : NutritionItemDto.of(NutrientType.ZINC, zinc));
    }

    /** 재료 태그에 OMEGA3 가 있는가. 표준표에서 온 태그든 AI 태그든 실제 관찰값이다. */
    private static boolean hasOmega3(FoodAnalysis food) {
        return food.getIngredients().stream()
                .anyMatch(ingredient -> ingredient.getTag() == IngredientTag.OMEGA3);
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
                .map(concern -> ConcernScoreDto.of(concern, concernScore(plates, concern), null,
                        concernMessage(plates, concern), concernTags(plates, concern)))
                .toList();
    }

    /**
     * 그 고민에 관해 <b>가장 크게 움직인 룰의 이유 문장</b>. 없으면 null 이라 키가 빠진다.
     *
     * <p><b>새 문장을 쓰지 않는다.</b> 룰이 판정할 때 만들어 저장해 둔 {@code reason} 을
     * 그대로 고른다(V8). 그래서 고민 카드의 문장과 음식 결과 화면의 문장이 같은 말을
     * 하고, AI 를 부르지 않으므로 같은 기록은 언제 열어도 같은 문장이다.
     *
     * <p>고르는 기준은 |{@code scoreDelta}| 다 — 그 고민의 점수를 실제로 가장 많이
     * 움직인 항목이 설명으로도 맞다. 동점이면 문구 사전순으로 고정한다(같은 날을
     * 두 번 열었을 때 문장이 바뀌지 않아야 한다).
     *
     * <p>V8 이전 기록은 {@code reason} 이 없다. 그때는 null 이고, 화면은 태그만 그린다.
     */
    private static String concernMessage(List<SkinPlate> plates, SkinConcern concern) {
        return relatedFeedbacks(plates, concern)
                .filter(feedback -> feedback.getReason() != null
                        && !feedback.getReason().isBlank())
                .max(Comparator.comparingInt((SkinPlateFeedback feedback) ->
                                Math.abs(feedback.getScoreDelta()))
                        .thenComparing(Comparator.comparing(SkinPlateFeedback::getReason).reversed()))
                .map(SkinPlateFeedback::getReason)
                .orElse(null);
    }

    /**
     * 그 고민에 걸린 룰의 짧은 라벨들. 시안의 해시태그 칩 자리다.
     *
     * <p>{@code message} 와 같은 출처(저장된 피드백)를 쓰되 {@code message} 필드를 본다 —
     * 룰이 돌려주는 짧은 리터럴("나트륨 과다" · "단백질 충분")이라 칩에 그대로 들어간다.
     * 빈도순으로 두 개까지다(시안이 카드마다 두 개를 그린다).
     */
    private static List<String> concernTags(List<SkinPlate> plates, SkinConcern concern) {
        return topCounts(relatedFeedbacks(plates, concern).map(SkinPlateFeedback::getMessage),
                CONCERN_TAG_COUNT).stream().map(Map.Entry::getKey).toList();
    }

    /**
     * 그 고민의 룰 코드에 해당하는 피드백만. {@code concernScore} 와 <b>같은 필터</b>를
     * 쓴다 — 점수를 만든 근거와 화면에 적히는 근거가 갈리면 카드가 자기 숫자를 설명하지
     * 못한다. ACTION 행과 rule_code 가 NULL 인 행을 거르는 이유도 그쪽과 같다.
     */
    private static Stream<SkinPlateFeedback> relatedFeedbacks(List<SkinPlate> plates,
                                                              SkinConcern concern) {
        Set<String> ruleCodes = ConcernRules.of(concern);

        return plates.stream()
                .flatMap(plate -> plate.getFeedbacks().stream())
                .filter(feedback -> feedback.getType() != FeedbackType.ACTION)
                .filter(feedback -> feedback.getRuleCode() != null)
                .filter(feedback -> ruleCodes.contains(feedback.getRuleCode()));
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
