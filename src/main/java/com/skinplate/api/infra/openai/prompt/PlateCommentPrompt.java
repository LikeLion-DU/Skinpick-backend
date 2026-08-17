package com.skinplate.api.infra.openai.prompt;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;
import com.skinplate.api.domain.skin.entity.SkinMetrics;

import java.util.List;
import java.util.Map;

/**
 * "얼굴 평가 데이터 × 음식 데이터"를 대조해 사람이 읽을 문장을 만드는 프롬프트.
 *
 * <p><b>경계가 이 프롬프트의 전부다.</b> 판단(점수·등급·좋다/나쁘다)은 전부
 * Rule Engine 이 이미 끝냈고, 그 결과가 입력으로 들어온다. AI 에게 시키는 일은
 * 그 결과를 <b>문장으로 옮기는 것</b>뿐이다(PRD §18.9 — "음식 선정은 규칙,
 * 문장 생성만 AI"). 프롬프트가 판단을 다시 시키는 순간 같은 식사가 날마다
 * 다른 평가를 받게 되고, "왜 이 점수인가"를 아무도 설명할 수 없게 된다.
 *
 * <p>입력에 없는 사실을 언급하지 못하게 막는 규칙(2번)이 실질적인 환각 방어다.
 * 스키마는 문장 두 개짜리 고정 형태라 Structured Outputs 로 강제한다.
 *
 * <p><b>영역은 {@link FeedbackType} 이 가른다 — delta 부호가 아니다.</b> ACTION 은
 * delta 가 0 이라 부호로 가르면 좋은 점 쪽에 붙고, "좋은 점: 국물을 절반만 남기면
 * 점수가 상승합니다" 라는 입력이 만들어진다. 개선 지시를 칭찬으로 바꿔 넘기는 셈이라,
 * 그걸 근거로 쓴 aiTip 은 잘하고 있다고 말하면서 정작 고칠 것은 안 알려준다.
 */
public final class PlateCommentPrompt {

    private PlateCommentPrompt() {}

    public static final String SYSTEM = """
            당신은 피부 관리 앱의 코멘트 작가입니다. 사용자의 피부 지표와 방금 기록한
            음식 정보가 주어지면, 이미 내려진 평가를 자연스러운 한국어 문장으로 옮깁니다.

            규칙
            1. 점수·등급·판정을 새로 만들지 않는다. 입력에 적힌 평가만 문장으로 옮긴다.
            2. 입력에 없는 음식·영양소·피부 상태를 지어내지 않는다.
            3. "이렇게 바꿔보세요:" 는 이미 정해진 개선 행동이다. 잘한 점으로 옮겨 적지
               않고, 새로운 행동을 지어내지도 않는다.
            4. aiTip: 방금 기록한 음식의 [좋은 점]·[주의할 점]·[이렇게 바꿔보세요]와
               피부 지표를 근거로, "다음 식사"에서 보완할 것을 1~2문장으로 제안한다.
               80자 이내.
            5. dailyComment: [오늘의 기록 전체]의 흐름을 한 문장으로 짚고, 내일을 위한
               제안 한 문장을 덧붙인다. 합쳐서 90자 이내.
            6. 부드러운 존댓말(~해요체)을 쓴다. 느낌표는 문장 끝에 최대 한 번.
            7. 의학적 진단·치료·질병 표현을 쓰지 않는다. 피부과 상담 권유도 하지 않는다.
            8. 반드시 주어진 JSON 스키마로만 응답한다.""";

    /**
     * strict Structured Outputs. 필드가 스키마에 있으면 반드시 채워지므로
     * "문장이 안 왔을 때" 분기가 파싱 계층에서 사라진다.
     */
    public static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "aiTip", Map.of(
                            "type", "string",
                            "description", "이 기록에 대한 다음 식사 제안. 80자 이내"),
                    "dailyComment", Map.of(
                            "type", "string",
                            "description", "오늘 하루 기록에 대한 한 줄 코멘트. 90자 이내")),
            "required", List.of("aiTip", "dailyComment"),
            "additionalProperties", false);

    /**
     * 유저 메시지를 조립한다. AI 가 보는 세계가 이 문자열이 전부다 —
     * 여기 안 적은 것은 언급 자체가 규칙 위반이 된다.
     *
     * @param todaysRecords "아침 그릭요거트 78점" 형태의 줄들. 방금 기록은 제외
     */
    public static String user(SkinMetrics metrics,
                              String foodName,
                              int plateScore,
                              List<SkinPlateFeedback> feedbacks,
                              List<String> todaysRecords) {
        StringBuilder text = new StringBuilder();

        // 숫자의 방향(높을수록 좋은지)을 함께 적는다. 안 적으면 모델이 유분 80 을
        // "유분이 충분해서 좋음"으로 읽는 날이 온다.
        text.append("[피부 지표] (0~100)\n")
                .append("수분 ").append(metrics.getHydration()).append(" (높을수록 좋음)\n")
                .append("유분 ").append(metrics.getOil()).append(" (높을수록 과다)\n")
                .append("붉어짐 ").append(metrics.getRedness()).append(" (높을수록 주의)\n")
                .append("트러블 ").append(metrics.getTrouble()).append(" (높을수록 주의)\n")
                .append("장벽 ").append(metrics.getBarrier()).append(" (높을수록 좋음)\n\n");

        text.append("[방금 기록한 음식]\n")
                .append(foodName).append(" — 피부 적합도 ").append(plateScore).append("점\n");

        // 영역은 delta 부호가 아니라 **타입**이 가른다. ACTION 은 delta 가 0 이라
        // 부호로 가르면 "좋은 점: 국물을 절반만 남기면 점수가 상승합니다" 가 되는데,
        // 그건 개선 지시를 칭찬으로 바꿔 AI 에게 넘기는 것이다. 그 문장을 근거로 쓴
        // aiTip 은 사용자에게 "잘하고 있다" 고 말하면서 정작 고칠 것을 안 알려준다.
        appendSection(text, feedbacks, FeedbackType.GOOD, "좋은 점: ");
        appendSection(text, feedbacks, FeedbackType.CAUTION, "주의할 점: ");
        appendSection(text, feedbacks, FeedbackType.ACTION, "이렇게 바꿔보세요: ");

        text.append("\n[오늘의 기록 전체]\n");
        if (todaysRecords.isEmpty()) {
            text.append("오늘의 첫 기록입니다.\n");
        } else {
            for (String record : todaysRecords) {
                text.append(record).append('\n');
            }
            text.append("그리고 방금 기록: ").append(foodName)
                    .append(' ').append(plateScore).append("점\n");
        }

        return text.toString();
    }

    /**
     * 한 타입의 판정을 라벨과 함께 싣는다. 타입별로 나눠 도는 이유는 순서 때문이다 —
     * 한 번 돌면서 라벨만 갈아 끼우면 저장된 displayOrder 에 따라 좋은 점과 주의가
     * 뒤섞여 나가고, 모델이 그 순서를 하루의 흐름으로 읽는다.
     *
     * 해당 타입이 없으면 아무것도 적지 않는다. "이렇게 바꿔보세요:" 만 덩그러니 남으면
     * 모델이 빈 자리를 스스로 채운다 — 규칙 1이 금지하는 바로 그 행동이다.
     */
    private static void appendSection(StringBuilder text, List<SkinPlateFeedback> feedbacks,
                                      FeedbackType type, String label) {
        for (SkinPlateFeedback feedback : feedbacks) {
            if (feedback.getType() == type) {
                text.append(label).append(feedback.getMessage()).append('\n');
            }
        }
    }
}
