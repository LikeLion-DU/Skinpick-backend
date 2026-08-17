package com.skinplate.api.infra.openai;

import com.skinplate.api.infra.openai.dto.FacePhoto;
import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;
import com.skinplate.api.infra.openai.dto.PlateComments;
import com.skinplate.api.infra.openai.dto.SkinInsightSentences;
import com.skinplate.api.infra.openai.dto.WeeklyComment;

import java.util.List;

/**
 * Mock 은 이 인터페이스를 구현한다. 상속으로 두면 부모의 WebClient 생성자를
 * 억지로 만족시켜야 하고 부모 빈도 같이 뜬다. (설계서 §1.22 · PRD §17.4)
 */
public interface VisionClient {

    /**
     * 정면·왼쪽·오른쪽 세 장을 <b>한 번의 요청</b>으로 보내 하나의 결과를 받는다.
     *
     * 사진마다 따로 호출하면 지표가 세 벌 나오고, 그걸 평균 내는 순간 점수가
     * 촬영 각도에 흔들린다 — 재현성이 이 제품의 주장이다. 비용도 요청 수만큼 는다.
     */
    OpenAiSkinResult analyzeSkin(List<FacePhoto> photos);

    /**
     * @param mediaType 실제 바이트에서 판별한 image/jpeg 또는 image/png.
     *                  data URI 에 선언하는 값이라 내용과 어긋나면 OpenAI 가 요청을 거절한다.
     */
    OpenAiFoodResult analyzeFood(String base64Image, String mediaType);

    /**
     * 룰 엔진의 결과를 사람이 읽을 문장으로 옮긴다. <b>판단은 이미 끝나 있다</b> —
     * userContext 에 실린 평가만 문장이 되고, 점수·등급은 여기서 만들지 않는다.
     * (PRD §18.9 — 음식 선정은 규칙, 문장 생성만 AI)
     */
    PlateComments generateComments(String userContext);

    /**
     * 개인화 인사이트의 문장을 만든다. <b>판단은 이미 끝나 있다</b> — 다룰 주제도
     * 그 순서도 userContext 에 실려 오고, AI 는 주제마다 문장 하나씩만 채운다.
     * (PRD §18.10 — 주제 선정은 규칙, 문장 생성만 AI)
     */
    SkinInsightSentences generateSkinInsight(String userContext);

    /**
     * 주간 리포트의 문장을 만든다. <b>판단은 이미 끝나 있다</b> — 평균 점수도 BEST DAY 도
     * 서버가 집계해 userContext 에 싣고, AI 는 그 표를 설명하기만 한다.
     * 한 주치 음식 기록 원문은 넘기지 않는다(WeeklyReportPrompt 참조).
     */
    WeeklyComment generateWeeklyComment(String userContext);
}
