package com.skinplate.api.domain.insight.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "skin_insight_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinInsightItem extends BaseTimeEntity {

    /** V6 의 description 길이와 묶인다. 둘이 갈라지면 클램프가 INSERT 를 못 막는다. */
    private static final int DESCRIPTION_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_insight_id", nullable = false)
    private SkinInsight skinInsight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InsightCategory category;

    /** 카테고리 라벨을 복사해 둔다 — 라벨을 나중에 바꿔도 과거 인사이트의 화면은 안 바뀐다. */
    @Column(nullable = false, length = 50)
    private String title;

    /** AI 가 만든 유일한 문장. 나머지는 전부 규칙이 정한 값이다. */
    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, length = 100)
    private String actionTitle;

    /** 0/1/2 가 그대로 우선순위 HIGH/MEDIUM/LOW 다. */
    @Column(nullable = false)
    private int displayOrder;

    public static SkinInsightItem of(InsightCategory category, String description, int displayOrder) {
        SkinInsightItem item = new SkinInsightItem();
        item.category = category;
        item.title = category.getTitle();
        item.description = clamp(description);
        item.actionTitle = category.getActionTitle();
        item.displayOrder = displayOrder;
        return item;
    }

    /**
     * Structured Outputs 는 프롬프트의 글자 수 요청을 지켜주지 않는다.
     * 넘치는 문장 하나가 INSERT 를 깨서 인사이트 전체를 잃게 둘 수는 없다.
     * 경계가 이모지 한가운데면 반쪽 문자가 남아 Postgres 가 거절한다 — SkinPlate.clamp 와 같은 방식.
     */
    private static String clamp(String sentence) {
        if (sentence == null || sentence.length() <= DESCRIPTION_MAX_LENGTH) return sentence;

        int end = Character.isHighSurrogate(sentence.charAt(DESCRIPTION_MAX_LENGTH - 1))
                ? DESCRIPTION_MAX_LENGTH - 1
                : DESCRIPTION_MAX_LENGTH;

        return sentence.substring(0, end);
    }

    void assignTo(SkinInsight skinInsight) {
        this.skinInsight = skinInsight;
    }
}
