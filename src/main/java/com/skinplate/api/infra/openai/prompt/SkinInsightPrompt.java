package com.skinplate.api.infra.openai.prompt;

import com.skinplate.api.domain.insight.entity.InsightCategory;
import com.skinplate.api.domain.insight.service.InsightTopics.Topic;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.AppUser;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 피부 지표 × 생활 습관을 대조해 개인화 인사이트 문장을 만드는 프롬프트.
 *
 * <p><b>PlateCommentPrompt 와 같은 경계다.</b> 어떤 주제를 다룰지, 어느 것이 급한지는
 * {@link com.skinplate.api.domain.insight.service.InsightTopics} 가 이미 정했고 그 결과가
 * 입력으로 들어온다. AI 가 하는 일은 주제마다 문장 하나를 붙이는 것뿐이다.
 *
 * <p>여기에만 있는 규칙이 하나 더 있다 — <b>인과를 확정하지 않는다.</b> "수면이 부족해서
 * 건조하다"는 이 앱이 증명할 수 없는 문장이다. 지표와 습관은 <b>함께 기록된 것</b>일 뿐이고,
 * 그 선을 넘는 순간 자가 신고 한 줄로 진단을 내리는 앱이 된다.
 */
public final class SkinInsightPrompt {

    private SkinInsightPrompt() {}

    public static final String SYSTEM = """
            당신은 피부 관리 앱의 인사이트 작가입니다. 이미 선정된 주제와 사용자의 피부 지표·
            생활 상태가 주어지면, 각 주제를 자연스러운 한국어 문장으로 옮깁니다.

            규칙
            1. 점수·판정·우선순위를 새로 만들지 않는다. 입력에 적힌 주제만 다룬다.
               주제를 추가하거나 빼지 않는다.
            2. 입력에 없는 사실·습관·지표를 지어내지 않는다.
            3. 인과를 확정하지 않는다. "~때문에 ~하다"로 쓰지 말고,
               "~가 함께 기록되고 있어요", "~도 영향을 줄 수 있어요"처럼 쓴다.
            4. 의학적 진단·치료·질병 표현을 쓰지 않는다. 피부과 상담 권유도 하지 않는다.
            5. summary 는 80자 이내로 오늘의 피부 상태를 한 문장으로 짚는다.
               주제별 description 은 150자 이내로, 그 주제 하나만 다룬다.
            6. 부드러운 존댓말(~해요체)을 쓴다. 느낌표는 문장 끝에 최대 한 번.
            7. 반드시 주어진 JSON 스키마로만 응답한다.""";

    /**
     * strict Structured Outputs. 중첩 object 에도 required · additionalProperties 를 적어야
     * strict 모드가 통과한다 — 빠뜨리면 400 이고, 그 400 은 스키마가 아니라 호출 실패로 보인다.
     *
     * category 를 enum 으로 못 박는다. 자유 문자열이면 AI 가 "수면관리" 같은 값을 돌려주고
     * 서비스가 매칭에 실패해 인사이트 전체가 날아간다.
     */
    public static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of(
                            "type", "string",
                            "description", "오늘의 피부 상태 한 문장. 80자 이내"),
                    "topics", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "category", Map.of(
                                                    "type", "string",
                                                    "enum", Arrays.stream(InsightCategory.values())
                                                            .map(Enum::name).toList()),
                                            "description", Map.of(
                                                    "type", "string",
                                                    "description", "이 주제에 대한 설명 문장. 150자 이내")),
                                    "required", List.of("category", "description"),
                                    "additionalProperties", false))),
            "required", List.of("summary", "topics"),
            "additionalProperties", false);

    /**
     * 유저 메시지를 조립한다. AI 가 보는 세계가 이 문자열이 전부다 —
     * 여기 안 적은 것은 언급 자체가 규칙 위반이 된다.
     *
     * <b>이름·이메일 같은 개인 식별 정보는 넣지 않는다.</b> 문장에 쓸모가 없고,
     * 외부 API 로 나가는 값이다.
     *
     * @param previous 직전 피부 분석. 없으면 변화량 블록이 통째로 빠진다 —
     *                 "변화 없음"으로 적으면 첫 분석인데 그대로라고 읽는다
     */
    public static String user(SkinAnalysis analysis,
                              SkinAnalysis previous,
                              AppUser user,
                              List<Topic> topics) {
        SkinMetrics metrics = analysis.getMetrics();
        StringBuilder text = new StringBuilder();

        // 숫자의 방향(높을수록 좋은지)을 함께 적는다. 안 적으면 모델이 유분 80 을
        // "유분이 충분해서 좋음"으로 읽는 날이 온다. (PlateCommentPrompt 와 같은 이유)
        text.append("[피부 지표] (0~100)\n")
                .append("수분 ").append(metrics.getHydration()).append(" (높을수록 좋음)\n")
                .append("유분 ").append(metrics.getOil()).append(" (높을수록 과다)\n")
                .append("붉어짐 ").append(metrics.getRedness()).append(" (높을수록 주의)\n")
                .append("트러블 ").append(metrics.getTrouble()).append(" (높을수록 주의)\n")
                .append("장벽 ").append(metrics.getBarrier()).append(" (높을수록 좋음)\n")
                .append("종합 점수 ").append(analysis.getSkinScore()).append(" (높을수록 좋음)\n\n");

        if (previous != null) {
            SkinMetrics before = previous.getMetrics();
            text.append("[직전 분석 대비 변화]\n")
                    .append(delta("수분", metrics.getHydration(), before.getHydration()))
                    .append(delta("유분", metrics.getOil(), before.getOil()))
                    .append(delta("붉어짐", metrics.getRedness(), before.getRedness()))
                    .append(delta("트러블", metrics.getTrouble(), before.getTrouble()))
                    .append(delta("장벽", metrics.getBarrier(), before.getBarrier()))
                    .append(delta("종합 점수", analysis.getSkinScore(), previous.getSkinScore()))
                    .append("촬영 조명·각도 등 측정 환경 차이가 있을 수 있음\n\n");
        }

        // 미입력 항목은 줄 자체를 넣지 않는다. "미선택"이라고 적으면 AI 가 그 사실을
        // 문장으로 만든다 — 사용자가 건너뛴 질문을 화면에서 다시 지적하는 꼴이다.
        StringBuilder habits = new StringBuilder();
        if (user.getSleepPattern() != null) {
            habits.append("수면: ").append(user.getSleepPattern().getLabel()).append('\n');
        }
        if (user.getStressLevel() != null) {
            habits.append("스트레스: ").append(user.getStressLevel().getLabel()).append('\n');
        }
        if (user.getExerciseHabit() != null) {
            habits.append("운동: ").append(user.getExerciseHabit().getLabel()).append('\n');
        }
        if (user.getWaterIntake() != null) {
            habits.append("수분 섭취: ").append(user.getWaterIntake().getLabel()).append('\n');
        }
        if (!habits.isEmpty()) {
            text.append("[자가 신고 생활 상태]\n").append(habits).append('\n');
        }

        text.append("[다룰 주제] (이 순서가 우선순위다 · 이 목록만 다룬다)\n");
        for (Topic topic : topics) {
            text.append(topic.category().name())
                    .append(" (").append(topic.category().getTitle()).append(") — ")
                    .append(topic.reason()).append('\n');
        }

        return text.toString();
    }

    /** 부호를 반드시 붙인다. "수분 9"는 값인지 변화량인지 구분이 안 된다. */
    private static String delta(String name, int current, int before) {
        int change = current - before;
        return name + " " + (change >= 0 ? "+" : "") + change + "\n";
    }
}
