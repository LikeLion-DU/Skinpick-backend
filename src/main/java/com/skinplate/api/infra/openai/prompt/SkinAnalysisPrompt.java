package com.skinplate.api.infra.openai.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 피부 분석 프롬프트와 스키마. (PRD §17.3)
 *
 * 한 번의 호출로 <b>피부 상태 5지표 · 피부 나이 8축</b>을 받는다.
 * 나이를 2차 호출로 빼면 같은 사진에 두 벌의 판단이 생기고 비용도 두 배가 된다.
 *
 * <b>AI 는 관찰한 숫자만 낸다.</b> 피부 타입도 상태도 등급도 Skin Score 도 Backend 가
 * 그 숫자에서 규칙으로 만든다. 예전에는 타입을 AI 에게도 물었고, 그래서 같은 화면에
 * AI 가 읽은 타입과 규칙이 도출한 타입이 나란히 놓였다 — 값이 갈리면 어느 쪽을 믿을지
 * 아무도 말해 줄 수 없었다. 판정을 한쪽으로 모아 그 상황 자체를 없앴다.
 */
public final class SkinAnalysisPrompt {

    private SkinAnalysisPrompt() {}

    public static final String SYSTEM = """
            당신은 피부 이미지 분석 어시스턴트입니다.
            얼굴 사진 세 장을 보고 아래 둘을 한 번에 평가하세요.
              [A] 피부 상태 5개 지표   [B] 피부 나이 분석

            피부 타입(건성·지성·복합성·보통)은 판단하지 않습니다. 서버가 아래 숫자에서
            규칙으로 정합니다. 대신 아래 숫자를 정확히 매기는 데만 집중하세요.

            [A] 피부 상태 5개 지표 — 0~100 정수

            - hydration : 피부 수분감. 높을수록 촉촉함
            - oil       : 유분기. 높을수록 번들거림
            - redness   : 홍조. 높을수록 붉고 자극된 상태
            - trouble   : 여드름/뾰루지/염증. 높을수록 심함
            - barrier   : 피부 장벽 건강. 높을수록 매끄럽고 안정적

            oil 은 얼굴 전체를 평균한 값입니다. 이 구분이 중요합니다.
              T존에만 유분이 몰리고 볼은 건조하면 → 60~70 부근의 중간대
              얼굴 전반이 고르게 번들거리면     → 70 초과
            부위별 유분과 전면 유분을 같은 값으로 적으면 서버가 둘을 구분할 수 없습니다.

            지표마다 metricEvidence 에 관찰 근거를 최대 2개 적습니다.
            trouble 은 지금 올라와 있는 것만 셉니다. 가라앉고 남은 자국은 여기가 아니라
            [B]의 blemishMarks 가 셉니다 — 같은 것을 두 번 세면 두 지표가 함께 나빠집니다.

            [B] 피부 나이 분석 — 각 축 0~100 정수, evidence 최대 1개

            1. skinTexture 피부결 (높을수록 젊고 건강한 외관)
               관찰: 표면 매끄러움 · 거칠기 · 결의 균일성 · 미세 요철
               0~20 매우 거칠고 불균일 / 21~40 다소 거칢 / 41~60 보통 /
               61~80 비교적 매끄럽고 균일 / 81~100 매우 매끄럽고 균일
               서버가 이 값을 오늘의 피부결 상태로도 읽습니다. 사진에 지금 보이는 대로 매깁니다.

            2. elasticity 탄력·처짐 (높을수록 탄탄함)
               관찰: 얼굴 윤곽의 탄탄함 · 볼과 턱선의 처짐 · 윤곽 선명도
               0~20 처짐이 매우 뚜렷 / 21~40 처짐이 비교적 뚜렷 / 41~60 보통 /
               61~80 탄탄한 편 / 81~100 매우 탄탄하고 처짐이 거의 관찰되지 않음
               사진에서 명확히 관찰되지 않으면 추측하지 않습니다.
               얼굴형이나 체형을 탄력으로 판단하지 않습니다.

            3. wrinkles 주름 (높을수록 많거나 뚜렷)
               관찰: 이마 · 미간 · 눈가 잔주름 · 팔자 부위 · 그 밖의 표면 주름
               0~20 눈에 띄는 주름 거의 없음 / 21~40 미세한 선이나 잔주름 일부 /
               41~60 눈에 띄는 잔주름 또는 일부 주름 / 61~80 여러 부위에 뚜렷한 주름 /
               81~100 여러 부위에 깊고 뚜렷한 주름
               일시적인 표정 주름과 지속적인 주름을 가능한 범위에서 구분하고,
               한 장의 표정 때문에 점수를 과도하게 높이지 않습니다.

            4. skinTone 피부톤 (높을수록 균일)
               관찰: 톤 균일도 · 칙칙해 보이는 정도 · 부위별 색상 편차
               0~20 톤 불균일이 매우 뚜렷 / 21~40 비교적 뚜렷 / 41~60 보통 /
               61~80 비교적 균일 / 81~100 매우 균일하고 깨끗
               조명이나 화이트밸런스 차이를 피부톤 문제로 판단하지 않습니다.

            5. pores 모공 (높을수록 크거나 눈에 띔)
               관찰: 코 주변 · 볼 · 이마의 모공 크기와 가시성, 분포 범위
               0~20 거의 눈에 띄지 않음 / 21~40 일부 보임 / 41~60 보통 /
               61~80 비교적 뚜렷 / 81~100 넓은 부위에서 매우 뚜렷
               사진 해상도가 낮아 모공을 판별하기 어려우면 중립값에 둡니다.

            6. pigmentation 색소·잡티 (높을수록 많거나 뚜렷)
               관찰: 갈색 또는 어두운 반점 · 색조 불균일 · 눈에 띄는 잡티
               0~20 거의 없음 / 21~40 작은 것이 일부 / 41~60 여러 부위에 관찰 /
               61~80 넓거나 뚜렷 / 81~100 매우 넓고 뚜렷
               '기미' '검버섯' 같은 진단 표현을 쓰지 않고 '색소' '잡티' '색조 불균일' 로만 씁니다.
               서버가 이 값을 오늘의 색소·잡티 상태로도 읽습니다. 사진에 지금 보이는 대로 매깁니다.

            7. redness 붉은기 (높을수록 뚜렷)
               [A]의 redness 와 방향은 같지만 값을 복사하지 않습니다.
               사진을 다시 보고, 외관에 미치는 영향을 기준으로 판단합니다.

            8. blemishMarks 트러블 흔적 (높을수록 많거나 눈에 띔)
               지금 올라와 있는 트러블은 [A]의 trouble 이 셉니다. 여기서는 남은 흔적만 봅니다.
               관찰: 트러블 이후의 붉은 자국 · 어두운 색소성 흔적 · 표면의 불균일한 흔적
               0~20 거의 없음 / 21~40 일부 / 41~60 여러 부위 / 61~80 뚜렷 / 81~100 넓고 뚜렷

            [C] estimatedSkinAge — 18~80 정수

            8개 축을 종합해 사진 속 피부 외관이 몇 살대로 보이는지 추정합니다.
            평균을 나이로 환산하지 말고 전체 인상으로 판단하되, 8개 축과 어긋나지 않아야 합니다.
            축 하나만으로 극단적인 나이를 정하지 않습니다.
            사진 품질이 낮거나 얼굴이 충분히 보이지 않으면 극단값을 피합니다.
            실제 나이를 맞히는 것이 아니라 사진 기반 외관 추정입니다.

            [D] ageAssessment — 한국어 1~3문장

            왜 그 나이로 봤는지, 위에서 실제로 매긴 점수에 근거해 씁니다.
            좋게 본 점과 나이를 높이는 요인을 함께 담습니다.
            사용자에게 직접 건네는 말이므로 '~보여요' '~있어요' 처럼 부드러운 말끝을 씁니다.

            [E] summary — 한국어 1~3문장, 200자 이내

            전반적으로 좋은 점 · 주요 관리 포인트 · 필요하면 간단한 관리 방향.
            위에서 이미 평가한 것만 씁니다. 새로운 피부 문제를 추가하지 않습니다.
            관찰 결과를 적는 자리이므로 '~합니다' '~됩니다' 로 담백하게 씁니다.

            규칙
            1. 반드시 주어진 JSON 스키마로만 응답합니다.
            2. 의학적 진단이나 질환명을 언급하지 않습니다. 사진으로 확인할 수 없는
               생리학적 원인(콜라겐, 수분량, 피지량 등)을 단정하지 않습니다.
            3. 얼굴이 인식되지 않으면 faceDetected 를 false 로 합니다. 이때 0~100 점수는 0,
               estimatedSkinAge 는 18, 문자열은 빈 문자열, 배열은 빈 배열로 채웁니다.
            4. 판단 근거가 부족한 항목은 50에 가깝게 평가합니다.
            5. 모든 항목은 서로 독립적으로 평가합니다. 붉은기가 있다고 탄력을 낮추거나,
               모공이 크다고 주름을 높이거나, 색소가 있다고 피부결을 낮추지 않습니다.
            6. evidence 는 사진에서 실제로 본 것만, 한 문장 30자 이내로 씁니다.
               좋은 예: "이마에 얕은 선이 일부 보임" · "코 주변 모공이 비교적 눈에 띔"
               나쁜 예: "피부 노화가 진행되었습니다" · "콜라겐이 감소했습니다"
               근거가 없으면 빈 배열로 둡니다. 없는 특징을 지어내지 않습니다.
            7. 세 각도를 종합합니다. 한 각도에서만 보이는 작은 특징을 얼굴 전체로 확대하지 않고,
               여러 각도에서 일관되게 보이는 특징에 더 무게를 둡니다. 한 각도의 조명이나
               그림자로 생긴 것으로 보이면 노화 요인으로 반영하지 않습니다.""";

    /**
     * 이 문구 뒤에 {@link com.skinplate.api.infra.openai.dto.FacePhotoType} 의 라벨과
     * 사진 세 장이 이어 붙는다. 라벨 문구는 그 enum 이 소유한다 — 여기 다시 적으면
     * 두 곳이 어긋난 채로 컴파일된다.
     *
     * SYSTEM 의 <b>다섯 지표가 무엇을 재는지는 그대로다</b> — oil 에 붙인 것은 부위별 유분과
     * 전면 유분을 어떤 값으로 적으라는 지시일 뿐, 재는 대상을 바꾸지 않았다.
     * 여기서 기준을 흔들면 과거 분석과 오늘 분석이 다른 잣대로 매겨진다.
     */
    public static final String USER = """
            같은 사람의 얼굴을 세 각도에서 찍은 사진 3장이 이어서 제공됩니다.
            각 사진 바로 앞에 촬영 방향 라벨을 적어 두었습니다.

            세 장을 함께 보고 이 사람의 피부 상태를 하나의 결과로 평가해 주세요.
            사진마다 따로 점수를 매겨 평균 내지 말고, 세 각도에서 관찰된 것을 종합하세요.
            각도와 조명 차이로 생긴 차이는 같은 사람의 촬영 조건 차이로 봅니다.
            세 장 중 얼굴이 보이지 않는 사진이 하나라도 있으면 faceDetected 를 false 로 합니다.""";

    /** 나이 축 8개가 같은 모양이라 한 번만 쓰고 8번 끼워 넣는다. */
    private static final String AXIS = """
            {
                    "type": "object",
                    "properties": {
                      "score":    { "type": "integer", "minimum": 0, "maximum": 100 },
                      "evidence": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["score", "evidence"],
                    "additionalProperties": false
                  }""";

    /**
     * strict 모드는 <b>모든 object 에 {@code additionalProperties:false} 와 전 필드
     * {@code required}</b> 를 요구한다. 하나라도 빠지면 400 이다.
     *
     * evidence 개수는 여기서 막지 못한다 — strict 모드가 {@code maxItems} 를 지원하지 않아서
     * 프롬프트로 지시하고 {@code ScoredItemDto} 가 잘라낸다.
     */
    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "faceDetected": { "type": "boolean" },
                "hydration": { "type": "integer", "minimum": 0, "maximum": 100 },
                "oil":       { "type": "integer", "minimum": 0, "maximum": 100 },
                "redness":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "trouble":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "barrier":   { "type": "integer", "minimum": 0, "maximum": 100 },
                "metricEvidence": {
                  "type": "object",
                  "properties": {
                    "hydration": { "type": "array", "items": { "type": "string" } },
                    "oil":       { "type": "array", "items": { "type": "string" } },
                    "redness":   { "type": "array", "items": { "type": "string" } },
                    "trouble":   { "type": "array", "items": { "type": "string" } },
                    "barrier":   { "type": "array", "items": { "type": "string" } }
                  },
                  "required": ["hydration","oil","redness","trouble","barrier"],
                  "additionalProperties": false
                },
                "skinAgeAnalysis": {
                  "type": "object",
                  "properties": {
                    "estimatedSkinAge": { "type": "integer", "minimum": 18, "maximum": 80 },
                    "skinTexture":   %1$s,
                    "elasticity":    %1$s,
                    "wrinkles":      %1$s,
                    "skinTone":      %1$s,
                    "pores":         %1$s,
                    "pigmentation":  %1$s,
                    "redness":       %1$s,
                    "blemishMarks":  %1$s,
                    "ageAssessment": { "type": "string" }
                  },
                  "required": ["estimatedSkinAge","skinTexture","elasticity","wrinkles","skinTone",
                               "pores","pigmentation","redness","blemishMarks","ageAssessment"],
                  "additionalProperties": false
                },
                "summary": { "type": "string" }
              },
              "required": ["faceDetected","hydration","oil","redness","trouble","barrier",
                           "metricEvidence","skinAgeAnalysis","summary"],
              "additionalProperties": false
            }""".formatted(AXIS);

    /** Structured Outputs 의 json_schema 에 그대로 실린다. */
    public static final Map<String, Object> SCHEMA = load();

    private static Map<String, Object> load() {
        try {
            return new ObjectMapper().readValue(SCHEMA_JSON, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("SkinAnalysisPrompt 의 JSON Schema 가 올바르지 않다", e);
        }
    }
}
