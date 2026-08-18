package com.skinplate.api.domain.food.entity;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "food_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 50)
    private String foodCategory;

    @Embedded
    private Nutrition nutrition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CookingMethod cookingMethod;

    @Column(name = "is_spicy", nullable = false)
    private boolean spicy;

    /**
     * 매칭된 표준 음식 이름. NULL 이면 표준 음식표에서 찾지 못했다는 뜻이다.
     *
     * <p><b>이 값이 있으면 점수 입력이 전부 표준표에서 온다</b> — 영양값·조리법·매운맛·재료
     * 태그·강도 계수까지. 같은 사진을 세 번 분석해도 같은 점수가 나오는 근거가 이 한 줄이다.
     */
    @Column(name = "standard_food_name", length = 100)
    private String standardFoodName;

    /**
     * AI 관찰 특성 5종 (V7). create() 시그니처를 늘리지 않고 {@link #assignTraits} 로
     * 한 번만 붙인다 — 기존 호출부·테스트가 그대로 남고, 안 붙인 엔티티는
     * {@link #getTraits()}가 UNKNOWN 으로 읽어 기존과 동일하게 동작한다.
     */
    @Embedded
    private FoodTraits traits;

    @OneToMany(mappedBy = "foodAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodIngredient> ingredients = new ArrayList<>();

    /**
     * AI 분석 원본 + 서버 메타데이터. 기록 저장(POST /plates/records)이 만든 행은
     * 최상위 형제 키로 {@code _meta.jti} 를 얹어 멱등키로 쓴다 — 새 컬럼·테이블 없이
     * 기존 jsonb 하나를 재사용한다. 이 컬럼이 없던 시절 행은 {@code _meta} 가 없고,
     * {@code raw_ai_response->'_meta'->>'jti'} 는 그 행들에서 null 을 준다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static FoodAnalysis create(AppUser user,
                                      String foodName,
                                      String foodCategory,
                                      Nutrition nutrition,
                                      CookingMethod cookingMethod,
                                      boolean spicy,
                                      String rawAiResponse) {
        FoodAnalysis food = new FoodAnalysis();
        food.user = user;
        food.foodName = foodName;
        food.foodCategory = foodCategory;
        food.nutrition = nutrition;
        food.cookingMethod = cookingMethod == null ? CookingMethod.ETC : cookingMethod;
        food.spicy = spicy;
        food.rawAiResponse = rawAiResponse;
        return food;
    }

    /** 생성 직후 한 번만 부른다. null 을 넣어도 getTraits() 가 UNKNOWN 으로 흡수한다. */
    public void assignTraits(FoodTraits traits) {
        this.traits = traits;
    }

    /** 생성 직후 한 번만 부른다. 표준 음식표에서 찾았을 때만 이름이 들어간다. */
    public void assignStandardFoodName(String standardFoodName) {
        this.standardFoodName = standardFoodName;
    }

    /** 표준 음식표가 이 한 끼를 확정했는가. 점수 입력의 출처를 가르는 유일한 조건이다. */
    public boolean isStandardMatched() {
        return standardFoodName != null;
    }

    /**
     * <b>점수 계산에만</b> 쓰는 특성. 표준 음식으로 확정된 한 끼는 UNKNOWN(=강도 1.0)이다.
     *
     * <p>spiciness·oiliness 는 AI 가 사진에서 관찰한 값이라 회차마다 달라진다 — 같은 떡볶이가
     * HOT 과 MEDIUM 을 오가면 R02 가 -16 과 -12 사이에서 흔들린다. 표준표가 이미 "맵다" 를
     * 확정한 음식에서 강도까지 AI 에 물으면 그 확정이 무의미해진다.
     *
     * <p>화면과 AI 코멘트는 {@link #getTraits()} 를 그대로 쓴다 — 관찰값을 버리는 것이 아니라
     * 점수에서만 뺀다. 표준표에 없는 음식은 다른 단서가 없으므로 관찰값을 그대로 쓴다.
     */
    public FoodTraits scoringTraits() {
        return isStandardMatched() ? FoodTraits.UNKNOWN : getTraits();
    }

    /**
     * null 을 반환하지 않는다. V7 이전 행·구 토큰·트레이트를 안 붙인 픽스처는
     * 전부 UNKNOWN 으로 읽혀 룰·리포트가 기존과 동일하게 동작한다.
     */
    public FoodTraits getTraits() {
        return traits == null ? FoodTraits.UNKNOWN : traits;
    }

    // ---- 연관관계 ----

    public void addIngredient(FoodIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.assignTo(this);
    }

    public void addIngredients(List<FoodIngredient> ingredientList) {
        ingredientList.forEach(this::addIngredient);
    }

    // ---- Rule Engine이 사용하는 질의 ----

    /**
     * <b>점수용 태그 판정.</b> 표준 음식으로 확정됐으면 표준표가 준 태그만 본다.
     *
     * <p>AI 가 사진에서 읽은 재료를 섞으면 같은 사진이 다른 점수를 낸다 — 떡볶이 한 장에서
     * ANTIOXIDANT 가 왔다 가면 R06 이 ±5, PROBIOTIC 이면 R09 가 ±4 움직였다.
     *
     * <p>표준표에서 못 찾은 음식은 <b>어떤 태그도 서지 않는다</b>. AI 태그를 쓰면 결정론이
     * 깨지고, 그렇다고 태그가 있는 척할 수도 없다 — 태그 룰이 전부 꺼지는 쪽이 안전하다.
     * 그 음식의 점수는 영양값 기반 룰(R04·R05·R10·R11)로만 난다.
     */
    public boolean hasTag(IngredientTag tag) {
        return ingredients.stream()
                .filter(FoodIngredient::isFromStandard)
                .anyMatch(ingredient -> ingredient.getTag() == tag);
    }

    public boolean hasAnyTag(IngredientTag... tags) {
        for (IngredientTag tag : tags) {
            if (hasTag(tag)) return true;
        }
        return false;
    }

    public boolean isSoup() {
        return cookingMethod == CookingMethod.BOILED;
    }

    public boolean isFried() {
        return cookingMethod == CookingMethod.FRIED;
    }

    /** 명시적 spicy 플래그 또는 캡사이신 재료 */
    public boolean isSpicyFood() {
        return spicy || hasTag(IngredientTag.CAPSAICIN);
    }
}
