package com.skinplate.api.domain.plate;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.plate.dto.PlateHistoryDayDto;
import com.skinplate.api.domain.plate.dto.PlateHistoryItemDto;
import com.skinplate.api.domain.plate.entity.SkinPlate;
import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.plate.service.PlateHistoryService;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PlateHistoryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 8, 13);
    private static final LocalDate TO = LocalDate.of(2026, 8, 14);

    private SkinPlateRepository skinPlateRepository;
    private SkinAnalysisRepository skinAnalysisRepository;
    private PlateHistoryService plateHistoryService;

    @BeforeEach
    void setUp() {
        skinPlateRepository = mock(SkinPlateRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        plateHistoryService = new PlateHistoryService(skinPlateRepository, skinAnalysisRepository);

        given(skinAnalysisRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        anyLong(), any(), any()))
                .willReturn(List.of());
    }

    @Test
    @DisplayName("날짜 내림차순으로 묶고, 같은 날 안에서는 시각 내림차순이다")
    void groupsByDateDescending() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of(
                plate(3L, "샐러드", 82, LocalDateTime.of(2026, 8, 14, 19, 4)),
                plate(2L, "치킨", 71, LocalDateTime.of(2026, 8, 14, 15, 21)),
                plate(1L, "떡볶이", 65, LocalDateTime.of(2026, 8, 13, 12, 32))));

        List<PlateHistoryDayDto> days = plateHistoryService.get(USER_ID, FROM, TO).days();

        assertThat(days).extracting(PlateHistoryDayDto::date)
                .containsExactly(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 13));
        assertThat(days.get(0).plates()).extracting(PlateHistoryItemDto::foodName)
                .containsExactly("샐러드", "치킨");
    }

    @Test
    @DisplayName("그날 얼굴을 안 찍었어도 점수가 빈칸이 아니다 — 기록의 채점 기준을 쓴다")
    void fallsBackToPlateBaseline() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any()))
                .willReturn(List.of(plate(1L, "떡볶이", 65, LocalDateTime.of(2026, 8, 13, 12, 32))));

        assertThat(plateHistoryService.get(USER_ID, FROM, TO).days().get(0).skinScore())
                .isEqualTo(55);
    }

    @Test
    @DisplayName("Plate 가 없는 날은 아예 나오지 않는다 — 히스토리는 식단 기록이다")
    void daysWithoutPlatesAreOmitted() {
        given(skinPlateRepository.findInRange(anyLong(), any(), any())).willReturn(List.of());

        assertThat(plateHistoryService.get(USER_ID, FROM, TO).days()).isEmpty();
    }

    @Test
    @DisplayName("from 이 to 보다 뒤면 400 이다")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> plateHistoryService.get(USER_ID, TO, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static SkinPlate plate(Long id, String foodName, int score, LocalDateTime createdAt) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        FoodAnalysis food = FoodAnalysis.create(user, foodName, "한식",
                Nutrition.of(500, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1800, BigDecimal.ONE),
                CookingMethod.BOILED, false, "{}");
        SkinAnalysis analysis = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");

        SkinPlate plate = SkinPlate.create(user, analysis, food, score, "요약", "[]");
        ReflectionTestUtils.setField(plate, "id", id);
        ReflectionTestUtils.setField(plate, "createdAt", createdAt);
        return plate;
    }
}
