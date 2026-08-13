package com.skinplate.api.infra.openai.dto;

/**
 * 피부 분석에 쓰는 얼굴 사진의 촬영 방향.
 *
 * 어느 사진이 어느 방향인지는 multipart 파트 이름(front · left · right)에서 정해진다.
 * 배열 순서로 구분하지 않는다 — 순서는 클라이언트가 정하는 값이라, 한 장이 밀리면
 * 왼쪽 사진이 정면으로 라벨링된 채 그대로 분석된다.
 *
 * [label] 은 프롬프트에서 사진 바로 앞에 붙는 라벨이다. (지시서 §4 · §8)
 */
public enum FacePhotoType {

    FRONT("정면"),
    LEFT("왼쪽 얼굴"),
    RIGHT("오른쪽 얼굴");

    private final String label;

    FacePhotoType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
