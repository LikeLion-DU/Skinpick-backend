package com.skinplate.api.global.image;

import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

/**
 * 업로드 이미지를 검증하고 Base64 로 바꾼다. 피부·음식 두 경로가 같은 규칙을 쓴다.
 *
 * 형식은 Content-Type 헤더가 아니라 실제 바이트로 판별한다. 헤더는 클라이언트가
 * 말하는 값이라, 모바일 갤러리가 application/octet-stream 을 보내면 멀쩡한 사진이
 * 400 으로 막히고, 반대로 헤더만 image/png 인 파일은 그대로 통과한다.
 *
 * 서버는 이미지를 저장하지 않는다. 여기서 만든 문자열을 OpenAI 에 보내고 버린다. (PRD §9.6)
 */
public final class ImageEncoder {

    private ImageEncoder() {}

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47};

    /** Base64 문자열과, data URI 에 선언할 미디어 타입. */
    public record EncodedImage(String base64, String mediaType) {}

    /**
     * 원본과 Base64 문자열을 AI 호출이 끝날 때까지 힙에 들고 있는다. 피부는 세 장이라
     * 최대치를 다 채운 요청 하나가 40MB 를 넘게 쓴다 — 그래도 상한을 걸지 않는다.
     * 배포 서버(가비아 2 vCore · 4GB)에서 심사위원 3명 동시를 최악 크기로 돌려도
     * 힙 471MiB / 기본 최대 1,024MiB 였고, 먼저 닿는 벽은 힙이 아니라 CPU 다.
     * (PRD §9.6 실측표)
     */
    public static EncodedImage encode(MultipartFile image) {
        return encode(image, null);
    }

    /**
     * @param subject 오류 메시지 앞에 붙일 대상 이름(예: "왼쪽 얼굴"). null 이면 일반 문구.
     *
     * 피부는 한 요청에 세 장이 온다. 어느 방향이 잘못됐는지 말해주지 않으면
     * 사용자가 셋 다 다시 찍는다. 음식은 한 장뿐이라 붙일 이름이 없다.
     */
    public static EncodedImage encode(MultipartFile image, String subject) {
        String prefix = subject == null ? "" : subject + " ";

        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE,
                    prefix + "사진이 비어 있습니다. 다시 촬영해 주세요.");
        }

        byte[] bytes = readBytes(image);
        String mediaType = detectMediaType(prefix, bytes);   // 인코딩 전에 막는다. 5MB 를 헛돌리지 않는다

        return new EncodedImage(Base64.getEncoder().encodeToString(bytes), mediaType);
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            // 업로드가 잘못된 게 아니라 서버가 파일을 못 읽은 것이다(임시 디렉터리 포화 등).
            // 400 으로 내리면 사용자는 멀쩡한 사진을 계속 다시 올리고,
            // 서버가 망가진 동안 5xx 지표(PRD §8.2)는 깨끗한 채로 남는다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    /** 판별한 타입은 OpenAI 의 data URI 에 그대로 선언된다. 내용과 어긋나면 400 이다. */
    private static String detectMediaType(String prefix, byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) return MediaType.IMAGE_JPEG_VALUE;
        if (startsWith(bytes, PNG_MAGIC))  return MediaType.IMAGE_PNG_VALUE;

        throw new BusinessException(ErrorCode.INVALID_IMAGE,
                prefix + "사진은 JPEG 또는 PNG 만 업로드할 수 있습니다.");
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        return bytes.length >= magic.length
                && Arrays.equals(bytes, 0, magic.length, magic, 0, magic.length);
    }
}
