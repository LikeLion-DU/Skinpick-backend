package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.user.entity.SkinConcern;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자가 신고 고민 ↔ 룰 코드 매핑. 고민별 점수가 이 표 하나에서 나온다.
 *
 * <p><b>새 점수를 만들지 않는다.</b> 이미 저장된 피드백의 {@code score_delta} 중
 * 그 고민과 관련된 룰만 골라 다시 합칠 뿐이다. 그래서 룰을 추가하면 여기 한 줄만
 * 늘리면 되고, 임계값을 바꾸면 고민 점수도 자동으로 따라간다.
 *
 * <p><b>⚠️ 이 표를 바꾸면 과거 일일·주간 리포트의 고민 점수도 함께 바뀐다.</b>
 * DB 에 확정돼 있는 것은 룰별 {@code score_delta} 이고, "어느 룰이 어느 고민에
 * 속하는가"는 이 코드에만 있기 때문이다. 알고 택했으며 스냅샷 테이블을 두지 않는다
 * (PRD §18.11):
 * <ol>
 *   <li>고민은 사용자가 언제든 바꾼다({@code PATCH /auth/me}). 기록 저장 시점에
 *       스냅샷을 뜨면 나중에 고민을 추가한 사용자는 과거 날짜에 그 고민의 행이 없어
 *       리포트가 뚫리고, 조회 시점에 뜨면 그건 확정이 아니라 캐시다 — 어느 쪽도
 *       "확정"이 되지 않는다.</li>
 *   <li>확정이 필요한 것은 이미 행으로 확정돼 있다. 이 표를 바꿔도 일일 종합 점수·
 *       영양·기록 수는 미동도 하지 않는다. 움직이는 것은 고민 점수 하나뿐이다.</li>
 *   <li>룰 자체(R01~R09)를 바꿀 계획이 08-21 전에 없다. 바꾼다면 그때 이 표를 함께
 *       고치는 것이 정상 경로다.</li>
 * </ol>
 *
 * <p>가점 룰을 반드시 함께 넣는다. 감점 룰만 매달면 그 고민 점수는 기준선(70)에서
 * 내려가기만 하고 절대 오르지 않아, 잘 챙긴 날에도 화면이 좋아지지 않는다.
 *
 * <p><b>DARK_CIRCLE 은 비어 있다.</b> 다크서클에 대응하는 식단 룰이 없다 —
 * 인사이트에서도 이 고민의 행동 문구는 화면 보는 시간이지 음식이 아니다.
 * 억지로 나트륨에 붙이면 "짜게 먹어서 다크서클"이라는, 이 앱이 증명할 수 없는
 * 말을 숫자로 하게 된다. 매핑이 빈 고민은 응답에서 통째로 빠진다.
 */
public final class ConcernRules {

    private ConcernRules() {}

    private static final Map<SkinConcern, Set<String>> RULES = Map.of(
            SkinConcern.ACNE,         Set.of("R03", "R07", "R09"),  // 당류·튀김 / 발효식품
            SkinConcern.REDNESS,      Set.of("R02", "R06"),         // 매운맛 / 항산화
            SkinConcern.DRYNESS,      Set.of("R01", "R08", "R04"),  // 수분·오메가3 / 나트륨
            SkinConcern.OILINESS,     Set.of("R07", "R03", "R06"),
            SkinConcern.TEXTURE,      Set.of("R06", "R05", "R07"),
            SkinConcern.PIGMENTATION, Set.of("R06", "R08"),
            SkinConcern.ELASTICITY,   Set.of("R05", "R08", "R06"),
            SkinConcern.PUFFINESS,    Set.of("R04", "R01"),
            SkinConcern.DARK_CIRCLE,  Set.of());

    public static Set<String> of(SkinConcern concern) {
        return RULES.getOrDefault(concern, Set.of());
    }

    /** 식단으로 설명할 수 있는 고민만 남긴다. 순서는 enum 선언 순서로 고정한다. */
    public static List<SkinConcern> scorable(Set<SkinConcern> concerns) {
        return java.util.Arrays.stream(SkinConcern.values())
                .filter(concerns::contains)
                .filter(concern -> !of(concern).isEmpty())
                .toList();
    }
}
