package com.skinplate.api.global.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 플랫폼의 헬스체크 대상이자, 스핀다운을 막는 외부 핑의 목표다.
 * 공통 응답 래퍼를 쓰지 않는다 — 플랫폼이 파싱하는 건 상태 코드뿐이고,
 * DB 를 건드리지 않아야 DB 가 흔들려도 컨테이너가 재시작되지 않는다.
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
