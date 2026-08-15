package com.skinplate.api.domain.insight.entity;

/**
 * 인사이트 주제 13종. (PRD §18.10)
 *
 * 제목과 오늘의 행동 문구를 여기 고정해 둔다 — 둘 다 AI 에게 맡기지 않는다.
 * 제목이 매번 달라지면 같은 사진에서 같은 화면이 안 나오고, 행동 문구는
 * 사용자가 실제로 따라 하는 문장이라 "물을 마시라"가 어느 날 "병원에 가라"가
 * 되어서는 안 된다. AI 몫은 description 한 줄뿐이다.
 *
 * 앞 5종은 측정(SkinMetrics)에서, 다음 4종은 자가 신고 고민에서, 마지막 4종은
 * 생활 습관에서만 진입한다 — {@link com.skinplate.api.domain.insight.service.InsightTopics}.
 */
public enum InsightCategory {

    // 측정 5종
    DRY         ("수분 관리",     "오늘은 보습 중심의 간단한 케어를 해보세요"),
    OILY        ("유분 관리",     "기름진 간식 대신 물이나 채소를 곁들여 보세요"),
    REDNESS     ("진정 관리",     "자극이 적은 진정 케어를 해보세요"),
    TROUBLE     ("트러블 관리",   "얼굴에 손이 가는 횟수를 줄이고 자극을 피해 보세요"),
    BARRIER_WEAK("장벽 관리",     "세안 뒤 3분 안에 보습을 마무리해 보세요"),

    // 자가 신고 4종 — 측정 지표로는 볼 수 없는 고민이다
    DARK_CIRCLE ("다크서클 관리", "잠들기 전 화면 보는 시간을 30분만 줄여 보세요"),
    PIGMENTATION("톤 관리",       "외출 30분 전에 자외선 차단제를 발라 보세요"),
    ELASTICITY  ("탄력 관리",     "단백질이 들어간 한 끼를 오늘 안에 챙겨 보세요"),
    PUFFINESS   ("붓기 관리",     "국물은 조금 남기고 짠 간식을 한 번 건너뛰어 보세요"),

    // 생활 습관 4종 — 나쁜 값일 때만 진입한다
    SLEEP       ("수면 관리",     "오늘은 7시간 이상 수면을 목표로 해보세요"),
    STRESS      ("스트레스 관리", "10분만 걸으며 숨을 고르는 시간을 가져 보세요"),
    EXERCISE    ("운동 습관",     "가볍게 20분만 몸을 움직여 보세요"),
    WATER       ("수분 섭취",     "물을 평소보다 한두 잔 더 챙겨 마셔보세요");

    private final String title;
    private final String actionTitle;

    InsightCategory(String title, String actionTitle) {
        this.title = title;
        this.actionTitle = actionTitle;
    }

    public String getTitle() { return title; }

    public String getActionTitle() { return actionTitle; }
}
