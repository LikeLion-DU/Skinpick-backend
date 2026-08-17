package com.skinplate.api.infra.openai.prompt;

import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyScoreDto;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.report.dto.WeeklyReportResponse;

import java.util.List;
import java.util.Map;

/**
 * 주간 리포트의 문장을 만드는 프롬프트.
 *
 * <p><b>PlateCommentPrompt · SkinInsightPrompt 와 같은 경계다.</b> 평균 점수도 BEST DAY 도
 * 영양 상태도 서버가 이미 계산해 두었고, AI 가 하는 일은 그 표를 사람 말로 옮기는 것뿐이다.
 *
 * <p>입력이 <b>집계된 숫자뿐</b>인 것도 의도다. 한 주치 음식 기록 원문을 다시 실어 보내면
 * 토큰이 폭증하고, 무엇보다 AI 가 그 원문으로 자기만의 주간 평가를 다시 하게 된다 —
 * 그러면 화면의 76점과 문장의 어조가 따로 논다.
 */
public final class WeeklyReportPrompt {

    private WeeklyReportPrompt() {}

    public static final String SYSTEM = """
            당신은 피부 관리 앱의 주간 리포트 작가입니다. 이미 집계된 한 주의 식단 데이터가
            주어지면, 그 결과를 자연스러운 한국어 문장으로 옮깁니다.

            규칙
            1. 점수·등급·평균을 새로 만들거나 다시 계산하지 않는다. 입력에 적힌 숫자만 쓴다.
            2. 입력에 없는 음식·영양소·피부 상태를 지어내지 않는다.
            3. 인과를 확정하지 않는다. "~때문에 ~해졌다"로 쓰지 말고
               "~가 함께 기록됐어요", "~도 영향을 줄 수 있어요"처럼 쓴다.
            4. goodPoint: 이번 주 잘한 점 한 가지를 1~2문장으로 짚는다. 80자 이내.
            5. improvePoint: 개선하면 좋을 점 한 가지를 1~2문장으로 짚는다. 80자 이내.
            6. habit: 반복해서 나타난 식습관을 한 문장으로 짚는다. 80자 이내.
            7. nextWeek: 다음 주에 해볼 만한 구체적인 행동 하나를 제안한다. 80자 이내.
            8. 부드러운 존댓말(~해요체)을 쓴다. 느낌표는 문장 끝에 최대 한 번.
            9. 의학적 진단·치료·질병 표현을 쓰지 않는다. 피부과 상담 권유도 하지 않는다.
            10. 반드시 주어진 JSON 스키마로만 응답한다.""";

    /** strict Structured Outputs. 네 문장이 모두 필수라 "문장이 안 왔을 때" 분기가 없다. */
    public static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "goodPoint", Map.of("type", "string",
                            "description", "이번 주 잘한 점. 80자 이내"),
                    "improvePoint", Map.of("type", "string",
                            "description", "개선할 점. 80자 이내"),
                    "habit", Map.of("type", "string",
                            "description", "반복적으로 나타난 식습관. 80자 이내"),
                    "nextWeek", Map.of("type", "string",
                            "description", "다음 주 추천 행동. 80자 이내")),
            "required", List.of("goodPoint", "improvePoint", "habit", "nextWeek"),
            "additionalProperties", false);

    /**
     * 유저 메시지를 조립한다. AI 가 보는 세계가 이 문자열이 전부다.
     *
     * @param topGoods    한 주 동안 자주 붙은 좋은 점 문구 (문구, 횟수)
     * @param topCautions 자주 붙은 주의 문구 (문구, 횟수)
     * @param topFoods    자주 기록된 음식 (이름, 횟수)
     */
    public static String user(WeeklyReportResponse report,
                             List<Map.Entry<String, Long>> topGoods,
                             List<Map.Entry<String, Long>> topCautions,
                             List<Map.Entry<String, Long>> topFoods) {
        StringBuilder text = new StringBuilder();

        text.append("[기간] ").append(report.from()).append(" ~ ").append(report.to())
                .append(" (").append(report.totalDays()).append("일 중 ")
                .append(report.recordedDays()).append("일 기록 · 총 ")
                .append(report.recordCount()).append("끼)\n\n");

        text.append("[일별 피부 식단 점수] (0~100 · 높을수록 좋음)\n");
        for (DailyScoreDto day : report.dailyScores()) {
            text.append(day.date()).append(' ').append(day.dailyScore())
                    .append(" (").append(day.grade()).append(")\n");
        }
        text.append("평균 ").append(report.averageDailyScore())
                .append(" (").append(report.grade()).append(")\n");
        if (report.bestDay() != null) {
            text.append("가장 높은 날 ").append(report.bestDay().date())
                    .append(' ').append(report.bestDay().dailyScore()).append("점 · ")
                    .append("가장 낮은 날 ").append(report.worstDay().date())
                    .append(' ').append(report.worstDay().dailyScore()).append("점\n");
        }

        // 하루 평균이라고 못 박는다. "칼로리 1450"만 적으면 모델이 한 주 합계로 읽고
        // "일주일에 1450kcal 밖에 안 드셨네요" 같은 문장을 쓴다.
        text.append("\n[하루 평균 영양] (기록된 끼니만 합산 · 기준은 성인 1일 참고량)\n");
        for (NutritionItemDto item : report.nutrition()) {
            text.append(item.label()).append(' ').append(item.amount()).append(item.unit())
                    .append(" (기준 ").append(item.target()).append(item.unit())
                    .append("의 ").append(item.percent()).append("%, ")
                    .append(statusLabel(item)).append(")\n");
        }

        if (!report.concerns().isEmpty()) {
            text.append("\n[고민별 식단 점수] (0~100 · 높을수록 그 고민에 유리)\n");
            for (ConcernScoreDto concern : report.concerns()) {
                text.append(concern.label()).append(' ').append(concern.score())
                        .append(" (").append(concern.status()).append(')');
                if (concern.change() != null) {
                    text.append(", 첫 기록일 대비 ")
                            .append(concern.change() >= 0 ? "+" : "").append(concern.change());
                }
                text.append('\n');
            }
        }

        // 단위를 제목에 적는다. 앞의 둘은 "그 문구가 뜬 날 수"이고 음식만 "끼니 수"다 —
        // 안 적으면 모델이 셋을 같은 단위로 읽고 "라면을 5일 먹었다"를 지어낸다.
        appendCounts(text, "[자주 기록된 좋은 점] (등장한 날 수)", topGoods);
        appendCounts(text, "[자주 기록된 주의할 점] (등장한 날 수)", topCautions);
        appendCounts(text, "[자주 먹은 음식] (끼니 수)", topFoods);

        return text.toString();
    }

    private static void appendCounts(StringBuilder text, String title,
                                     List<Map.Entry<String, Long>> counts) {
        if (counts.isEmpty()) return;

        text.append('\n').append(title).append('\n');
        for (Map.Entry<String, Long> entry : counts) {
            text.append(entry.getKey()).append(" ×").append(entry.getValue()).append('\n');
        }
    }

    /**
     * 방향을 한국어로 적는다. LOW/HIGH 만 넘기면 모델이 단백질 LOW 와 나트륨 LOW 를
     * 같은 뜻으로 읽는다 — 하나는 아쉬운 것이고 하나는 잘한 것이다.
     */
    private static String statusLabel(NutritionItemDto item) {
        return switch (item.status()) {
            case LOW -> item.higherIsWorse() ? "여유 있음" : "부족";
            case HIGH -> item.higherIsWorse() ? "과다" : "충분";
            case NORMAL -> "적정";
        };
    }
}
