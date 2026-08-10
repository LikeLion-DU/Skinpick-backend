package com.skinplate.api.domain.skin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.skin.dto.HighlightDto;
import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.repository.SkinAnalysisRepository;
import com.skinplate.api.domain.skin.service.SkinAnalysisService;
import com.skinplate.api.domain.skin.service.SkinHighlightBuilder;
import com.skinplate.api.domain.skin.service.SkinScoreCalculator;
import com.skinplate.api.domain.skin.service.SkinTypeGapAnalyzer;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import com.skinplate.api.infra.openai.VisionClient;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 점수·뱃지·갭 코멘트는 실제 구현을 그대로 쓴다. 그 셋이 이 API 가 증명해야 할 값이라
 * 대역으로 바꾸면 아무것도 검증하지 못한다. 대역은 DB 와 AI 뿐이다.
 *
 * 지표 38/52/64/25/78 은 문서의 시연 예시이자 Mock 클라이언트가 돌려주는 값이다.
 * 이 테스트가 깨지면 무대에서 말할 숫자가 바뀐 것이므로 문서도 같이 고쳐야 한다.
 */
class SkinAnalysisServiceTest {

    private static final Long USER_ID = 1L;

    private AppUserRepository userRepository;
    private SkinAnalysisRepository skinAnalysisRepository;
    private VisionClient visionClient;
    private SkinAnalysisService skinAnalysisService;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        skinAnalysisRepository = mock(SkinAnalysisRepository.class);
        visionClient = mock(VisionClient.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));

        given(skinAnalysisRepository.save(any(SkinAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        skinAnalysisService = new SkinAnalysisService(
                userRepository, skinAnalysisRepository, visionClient,
                new SkinScoreCalculator(), new SkinHighlightBuilder(), new SkinTypeGapAnalyzer(),
                new ObjectMapper(), transactionTemplate);
    }

    @Test
    @DisplayName("지표 38/52/64/25/78 → 55점 · 뱃지 3줄 · OILY→DRY 갭 코멘트 (PRD §14.3 ⑤)")
    void analyze_reproducesDocumentedExample() {
        givenUser(SkinType.OILY);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                "피부 장벽은 양호하지만 건조하고 홍조가 관찰됩니다."));

        SkinAnalysisResponse response = skinAnalysisService.analyze(USER_ID, jpegImage());

        assertThat(response.skinScore()).isEqualTo(55);
        assertThat(response.metrics().hydration()).isEqualTo(38);
        assertThat(response.highlights()).containsExactly(
                HighlightDto.good("피부 장벽 양호"),
                HighlightDto.caution("건조 주의"),
                HighlightDto.caution("홍조 주의"));
        assertThat(response.skinTypeGap().declared()).isEqualTo(SkinType.OILY);
        assertThat(response.skinTypeGap().observed()).isEqualTo(SkinType.DRY);
        assertThat(response.skinTypeGap().matched()).isFalse();
        assertThat(response.skinTypeGap().message()).startsWith("지성이라고 생각하셨지만");
    }

    @Test
    @DisplayName("피부 타입을 안 골랐으면 skinTypeGap 은 null 이다 — 앱이 선택 칩을 띄운다")
    void analyze_withoutDeclaredType_returnsNullGap() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78, "요약"));

        assertThat(skinAnalysisService.analyze(USER_ID, jpegImage()).skinTypeGap()).isNull();
    }

    @Test
    @DisplayName("faceDetected=false 면 422 이고 저장하지 않는다")
    void analyze_faceNotDetected_throwsAndDoesNotSave() {
        givenSkinResult(new OpenAiSkinResult(false, 0, 0, 0, 0, 0, null));

        assertThatThrownBy(() -> skinAnalysisService.analyze(USER_ID, jpegImage()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FACE_NOT_DETECTED);

        verify(skinAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("JPEG·PNG 가 아니면 AI 를 부르기 전에 막는다 — 호출 한 번이 곧 비용이다")
    void analyze_rejectsNonImage_beforeCallingAi() {
        // 헤더는 image/jpeg 라고 말하지만 내용은 PDF 다. 헤더를 믿으면 이게 통과한다.
        MultipartFile pdf = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> skinAnalysisService.analyze(USER_ID, pdf))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);

        verify(visionClient, never()).analyzeSkin(anyString(), anyString());
    }

    @Test
    @DisplayName("PNG 는 PNG 로 선언해서 보낸다 — image/jpeg 로 고정하면 OpenAI 가 거절한다")
    void analyze_declaresActualMediaType() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78, "요약"));
        MultipartFile png = new MockMultipartFile("image", "shot.png", "application/octet-stream",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        skinAnalysisService.analyze(USER_ID, png);

        ArgumentCaptor<String> mediaType = ArgumentCaptor.forClass(String.class);
        verify(visionClient).analyzeSkin(anyString(), mediaType.capture());
        assertThat(mediaType.getValue()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("summary 가 300자를 넘겨도 저장에서 터지지 않는다 — 유료 호출은 이미 끝나 있다")
    void analyze_trimsOverlongSummary() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78, "가".repeat(500)));

        assertThat(skinAnalysisService.analyze(USER_ID, jpegImage()).summary()).hasSize(300);
    }

    @Test
    @DisplayName("자르는 자리가 이모지 한가운데면 한 글자 덜 자른다 — 반쪽 문자는 저장에서 터진다")
    void analyze_doesNotSplitSurrogatePair() {
        givenUser(null);
        // 299자 + 이모지 → 300번째 char 가 이모지의 앞쪽 절반이다
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78, "가".repeat(299) + "🙂"));

        String summary = skinAnalysisService.analyze(USER_ID, jpegImage()).summary();

        assertThat(summary).hasSize(299);
        assertThat(summary.chars().anyMatch(c -> Character.isSurrogate((char) c))).isFalse();
    }

    @Test
    @DisplayName("남의 분석 id 는 403 이 아니라 404 다 — 존재 여부를 알려주지 않는다")
    void get_otherUsersAnalysis_returns404() {
        given(skinAnalysisRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skinAnalysisService.get(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SKIN_ANALYSIS_NOT_FOUND);
    }

    @Test
    @DisplayName("기록이 하나도 없으면 latest 는 null 이다 — 홈 화면이 빈 카드를 그린다")
    void getLatest_withoutHistory_returnsNull() {
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.empty());

        assertThat(skinAnalysisService.getLatest(USER_ID)).isNull();
    }

    @Test
    @DisplayName("조회에서도 뱃지와 갭은 저장값이 아니라 지표에서 다시 계산된다")
    void getLatest_rebuildsDerivedValues() {
        AppUser user = givenUser(SkinType.OILY);
        SkinAnalysis stored = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "{}");
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.of(stored));

        SkinAnalysisResponse response = skinAnalysisService.getLatest(USER_ID);

        assertThat(response.highlights()).hasSize(3);
        assertThat(response.skinTypeGap().observed()).isEqualTo(SkinType.DRY);
    }

    // ---- 픽스처 ----

    private AppUser givenUser(SkinType declaredSkinType) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (declaredSkinType != null) user.declareSkinType(declaredSkinType);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    private void givenSkinResult(OpenAiSkinResult result) {
        given(visionClient.analyzeSkin(anyString(), anyString())).willReturn(result);
    }

    /** 앞 세 바이트가 JPEG 시그니처다. 형식 판별이 헤더가 아니라 여기를 본다. */
    private MultipartFile jpegImage() {
        return new MockMultipartFile("image", "face.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }
}
