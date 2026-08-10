package com.skinplate.api.infra.openai;

import com.skinplate.api.infra.openai.dto.OpenAiFoodResult;
import com.skinplate.api.infra.openai.dto.OpenAiSkinResult;

/**
 * Mock 은 이 인터페이스를 구현한다. 상속으로 두면 부모의 WebClient 생성자를
 * 억지로 만족시켜야 하고 부모 빈도 같이 뜬다. (설계서 §1.22 · PRD §17.4)
 */
public interface VisionClient {

    OpenAiSkinResult analyzeSkin(String base64Image);

    OpenAiFoodResult analyzeFood(String base64Image);
}
