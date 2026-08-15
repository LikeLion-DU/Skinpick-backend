package com.skinplate.api.infra.openai.dto;

/**
 * 피부 분석에 쓰는 얼굴 사진의 촬영 방향.
 *
 * 어느 사진이 어느 방향인지는 multipart 파트 이름(front · left · right)에서 정해진다.
 * 배열 순서로 구분하지 않는다 — 순서는 클라이언트가 정하는 값이라, 한 장이 밀리면
 * 왼쪽 사진이 정면으로 라벨링된 채 그대로 분석된다.
 *
 * [label] 은 프롬프트에서 사진 바로 앞에 붙는 라벨이고, [subject] 는 사용자에게
 * 보이는 오류 메시지의 주어("왼쪽으로 돌린 사진이 비어 있습니다")다. (지시서 §4 · §8)
 *
 * LEFT 는 "고개를 본인 왼쪽으로 돌리고 찍은 사진"이다 — 사진에는 **오른쪽 뺨**이
 * 보인다. 앱 지시문("고개를 왼쪽으로 돌려주세요") 기준을 따르기로 한 결정이다
 * (2026-08-15, 프론트 FacePhotoType 주석과 쌍). 라벨을 "왼쪽 얼굴"로 쓰면 Vision 이
 * 보이는 뺨과 라벨이 어긋난 사진을 받게 되어 summary 가 좌우를 바꿔 말할 수 있다.
 */
public enum FacePhotoType {

    FRONT("정면", "정면"),
    LEFT("고개를 왼쪽으로 돌린 측면 — 오른쪽 뺨이 보임", "왼쪽으로 돌린"),
    RIGHT("고개를 오른쪽으로 돌린 측면 — 왼쪽 뺨이 보임", "오른쪽으로 돌린");

    private final String label;
    private final String subject;

    FacePhotoType(String label, String subject) {
        this.label = label;
        this.subject = subject;
    }

    public String getLabel() {
        return label;
    }

    public String getSubject() {
        return subject;
    }
}
