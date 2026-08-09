package com.skinplate.api.domain.plate.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * Skin Plate Score 계산기.
 *
 * 점수를 LLM에 맡기지 않는 이유:
 *   같은 사진을 두 번 찍으면 다른 점수가 나오고, 그러면 무대에서 설명할 수 없다.
 *   규칙 기반 계산은 항상 같은 결과를 낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlateRuleEngine {

    /** Spring이 PlateRule 구현체를 전부 주입한다. 룰 추가 = 클래스 추가. */
    private final List<PlateRule> rules;

    public PlateEvaluation evaluate(PlateContext context) {

        List<RuleResult> applied = rules.stream()
                .sorted(Comparator.comparingInt(PlateRule::priority)
                                  .thenComparing(PlateRule::code))   // 순서 고정
                .filter(rule -> rule.supports(context))
                .map(rule -> rule.apply(context))
                .toList();

        int raw = BASE_SCORE + applied.stream().mapToInt(RuleResult::delta).sum();
        int score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, raw));

        log.debug("Plate 평가 결과 {}점 (원점수 {}), 적용 룰 {}",
                  score, raw, applied.stream().map(RuleResult::ruleCode).toList());

        return new PlateEvaluation(score, applied, buildSummary(applied));
    }

    /** 좋은 점 1개 + 주의사항 1개를 엮어 한 문장으로 만든다. */
    private String buildSummary(List<RuleResult> results) {
        String goods = results.stream()
                .filter(result -> result.delta() > 0)
                .map(RuleResult::message)
                .limit(2)
                .collect(Collectors.joining(", "));

        String cautions = results.stream()
                .filter(result -> result.delta() < 0)
                .map(RuleResult::message)
                .limit(2)
                .collect(Collectors.joining(", "));

        if (goods.isBlank() && cautions.isBlank()) return "특별한 주의사항이 없는 무난한 한 끼입니다.";
        if (cautions.isBlank()) return goods + ". 오늘 피부에 잘 맞는 선택입니다.";
        if (goods.isBlank())    return cautions + ". 오늘 피부에는 부담이 될 수 있습니다.";
        return goods + ". 다만 " + cautions + ".";
    }
}
