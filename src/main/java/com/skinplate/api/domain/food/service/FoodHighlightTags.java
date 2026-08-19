package com.skinplate.api.domain.food.service;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 기록 카드에 붙는 "주요영양" 칩. 한 끼에서 눈에 띄는 항목을 최대 셋까지 서버가 고른다.
 *
 * <p><b>이 값이 무엇인가 — 그리고 무엇이 아닌가.</b> 칩은 <b>그 기록에서 관찰된 것을
 * 요약해 보여주는 표시값</b>이다. <b>룰이 발동했는지를 나타내는 값이 아니다.</b>
 * 그래서 룰 엔진의 판정과 반드시 일치할 필요가 없고, 실제로 한 곳에서 갈린다:
 *
 * <pre>
 *   영양값 칩(나트륨·당류·포화지방·열량·단백질·식이섬유)
 *       → {@link Nutrition} 이 소유한 판정을 그대로 되읽는다. 룰과 <b>같은 선</b>이다.
 *
 *   재료 태그 칩(비타민·오메가3·발효식품)
 *       → 저장된 재료 태그를 전부 읽는다. 룰의 {@code FoodAnalysis#hasTag} 는
 *         {@code isFromStandard} 인 태그만 보므로 <b>여기가 더 넓다</b>.
 * </pre>
 *
 * <p><b>왜 좁히지 않았나.</b> 룰이 표준표 태그만 보는 것은 <b>점수의 재현성</b> 때문이다 —
 * AI 태그로 가점을 주면 같은 사진이 실행마다 다른 점수를 받는다. 칩은 점수를 만들지
 * 않으므로 그 제약을 받지 않고, 반대로 좁히면 표준표에 없는 음식(집밥·외국 음식)의
 * 카드가 통째로 비어 "이 기록은 분석이 덜 됐다"로 읽힌다. 재현성은 그대로다 — 태그는
 * 분석 시점에 <b>저장된 값</b>이라 같은 기록은 언제 열어도 같은 칩이다.
 *
 * <p>따라서 <b>칩이 뜬 끼니가 결과 화면에서 같은 항목의 피드백을 받는다는 보장은
 * 영양값 칩에만 있다.</b> 미매칭 끼니에 "오메가3" 칩이 뜨는데 R08·R14 가 안 걸리는
 * 것은 버그가 아니라 위 두 기준의 차이다.
 *
 * <p><b>새 임계값을 만들지 않는다.</b> 무엇이 "높다/풍부하다"인지는 전부 {@link Nutrition}
 * 이 이미 소유한 판정({@code isHighSodium} · {@code fiberTier} …)과 재료 태그를 되읽은
 * 것이다. 여기에 숫자를 하나라도 새로 쓰면 같은 판정이 두 곳에 생긴다.
 *
 * <p><b>앱이 고르지 않는 이유.</b> 히스토리 목록은 끼니마다 영양값을 내려보내지 않는다
 * (카드 한 줄에 필요한 것은 이름 세 개뿐이다). 앱이 고르려면 목록에 영양값 전체를
 * 실어야 하고, 그러면 앱 안에 "얼마부터 높은가"가 또 한 벌 생긴다.
 *
 * <p>우선순위는 선언 순서다. 같은 끼니를 두 번 조회했을 때 칩 순서가 흔들리면
 * 사용자가 값이 바뀐 것으로 읽으므로, 빈도나 크기로 정렬하지 않고 고정한다.
 * 부담이 되는 항목이 앞이다 — 카드 한 장에서 먼저 읽어야 하는 쪽이다.
 */
public final class FoodHighlightTags {

    private FoodHighlightTags() {}

    /**
     * 카드 하나에 들어가는 칩 수의 <b>상한</b>. 시안이 두세 개를 그린다.
     *
     * <p>하한은 없다 — 걸리는 항목이 하나면 하나고, 없으면 빈 배열이다.
     */
    private static final int MAX_TAGS = 3;

    public static List<String> of(FoodAnalysis food) {
        Nutrition nutrition = food.getNutrition();
        Set<IngredientTag> tags = tagsOf(food);

        List<String> picked = new ArrayList<>(MAX_TAGS);

        // ---- 부담이 되는 쪽 ----
        add(picked, nutrition.isHighSodium(), "나트륨");
        add(picked, nutrition.isHighSugar(), "당류");
        add(picked, nutrition.saturatedFatTier() > 0, "포화지방");
        add(picked, nutrition.isHighCalorie(), "열량");

        // ---- 챙긴 쪽 ----
        add(picked, nutrition.isHighProtein(), "단백질");
        add(picked, nutrition.fiberTier() > 0, "식이섬유");
        add(picked, nutrition.isVitaminRich()
                || tags.contains(IngredientTag.VITAMIN_C)
                || tags.contains(IngredientTag.VITAMIN_A), "비타민");
        add(picked, tags.contains(IngredientTag.OMEGA3), "오메가3");
        add(picked, tags.contains(IngredientTag.PROBIOTIC), "발효식품");

        return List.copyOf(picked);
    }

    /**
     * 아무것도 걸리지 않는 끼니가 있다 — 룰이 하나도 안 켜진 평범한 식사다.
     * 그때는 <b>빈 배열</b>이고 앱은 칩 줄을 그리지 않는다. 억지로 채우면 "이 음식의
     * 특징"이 아니라 "가장 덜 평범한 항목"이 되어 뜻이 달라진다.
     *
     * <p>중복 검사는 두지 않는다 — 호출부의 라벨 아홉 개가 전부 다른 리터럴이라
     * 같은 라벨이 두 번 들어올 길이 없다. 라벨을 재사용하는 축을 넣게 되면
     * 그때 여기에 검사를 붙이는 것이 아니라 라벨을 나눠야 한다.
     */
    private static void add(List<String> picked, boolean present, String label) {
        if (present && picked.size() < MAX_TAGS) picked.add(label);
    }

    /**
     * 재료 태그 집합. {@code ETC} 는 "분류 없음"이라 빼둔다 — 남겨 두면 아무 뜻 없는
     * 태그가 contains 검사에 섞인다.
     *
     * <p><b>{@code isFromStandard} 로 거르지 않는다 — 의도한 것이다.</b> 룰의
     * {@code FoodAnalysis#hasTag} 는 점수를 만들기 때문에 표준표 태그만 보지만, 여기는
     * 표시값이라 저장된 관찰을 그대로 읽는다. 자세한 이유는 클래스 주석에 적었다.
     * 거르는 순간 표준표에 없는 음식의 칩이 전멸한다.
     *
     * <p>{@link EnumSet} 이라 순회 순서가 enum 선언 순서로 고정된다 — 다만 이 집합은
     * {@code contains} 로만 쓰이고 칩 순서는 {@code of} 의 호출 순서가 정한다.
     *
     * <p>반드시 트랜잭션 안에서 부른다 — {@code ingredients} 가 LAZY 컬렉션이다.
     */
    private static Set<IngredientTag> tagsOf(FoodAnalysis food) {
        Set<IngredientTag> tags = EnumSet.noneOf(IngredientTag.class);
        food.getIngredients().stream()
                .map(ingredient -> ingredient.getTag())
                .filter(tag -> tag != null && tag != IngredientTag.ETC)
                .forEach(tags::add);
        return tags;
    }
}
