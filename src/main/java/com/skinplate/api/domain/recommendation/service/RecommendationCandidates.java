package com.skinplate.api.domain.recommendation.service;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 취약 항목 → 후보 음식 매핑. (PRD §18.9)
 *
 * 음식 선정은 규칙, 문장 생성만 AI.
 * LLM이 매번 다른 음식을 추천하면 데모마다 결과가 달라져 설명할 수 없다.
 */
public final class RecommendationCandidates {

    private RecommendationCandidates() {}

    /** 추천 축. 앞 5개는 측정(SkinMetrics), 뒤 7개는 자가 신고 고민·습관에서만 진입한다. */
    public enum Concern {
        DRY, REDNESS, TROUBLE, OILY, BARRIER_WEAK,
        DARK_CIRCLE, PIGMENTATION, ELASTICITY, PUFFINESS,
        SLEEP_LACK, STRESS_HIGH, EXERCISE_NONE
    }

    public record Candidates(List<String> recommend, List<String> avoid) {}

    private static final Map<Concern, Candidates> TABLE = Map.ofEntries(
            Map.entry(Concern.DRY,          new Candidates(List.of("연어", "아보카도", "오이", "견과류"),
                                                           List.of("커피", "술"))),
            Map.entry(Concern.REDNESS,      new Candidates(List.of("브로콜리", "녹차", "토마토"),
                                                           List.of("매운 음식", "술"))),
            Map.entry(Concern.TROUBLE,      new Candidates(List.of("키위", "고구마", "견과류"),
                                                           List.of("탄산음료", "초콜릿", "튀김"))),
            Map.entry(Concern.OILY,         new Candidates(List.of("채소", "두부", "흰살생선"),
                                                           List.of("튀김", "라면", "패스트푸드"))),
            Map.entry(Concern.BARRIER_WEAK, new Candidates(List.of("연어", "달걀", "아몬드"),
                                                           List.of("인스턴트", "가공육"))),
            // 자가 신고 전용 축 — 측정 지표로는 볼 수 없는 고민이다
            Map.entry(Concern.DARK_CIRCLE,  new Candidates(List.of("시금치", "달걀"),
                                                           List.of("술"))),
            Map.entry(Concern.PIGMENTATION, new Candidates(List.of("토마토", "키위", "파프리카"),
                                                           List.of("술"))),
            Map.entry(Concern.ELASTICITY,   new Candidates(List.of("닭가슴살", "달걀", "베리류"),
                                                           List.of("탄산음료"))),
            Map.entry(Concern.PUFFINESS,    new Candidates(List.of("오이", "바나나"),
                                                           List.of("라면", "가공육"))),
            // 습관 축 — 나쁜 값일 때만 트리거된다
            Map.entry(Concern.SLEEP_LACK,   new Candidates(List.of("바나나", "우유"),
                                                           List.of("커피", "술"))),
            Map.entry(Concern.STRESS_HIGH,  new Candidates(List.of("견과류", "녹차", "연어"),
                                                           List.of("커피"))),
            Map.entry(Concern.EXERCISE_NONE, new Candidates(List.of("두부", "달걀", "닭가슴살"),
                                                            List.of("패스트푸드"))));

    public static Candidates of(Concern concern) {
        return TABLE.get(concern);
    }

    /**
     * <b>실제로 취약한</b> 항목만 심각한 순으로 최대 N개 뽑는다. 없으면 빈 목록이다.
     *
     * 판정은 {@link SkinMetrics} 의 판정자를 그대로 쓴다. 여기서 임계값을 다시 적으면
     * 같은 뜻의 숫자가 두 곳에 생기고, Rule Engine 은 "건조하지 않다"고 보는 지표를
     * 추천만 "건조하다"고 보는 날이 온다.
     *
     * 거르지 않으면 <b>피부가 멀쩡해도 상위 두 개가 뽑힌다.</b> 모든 지표가 좋은
     * 사용자에게 "장벽 회복을 위해 연어를 드세요"가 뜨는데, 심사위원이 본인 얼굴로
     * 찍어 보는 순간이 정확히 그 경우다.
     */
    public static List<Concern> topConcerns(SkinMetrics metrics, int count) {
        record Scored(Concern concern, boolean present, int severity) {}

        return List.of(
                        new Scored(Concern.DRY,          metrics.isDry(),         100 - metrics.getHydration()),
                        new Scored(Concern.BARRIER_WEAK, metrics.isBarrierWeak(), 100 - metrics.getBarrier()),
                        new Scored(Concern.OILY,         metrics.isOily(),        metrics.getOil()),
                        new Scored(Concern.REDNESS,      metrics.hasRedness(),    metrics.getRedness()),
                        new Scored(Concern.TROUBLE,      metrics.hasTrouble(),    metrics.getTrouble()))
                .stream()
                .filter(Scored::present)
                // 동점일 때 순서가 흔들리면 같은 지표에 다른 추천이 나온다.
                // 재현성이 이 테이블의 존재 이유이므로 이름으로 한 번 더 고정한다.
                .sorted(Comparator.comparingInt(Scored::severity).reversed()
                                  .thenComparing(scored -> scored.concern().name()))
                .limit(count)
                .map(Scored::concern)
                .toList();
    }

    /**
     * 음식별 추천 문구. (PRD §14.3 ⑧)
     *
     * 항목별로 한 문장씩 두면 같은 취약 항목에서 나온 음식들이 <b>글자까지 똑같은
     * 문장</b>을 달고 화면에 줄줄이 뜬다 — 시연 지표에서는 추천 7장에 문장이 2종뿐이었다.
     * S08 은 영상에 나가는 화면이다.
     *
     * AI 로 문장을 만들지 않는다. §18.9 는 문장 생성을 AI 몫으로 뒀지만 그건 G4
     * 축소 경로("추천을 정적 문구로 대체")를 두고 한 설계이고, 지금은 그 경로를 쓴다.
     * 무대에서 같은 사진에 같은 문장이 나오는 쪽이 자연스러운 문장보다 중요하다.
     *
     * 음식 이름을 바꾸면 여기도 같이 바꾼다 — 빠지면 문구 없이 이름만 뜬다.
     */
    private static final Map<String, String> REASONS = Map.ofEntries(
            // 추천
            Map.entry("연어",      "오메가3와 단백질이 들어 있어 피부 장벽을 채우는 데 좋습니다."),
            Map.entry("아보카도",  "불포화지방과 비타민E가 수분이 빠져나가는 것을 붙잡아 줍니다."),
            Map.entry("견과류",    "비타민E와 좋은 지방이 들어 있어 조금씩 자주 먹기 좋습니다."),
            Map.entry("브로콜리",  "항산화 성분이 풍부한 채소라 자극받은 피부에 부담이 적습니다."),
            Map.entry("녹차",      "폴리페놀이 들어 있고 카페인이 커피보다 적습니다."),
            Map.entry("토마토",    "라이코펜이 들어 있어 붉어진 피부를 진정시키는 데 도움이 됩니다."),
            Map.entry("키위",      "비타민C가 많아 피부 컨디션을 관리하기에 좋습니다."),
            Map.entry("고구마",    "식이섬유와 베타카로틴이 함께 들어 있습니다."),
            Map.entry("채소",      "기름기가 적어 유분이 많은 날에도 부담이 없습니다."),
            Map.entry("두부",      "지방은 적고 단백질은 챙길 수 있습니다."),
            Map.entry("흰살생선",  "기름기가 적은 단백질이라 유분이 많을 때 알맞습니다."),
            Map.entry("달걀",      "단백질과 아미노산이 고루 들어 있습니다."),
            Map.entry("아몬드",    "비타민E가 많아 장벽이 약할 때 곁들이기 좋습니다."),
            // 주의
            Map.entry("술",        "탈수를 부르고 혈관을 확장시켜 붉은기를 키울 수 있습니다."),
            Map.entry("매운 음식",  "캡사이신이 혈관을 확장시켜 홍조를 더 붉게 만들 수 있습니다."),
            Map.entry("초콜릿",    "당과 지방이 함께 많아 트러블이 있을 때 부담이 됩니다."),
            Map.entry("튀김",      "튀김 기름이 유분과 트러블 양쪽을 자극할 수 있습니다."),
            Map.entry("패스트푸드", "기름기와 나트륨이 함께 높습니다."),
            Map.entry("인스턴트",  "가공도가 높아 장벽 회복에 도움이 되지 않습니다."),
            // 교체 (기존 문구가 새 맥락에서 어색해지는 것들)
            Map.entry("오이",      "수분이 대부분이라 물기를 채우고 붓기를 가라앉히는 데 좋습니다."),
            Map.entry("커피",      "카페인이 수분을 빼앗고 잠들기도 어렵게 만듭니다."),
            Map.entry("탄산음료",  "당류가 많아 트러블과 탄력 저하를 함께 부추길 수 있습니다."),
            Map.entry("라면",      "나트륨이 높아 붓기를 부르고 수분을 빼앗아 갑니다."),
            Map.entry("가공육",    "나트륨과 첨가물이 많아 붓기와 장벽 회복 모두에 부담이 됩니다."),
            // 신규
            Map.entry("시금치",    "철분과 루테인이 들어 있어 눈가 그늘 관리에 곁들이기 좋습니다."),
            Map.entry("파프리카",  "비타민C가 풍부해 칙칙해진 톤을 관리하는 데 도움이 됩니다."),
            Map.entry("닭가슴살",  "단백질이 풍부해 피부 탄력의 재료를 채워 줍니다."),
            Map.entry("베리류",    "안토시아닌 같은 항산화 성분이 탄력 저하를 늦추는 데 좋습니다."),
            Map.entry("바나나",    "칼륨이 나트륨 배출을 도와 붓기를 가라앉히고 저녁 간식으로도 부담이 없습니다."),
            Map.entry("우유",      "트립토판이 들어 있어 잠들기 어려운 날 저녁에 알맞습니다."));

    /** 표에 없는 음식이면 이름만 남긴다 — 문구가 없다고 추천이 사라지면 안 된다. */
    public static String reasonOf(String foodName) {
        return REASONS.getOrDefault(foodName, "");
    }
}
