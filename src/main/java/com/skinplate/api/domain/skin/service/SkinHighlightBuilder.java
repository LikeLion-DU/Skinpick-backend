package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.dto.HighlightDto;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * S05 상단의 3줄 요약을 만든다. (PRD §4.1)
 *
 * 두 결정을 분리한다.
 *   무엇을 보여줄까 → 위치 (가장 좋은 것 / 두 번째로 나쁜 것 / 가장 나쁜 것)
 *   어떤 상태로 보여줄까 → 값 (60 이상 GOOD / 40 이상 WARN / 그 미만 CAUTION)
 *
 * 위치만으로 상태까지 정하면 화면이 거짓말을 한다.
 *   모든 지표가 나쁜 사용자 → "가장 좋은 것"이 장벽 25 인데 초록 GOOD "장벽 양호"
 *   모든 지표가 좋은 사용자 → "가장 나쁜 것"이 유분 10 인데 빨강 CAUTION "유분 과다"
 * Skin Score 18 점 화면에 초록 뱃지가 뜨는 것은 86 점 vs 수분 38 과 같은 종류의 사고다.
 *
 * 위치로 3개를 뽑는 이유는 레이아웃 고정이다 — 어떤 날은 3줄, 어떤 날은 0줄이 되면
 * S05 화면이 매번 다른 높이로 그려진다.
 */
@Component
public class SkinHighlightBuilder {

    //                            key             GOOD           WARN             CAUTION
    private static final Map<String, String[]> LABELS = Map.of(
            "hydration", new String[]{"수분 충분",      "약간 건조",      "건조 주의"},
            // "유분 과다"가 아니라 "유분 많음". isOily 임계(70 초과)와 CAUTION 경계(정렬점수 40 미만
            // = 유분 60 초과)가 어긋나므로, 유분 65면 R07 은 안 켜지는데 뱃지만 빨강이 된다.
            // "유분 과다라면서 왜 튀김 감점이 없죠?"에 답할 수 없다. 문구를 약하게 둔다.
            "oil",       new String[]{"유분 균형",      "약간 번들거림",  "유분 많음"},
            "redness",   new String[]{"홍조 없음",      "약간 붉음",      "홍조 주의"},
            "trouble",   new String[]{"트러블 없음",    "약간의 트러블",  "트러블 주의"},
            "barrier",   new String[]{"피부 장벽 양호", "장벽 다소 약함", "장벽 손상 주의"});

    public List<HighlightDto> build(SkinMetrics metrics) {
        // 전부 "높을수록 좋음" 점수로 환산한 뒤 나쁜 순으로 정렬
        List<Scored> sorted = List.of(
                        new Scored("hydration", metrics.getHydration()),
                        new Scored("oil",       100 - metrics.getOil()),
                        new Scored("redness",   100 - metrics.getRedness()),
                        new Scored("trouble",   100 - metrics.getTrouble()),
                        new Scored("barrier",   metrics.getBarrier()))
                .stream()
                .sorted(Comparator.comparingInt(Scored::score)
                                  .thenComparing(Scored::key))   // 동점 시 순서 고정
                .toList();

        Scored worst  = sorted.get(0);
        Scored second = sorted.get(1);
        Scored best   = sorted.get(sorted.size() - 1);

        // 위치는 "무엇을 보여줄지"만 정한다. 상태와 문구는 값이 정한다.
        return List.of(toHighlight(best), toHighlight(second), toHighlight(worst));
    }

    /**
     * 임계값은 SkinMetrics 의 판정 기준과 일치시킨다.
     *   isDry / isBarrierWeak → 40 미만       → 정렬점수 40 미만 → CAUTION
     *   isOily(70 초과)       → 정렬점수 30   → CAUTION
     *   hasRedness(60 초과)   → 정렬점수 40 미만 → CAUTION
     * 두 곳의 기준이 어긋나면 "홍조 주의라면서 뱃지는 노랑"이 된다.
     */
    private HighlightDto toHighlight(Scored scored) {
        String[] labels = LABELS.get(scored.key());

        if (scored.score() >= 60) return HighlightDto.good(labels[0]);
        if (scored.score() >= 40) return HighlightDto.warn(labels[1]);
        return HighlightDto.caution(labels[2]);
    }

    private record Scored(String key, int score) {}
}
