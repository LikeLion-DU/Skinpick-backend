package com.skinplate.api.infra.openai.dto;

/**
 * Vision 에 올릴 얼굴 사진 한 장.
 *
 * 서버는 이미지를 저장하지 않으므로(PRD §9.6) 이 Base64 문자열이 유일한 사본이고,
 * AI 호출이 끝나면 그대로 버려진다. 로그에 찍지 않는다 — 얼굴 사진이다.
 *
 * @param mediaType 실제 바이트에서 판별한 image/jpeg 또는 image/png.
 *                  data URI 에 선언하는 값이라 내용과 어긋나면 OpenAI 가 요청을 거절한다.
 */
public record FacePhoto(FacePhotoType type, String base64, String mediaType) {
}
