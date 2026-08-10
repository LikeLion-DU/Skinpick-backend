package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.springframework.stereotype.Component;

/**
 * Skin Score = 5개 지표의 방향을 "높을수록 좋음"으로 통일한 뒤 평균. (PRD §4.1)
 *
 * 산식이 없으면 구현자가 아무 평균이나 짜고, 그때부터 문서의 예시 점수와 어긋난다.
 * S05 화면은 총점 게이지와 5개 지표 바를 한 화면에 동시에 띄우기 때문에
 * 둘이 안 맞으면 심사위원이 3초 만에 본다.
 */
@Component
public class SkinScoreCalculator {

    public int calculate(SkinMetrics metrics) {
        int sum = metrics.getHydration()          // 높을수록 좋음
                + metrics.getBarrier()            // 높을수록 좋음
                + (100 - metrics.getOil())        // 낮을수록 좋음 → 뒤집는다
                + (100 - metrics.getRedness())
                + (100 - metrics.getTrouble());

        return Math.round(sum / 5f);
    }
}
