package com.skinplate.api.domain.skin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.domain.skin.dto.HighlightDto;
import com.skinplate.api.domain.skin.dto.ScoredItemDto;
import com.skinplate.api.domain.skin.dto.SkinAgeDto;
import com.skinplate.api.domain.skin.dto.SkinAnalysisResponse;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinLevel;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.skin.entity.SkinTrait;
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
import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.FacePhotoType;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        givenSkinResult(demoResult("피부 장벽은 양호하지만 건조하고 홍조가 관찰됩니다."));

        SkinAnalysisResponse response = analyzeThreePhotos();

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
        givenSkinResult(demoResult("요약"));

        assertThat(analyzeThreePhotos().skinTypeGap()).isNull();
    }

    @Test
    @DisplayName("세 장이 한 번의 호출로, 각자의 방향 표시를 달고 넘어간다 (지시서 §5 · §8)")
    void analyze_sendsThreeLabelledPhotosInOneCall() {
        givenUser(null);
        givenSkinResult(demoResult("요약"));

        // 세 장을 서로 다른 바이트로 만든다. 같으면 방향이 뒤바뀌어도 통과한다.
        skinAnalysisService.analyze(USER_ID,
                jpegImage((byte) 0x01), jpegImage((byte) 0x02), jpegImage((byte) 0x03));

        List<FacePhoto> photos = capturePhotos();

        assertThat(photos).extracting(FacePhoto::type)
                .containsExactly(FacePhotoType.FRONT, FacePhotoType.LEFT, FacePhotoType.RIGHT);
        assertThat(photos).extracting(photo -> lastByteOf(photo.base64()))
                .containsExactly((byte) 0x01, (byte) 0x02, (byte) 0x03);
        // 장당 한 번씩 부르면 지표가 세 벌 나오고 비용도 세 배가 된다.
        verify(visionClient).analyzeSkin(anyList());
    }

    @Test
    @DisplayName("faceDetected=false 면 422 이고 저장하지 않는다")
    void analyze_faceNotDetected_throwsAndDoesNotSave() {
        givenSkinResult(new OpenAiSkinResult(false, 0, 0, 0, 0, 0, null, null, null, null));

        assertThatThrownBy(this::analyzeThreePhotos)
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
                "left", "face.jpg", "image/jpeg", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> skinAnalysisService.analyze(USER_ID, jpegImage(), pdf, jpegImage()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);

        verify(visionClient, never()).analyzeSkin(anyList());
    }

    @Test
    @DisplayName("세 장 중 어느 것이 잘못됐는지 메시지에 담는다 — 아니면 셋 다 다시 찍는다")
    void analyze_namesTheOffendingDirection() {
        MultipartFile empty = new MockMultipartFile("right", new byte[0]);

        assertThatThrownBy(() -> skinAnalysisService.analyze(USER_ID, jpegImage(), jpegImage(), empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("오른쪽으로 돌린");

        verify(visionClient, never()).analyzeSkin(anyList());
    }

    @Test
    @DisplayName("PNG 는 PNG 로 선언해서 보낸다 — image/jpeg 로 고정하면 OpenAI 가 거절한다")
    void analyze_declaresActualMediaType() {
        givenUser(null);
        givenSkinResult(demoResult("요약"));
        MultipartFile png = new MockMultipartFile("left", "shot.png", "application/octet-stream",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        // 세 장의 형식이 서로 달라도 각자 판별된다. 앱은 크롭 결과를 JPEG 로 보내지만
        // 웹은 사용자가 고른 파일이 그대로 올라와 한 장만 PNG 인 경우가 생긴다.
        skinAnalysisService.analyze(USER_ID, jpegImage(), png, jpegImage());

        assertThat(capturePhotos()).extracting(FacePhoto::mediaType)
                .containsExactly("image/jpeg", "image/png", "image/jpeg");
    }

    @Test
    @DisplayName("summary 가 300자를 넘겨도 저장에서 터지지 않는다 — 유료 호출은 이미 끝나 있다")
    void analyze_trimsOverlongSummary() {
        givenUser(null);
        givenSkinResult(demoResult("가".repeat(500)));

        assertThat(analyzeThreePhotos().summary()).hasSize(300);
    }

    @Test
    @DisplayName("자르는 자리가 이모지 한가운데면 한 글자 덜 자른다 — 반쪽 문자는 저장에서 터진다")
    void analyze_doesNotSplitSurrogatePair() {
        givenUser(null);
        // 299자 + 이모지 → 300번째 char 가 이모지의 앞쪽 절반이다
        givenSkinResult(demoResult("가".repeat(299) + "🙂"));

        String summary = analyzeThreePhotos().summary();

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

    @Test
    @DisplayName("등급은 방향을 맞춘 뒤 매긴다 — 유분 52 가 NORMAL 이지 GOOD 이 아니다")
    void analyze_levelsUseDirectionAlignedScore() {
        givenUser(null);
        givenSkinResult(demoResult("요약"));

        assertThat(analyzeThreePhotos().metricDetails())
                .extracting(ScoredItemDto::key, ScoredItemDto::score, ScoredItemDto::level)
                .containsExactly(
                        tuple("hydration", 38, SkinLevel.CAUTION),    // 그대로 38
                        tuple("oil",       52, SkinLevel.NORMAL),     // 정렬 48
                        tuple("redness",   64, SkinLevel.CAUTION),    // 정렬 36
                        tuple("trouble",   25, SkinLevel.GOOD),       // 정렬 75
                        tuple("barrier",   78, SkinLevel.GOOD));      // 그대로 78
    }

    @Test
    @DisplayName("근거는 지표당 2개까지만 남는다 — 스키마가 개수를 못 막는다")
    void analyze_trimsEvidenceBeyondTheCap() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                new OpenAiSkinResult.MetricEvidence(
                        List.of("첫째", "둘째", "셋째", "넷째"),   // 넷을 보내도
                        List.of(), List.of("  "), null, List.of("근거")),
                null, null, "요약"));

        List<ScoredItemDto> details = analyzeThreePhotos().metricDetails();

        assertThat(details.get(0).evidence()).containsExactly("첫째", "둘째");   // 둘만 남는다
        assertThat(details.get(1).evidence()).isEmpty();
        assertThat(details.get(2).evidence()).isEmpty();                       // 공백은 버린다
        assertThat(details.get(3).evidence()).isEmpty();                       // null 도 빈 배열
        assertThat(details.get(4).evidence()).containsExactly("근거");
    }

    @Test
    @DisplayName("피부 나이 축은 7개다 — redness 는 상태 지표와 겹쳐 응답에서 뺀다")
    void analyze_omitsRednessFromAgeAxes() {
        givenUser(null);
        givenSkinResult(demoResult("요약"));

        SkinAgeDto skinAge = analyzeThreePhotos().skinAge();

        assertThat(skinAge.estimatedSkinAge()).isEqualTo(29);
        assertThat(skinAge.axes()).extracting(ScoredItemDto::key)
                .containsExactly("skinTexture", "elasticity", "wrinkles",
                                 "skinTone", "pores", "pigmentation", "blemishMarks");
        // 주름 28 은 "높을수록 나쁨"이라 정렬 72 → GOOD. 뒤집지 않으면 CAUTION 이 된다.
        assertThat(skinAge.axes().get(2).level()).isEqualTo(SkinLevel.GOOD);
    }

    @Test
    @DisplayName("피부 나이는 18~80 밖으로 나가지 않는다 — 스키마 minimum 을 믿지 않는다")
    void analyze_clampsSkinAge() {
        givenUser(null);
        givenSkinResult(withSkinAge(120));

        assertThat(analyzeThreePhotos().skinAge().estimatedSkinAge()).isEqualTo(80);
    }

    @Test
    @DisplayName("AI 피부 타입은 갭 카드를 건드리지 않는다 — observed 는 계속 규칙에서 나온다")
    void analyze_aiSkinTypeDoesNotReplaceGapCard() {
        givenUser(SkinType.OILY);
        givenSkinResult(demoResult("요약"));

        SkinAnalysisResponse response = analyzeThreePhotos();

        assertThat(response.skinType().primary()).isEqualTo(SkinType.DRY);
        assertThat(response.skinType().traits()).containsExactly(SkinTrait.SENSITIVE_TENDENCY);
        // 지표 38/52/64/25/78 → observe() 는 DRY. AI 가 뭘 말하든 이 값이 갭 카드를 만든다.
        assertThat(response.skinTypeGap().observed()).isEqualTo(SkinType.DRY);
    }

    @Test
    @DisplayName("모르는 타입·경향 값은 버린다 — 스키마 enum 은 OpenAI 쪽 약속일 뿐이다")
    void analyze_dropsUnknownSkinTypeValues() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78, null,
                new OpenAiSkinResult.SkinTypeResult("DRY", List.of("DEHYDRATED", "없는값")),
                null, "요약"));

        assertThat(analyzeThreePhotos().skinType().traits())
                .containsExactly(SkinTrait.DEHYDRATED);
    }

    @Test
    @DisplayName("뱃지와 등급이 같은 방향을 쓴다 — 한쪽만 뒤집히면 같은 줄에서 색이 엇갈린다")
    void analyze_badgeAndLevelAgreeOnDirection() {
        // 방향은 SkinMetrics 임계값 · SkinHighlightBuilder · metricDetails 세 곳에 따로 있다.
        // 한 곳에서 유분 방향을 뒤집어도 아무것도 안 깨지는 구조라, 여기서 묶어 둔다.
        givenUser(null);

        // 다섯 지표가 전부 "나쁜" 쪽 극단 — 뱃지는 셋 다 CAUTION, 등급은 전부 SEVERE 여야 한다
        givenSkinResult(metricsOnly(10, 90, 90, 90, 10));
        SkinAnalysisResponse worst = analyzeThreePhotos();

        assertThat(worst.metricDetails()).extracting(ScoredItemDto::level)
                .containsOnly(SkinLevel.SEVERE);
        assertThat(worst.highlights()).extracting(HighlightDto::status)
                .containsOnly(HighlightDto.HighlightStatus.CAUTION);

        // 반대 극단 — 뱃지는 전부 GOOD, 등급은 전부 EXCELLENT
        givenSkinResult(metricsOnly(90, 10, 10, 10, 90));
        SkinAnalysisResponse best = analyzeThreePhotos();

        assertThat(best.metricDetails()).extracting(ScoredItemDto::level)
                .containsOnly(SkinLevel.EXCELLENT);
        assertThat(best.highlights()).extracting(HighlightDto::status)
                .containsOnly(HighlightDto.HighlightStatus.GOOD);
    }

    @Test
    @DisplayName("AI 타입이 지표와 어긋나도 재분류하지 않는다 — 경고만 남기고 값은 그대로 내린다")
    void analyze_contradictingSkinTypeIsKeptNotReclassified() {
        givenUser(SkinType.OILY);
        // 수분 90 · 유분 10 인데 AI 가 OILY 라고 한다. 명백한 모순이다.
        givenSkinResult(new OpenAiSkinResult(true, 90, 10, 10, 10, 90, null,
                new OpenAiSkinResult.SkinTypeResult("OILY", List.of()), null, "요약"));

        SkinAnalysisResponse response = analyzeThreePhotos();

        // 값을 고치지 않는다 — 고치기 시작하면 규칙이 두 벌이 된다
        assertThat(response.skinType().primary()).isEqualTo(SkinType.OILY);
        // 갭 카드는 여전히 규칙에서 나온다 (PRD §14.3)
        assertThat(response.skinTypeGap().observed()).isEqualTo(SkinType.NORMAL);
    }

    @Test
    @DisplayName("근거가 길어도 잘라서 내린다 — DB 에 안 닿아 500 도 안 나고 화면만 깨진다")
    void analyze_trimsOverlongEvidenceAndAssessment() {
        givenUser(null);
        givenSkinResult(new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                new OpenAiSkinResult.MetricEvidence(
                        List.of("가".repeat(400)), List.of(), List.of(), List.of(), List.of()),
                null,
                new OpenAiSkinResult.SkinAgeAnalysis(29,
                        axis(72), axis(76), axis(28), axis(58), axis(42), axis(35), axis(60), axis(30),
                        "나".repeat(900)),
                "요약"));

        SkinAnalysisResponse response = analyzeThreePhotos();

        assertThat(response.metricDetails().get(0).evidence().get(0)).hasSize(60);
        assertThat(response.skinAge().assessment()).hasSize(300);
    }

    @Test
    @DisplayName("확장 필드가 없던 시절의 기록도 조회된다 — 점수·지표·뱃지는 그대로 나온다")
    void getLatest_readsAnalysisSavedBeforeTheseFields() {
        AppUser user = givenUser(null);
        // 예전 응답 모양. 근거·타입·나이가 통째로 없다.
        SkinAnalysis stored = SkinAnalysis.create(user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약",
                """
                {"faceDetected":true,"hydration":38,"oil":52,"redness":64,
                 "trouble":25,"barrier":78,"summary":"요약"}""");
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.of(stored));

        SkinAnalysisResponse response = skinAnalysisService.getLatest(USER_ID);

        assertThat(response.skinScore()).isEqualTo(55);
        assertThat(response.metricDetails()).hasSize(5);
        assertThat(response.metricDetails().get(0).evidence()).isEmpty();
        assertThat(response.skinType()).isNull();
        assertThat(response.skinAge()).isNull();
    }

    @Test
    @DisplayName("저장된 원본이 깨져 있어도 조회는 죽지 않는다")
    void getLatest_survivesUnreadableRawResponse() {
        AppUser user = givenUser(null);
        SkinAnalysis stored = SkinAnalysis.create(
                user, SkinMetrics.of(38, 52, 64, 25, 78), 55, "요약", "이건 JSON 이 아니다");
        given(skinAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(Optional.of(stored));

        SkinAnalysisResponse response = skinAnalysisService.getLatest(USER_ID);

        assertThat(response.skinScore()).isEqualTo(55);
        assertThat(response.metricDetails()).hasSize(5);
        assertThat(response.skinAge()).isNull();
    }

    // ---- 픽스처 ----

    /** 문서·Mock 의 시연 지표 38/52/64/25/78 에 확장 필드를 붙인 것. */
    private static OpenAiSkinResult demoResult(String summary) {
        return withSkinAge(29, summary);
    }

    private static OpenAiSkinResult withSkinAge(int estimatedSkinAge) {
        return withSkinAge(estimatedSkinAge, "요약");
    }

    private static OpenAiSkinResult withSkinAge(int estimatedSkinAge, String summary) {
        return new OpenAiSkinResult(true, 38, 52, 64, 25, 78,
                new OpenAiSkinResult.MetricEvidence(
                        List.of("볼과 입가에 부분적인 각질이 보임"),
                        List.of("T존에 중간 정도의 광택이 보임"),
                        List.of("코와 볼 주변에 붉은기가 뚜렷함"),
                        List.of("작은 융기가 소수만 보임"),
                        List.of("전반적인 피부결이 균일한 편임")),
                new OpenAiSkinResult.SkinTypeResult("DRY", List.of("SENSITIVE_TENDENCY")),
                new OpenAiSkinResult.SkinAgeAnalysis(estimatedSkinAge,
                        axis(72), axis(76), axis(28), axis(58),
                        axis(42), axis(35), axis(60), axis(30),
                        "비교적 젊은 피부 외관으로 보여요."),
                summary);
    }

    private static OpenAiSkinResult.Axis axis(int score) {
        return new OpenAiSkinResult.Axis(score, List.of("관찰 근거"));
    }

    /** 방향 검증용 — 확장 필드 없이 다섯 지표만 바꾼다. */
    private static OpenAiSkinResult metricsOnly(int hydration, int oil, int redness,
                                                int trouble, int barrier) {
        return new OpenAiSkinResult(true, hydration, oil, redness, trouble, barrier,
                null, null, null, "요약");
    }

    private AppUser givenUser(SkinType declaredSkinType) {
        AppUser user = AppUser.create("test@skinplate.app", "encoded", "테스트유저");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (declaredSkinType != null) user.declareSkinType(declaredSkinType);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    private void givenSkinResult(OpenAiSkinResult result) {
        given(visionClient.analyzeSkin(anyList())).willReturn(result);
    }

    private SkinAnalysisResponse analyzeThreePhotos() {
        return skinAnalysisService.analyze(USER_ID, jpegImage(), jpegImage(), jpegImage());
    }

    @SuppressWarnings("unchecked")
    private List<FacePhoto> capturePhotos() {
        ArgumentCaptor<List<FacePhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(visionClient).analyzeSkin(captor.capture());
        return captor.getValue();
    }

    private static byte lastByteOf(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64);
        return decoded[decoded.length - 1];
    }

    /** 앞 세 바이트가 JPEG 시그니처다. 형식 판별이 헤더가 아니라 여기를 본다. */
    private MultipartFile jpegImage() {
        return jpegImage((byte) 0xE0);
    }

    /** 마지막 바이트로 세 장을 구분한다 — 어느 사진이 어느 방향으로 갔는지 보려고. */
    private MultipartFile jpegImage(byte marker) {
        return new MockMultipartFile("photo", "face.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, marker});
    }
}
