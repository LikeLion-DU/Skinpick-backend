package com.skinplate.api.domain.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.report.dto.ConcernScoreDto;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.report.dto.DailyScoreDto;
import com.skinplate.api.domain.report.dto.NutrientType;
import com.skinplate.api.domain.report.dto.NutritionItemDto;
import com.skinplate.api.domain.skin.dto.CareFocusDto;
import com.skinplate.api.domain.skin.entity.SkinCareFocus;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.user.entity.SkinConcern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새 필드가 JSON 에서 어떤 이름·어떤 모양으로 나가는지 못 박는다.
 *
 * <p>앱 DTO 와 어긋나면 <b>조용히 null 이 되고 화면에 빈 값이 뜬다</b>(설계서 Part 3).
 * 서버는 {@code default-property-inclusion: non_null} 이라 null 필드는 키 자체가 사라지므로,
 * "키가 없는 것"과 "값이 null 인 것"의 구분까지 여기서 고정한다.
 */
class NewContractSerializationTest {

    /**
     * application.yml 의 non_null 을 흉내 낸다 — 그 설정이 곧 계약이다.
     * JavaTimeModule 은 Spring Boot 가 자동으로 넣어 주는 것이라 여기서 직접 등록한다.
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    @DisplayName("일일 리포트에 skinNutrients 키가 nutrition 과 나란히 나간다")
    void dailyCarriesSkinNutrients() throws Exception {
        DailyReportResponse response = new DailyReportResponse(
                LocalDate.of(2026, 8, 12), 72, SkinLevel.GOOD, 3,
                List.of(NutritionItemDto.of(NutrientType.CALORIES, BigDecimal.valueOf(1560))),
                List.of(NutritionItemDto.of(NutrientType.VITAMIN_C, BigDecimal.valueOf(45)),
                        NutritionItemDto.unmeasured(NutrientType.ZINC)),
                List.of(), List.of(), null, List.of(), List.of());

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"nutrition\"").contains("\"skinNutrients\"");
        assertThat(json).contains("\"nutrient\":\"VITAMIN_C\"").contains("\"unit\":\"mg\"");
        // 측정 못 한 항목은 status 키가 통째로 빠진다 — 앱은 키 없음을 "알 수 없음"으로 읽는다.
        assertThat(json).contains("\"nutrient\":\"ZINC\"");
        assertThat(json).doesNotContain("\"status\":null");
    }

    @Test
    @DisplayName("오메가3 단위는 '회' 다 — 앱이 g 으로 읽으면 안 된다")
    void omega3UnitIsCount() throws Exception {
        String json = mapper.writeValueAsString(
                NutritionItemDto.of(NutrientType.OMEGA3, BigDecimal.ONE));

        assertThat(json).contains("\"nutrient\":\"OMEGA3\"")
                .contains("\"unit\":\"회\"")
                .contains("\"target\":1");
    }

    @Test
    @DisplayName("고민의 message 는 없으면 키가 빠지고 tags 는 빈 배열로 남는다")
    void concernNullMessageOmitsKey() throws Exception {
        String withMessage = mapper.writeValueAsString(ConcernScoreDto.of(
                SkinConcern.ACNE, 62, null, "당류가 높은 편이에요.", List.of("당류 과다")));
        String without = mapper.writeValueAsString(
                ConcernScoreDto.of(SkinConcern.ACNE, 62, null, null, List.of()));

        assertThat(withMessage).contains("\"message\":\"당류가 높은 편이에요.\"")
                .contains("\"tags\":[\"당류 과다\"]");
        // 배열은 비어도 키가 남는다. 앱이 "키 없음"과 "빈 배열"을 같게 다루므로 문제없다.
        assertThat(without).doesNotContain("\"message\"").contains("\"tags\":[]");
    }

    @Test
    @DisplayName("기록 카드에 highlightTags 가 실리고, 비면 빈 배열이다")
    void historyItemCarriesTags() throws Exception {
        String json = mapper.writeValueAsString(new PlateHistoryItemDto(
                27L, "떡볶이", 58, SkinLevel.of(58), null,
                LocalDate.of(2026, 8, 12).atTime(12, 0),
                List.of("나트륨", "당류")));

        assertThat(json).contains("\"highlightTags\":[\"나트륨\",\"당류\"]");
        // 등급을 함께 싣는다 — 앱이 점수에서 다시 내면 경계표가 두 벌이 된다.
        assertThat(json).contains("\"grade\":\"NORMAL\"");
        // mealType 이 null 이면 키가 빠지는 기존 계약은 그대로다.
        assertThat(json).doesNotContain("\"mealType\"");
    }

    @Test
    @DisplayName("BEST/WORST 만 plateIds 를 갖는다 — 추이 칸에는 키가 없다")
    void plateIdsOnlyOnBestWorst() throws Exception {
        DailyScoreDto trend = DailyScoreDto.of(LocalDate.of(2026, 8, 12), 72);
        DailyScoreDto best = trend.withPlateIds(List.of(27L, 28L));

        assertThat(mapper.writeValueAsString(trend)).doesNotContain("plateIds");
        assertThat(mapper.writeValueAsString(best)).contains("\"plateIds\":[27,28]");
    }

    @Test
    @DisplayName("careFocus 는 enum 이름과 라벨을 함께 싣는다 — 앱이 축 이름을 갖지 않는다")
    void careFocusCarriesLabel() throws Exception {
        String json = mapper.writeValueAsString(
                List.of(CareFocusDto.from(SkinCareFocus.HYDRATION)));

        assertThat(json).contains("\"focus\":\"HYDRATION\"").contains("\"label\":\"수분·장벽\"");
    }
}
