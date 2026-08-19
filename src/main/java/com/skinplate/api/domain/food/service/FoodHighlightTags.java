package com.skinplate.api.domain.food.service;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.food.entity.Nutrition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 기록 카드에 붙는 "주요영양" 칩. 한 끼에서 눈에 띄는 항목 두세 개를 서버가 고른다.
 *
 * <p><b>새 임계값을 만들지 않는다.</b> 무엇이 "높다/풍부하다"인지는 전부
 * {@link Nutrition} 이 이미 소유한 판정({@code isHighSodium} · {@code fiberTier} …)과
 * 재료 태그를 되읽은 것이다. 룰 엔진이 감점·가점을 결정한 것과 <b>같은 선</b>을 쓰므로,
 * 칩에 "나트륨"이 뜬 끼니는 결과 화면에서도 나트륨 경고를 받는다.
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

    /** 카드 하나에 들어가는 칩 수. 시안이 두세 개를 그린다. */
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
     */
    private static void add(List<String> picked, boolean present, String label) {
        if (present && picked.size() < MAX_TAGS && !picked.contains(label)) picked.add(label);
    }

    /**
     * 재료 태그 집합. {@code ETC} 는 "분류 없음"이라 빼둔다 — 남겨 두면 아무 뜻 없는
     * 태그가 contains 검사에 섞인다.
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
