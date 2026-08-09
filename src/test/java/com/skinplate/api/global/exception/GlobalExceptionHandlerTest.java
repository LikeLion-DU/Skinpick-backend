package com.skinplate.api.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 오류 400 계열은 실제 경로가 생기기 전까지 살아있는 서버로 확인할 수 없다.
 * 그런데 이 세 가지는 프론트 통합 첫날에 바로 나는 것들이고,
 * 포괄 핸들러로 새면 500 + 스택트레이스가 되어 원인이 백엔드로 온다.
 *
 * 컨트롤러 하나를 테스트 소스에만 두고 실제 핸들러를 붙여 고정한다.
 * 운영 코드에는 검증용 경로를 만들지 않는다.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("multipart 파트 이름이 어긋나면 400 — 프론트가 image 를 file 로 보내는 그 경우다")
    void wrongMultipartPartName() throws Exception {
        MockMultipartFile wrongName = new MockMultipartFile(
                "file", "face.jpg", MediaType.IMAGE_JPEG_VALUE, "bytes".getBytes());

        mockMvc.perform(multipart("/probe/upload").file(wrongName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("본문이 깨진 JSON 이면 400")
    void brokenJsonBody() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("경로 변수 타입이 안 맞으면 400 — /plates/abc 같은 요청이다")
    void pathVariableTypeMismatch() throws Exception {
        mockMvc.perform(get("/probe/42abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 400")
    void missingRequiredParameter() throws Exception {
        mockMvc.perform(get("/probe/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping("/upload")
        String upload(@RequestPart("image") org.springframework.web.multipart.MultipartFile image) {
            return "ok";
        }

        @PostMapping("/body")
        String body(@RequestBody java.util.Map<String, String> payload) {
            return "ok";
        }

        @GetMapping("/{id}")
        String byId(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/search")
        String search(@RequestParam String keyword) {
            return "ok";
        }
    }
}
