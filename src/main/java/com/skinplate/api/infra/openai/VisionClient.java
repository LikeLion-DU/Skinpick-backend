package com.skinplate.api.infra.openai;

import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;

/**
 * Mock 은 이 인터페이스를 구현한다. 상속으로 두면 부모의 WebClient 생성자를
 * 억지로 만족시켜야 하고 부모 빈도 같이 뜬다. (설계서 §1.22 · PRD §17.4)
 */
public interface VisionClient {

    /**
     * @param mediaType 실제 바이트에서 판별한 image/jpeg 또는 image/png.
     *                  data URI 에 선언하는 값이라 내용과 어긋나면 OpenAI 가 요청을 거절한다.
     */
    OpenAiSkinResult analyzeSkin(String base64Image, String mediaType);

    OpenAiFoodResult analyzeFood(String base64Image, String mediaType);
}
