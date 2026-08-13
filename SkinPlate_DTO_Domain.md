# Skin Plate — DTO & 도메인 구조 설계서

> PRD v1.1 기반 · 빌드 가능한 스켈레톤 · Backend(Spring Boot) + Frontend(Flutter)

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.8 |
| 기준 문서 | Skin Plate PRD & Technical Design **v1.7** |
| 범위 | 설정, 마이그레이션, Entity, Enum, Repository, DTO, Rule Engine 골격 (Backend) / DTO, Entity, Repository 인터페이스 (Flutter) |
| 제외 | Service·Controller 구현체, OpenAI 호출 구현, UI 위젯 |

---

## 이 문서를 쓰는 법

각 코드 블록 위에 **파일 경로**가 적혀 있다. 그대로 만들면 컴파일된다.

작업 순서 권장:

1. `build.gradle` → `docker compose up -d postgres` → `V1__init.sql`
2. Enum·값 객체 → Entity → Repository (여기까지 하면 앱이 뜬다)
3. DTO → Rule Engine 골격
4. Flutter `pubspec.yaml` → `core/` → DTO → `build_runner`

> **먼저 읽을 것** — 이 스켈레톤에는 Service와 Controller가 없다. 그 둘은 팀에서 직접 작성하는 편이 낫다. 대신 **그것들이 딛고 설 바닥**(타입, 계약, 제약)은 전부 들어 있다. 바닥이 흔들리면 위에서 아무리 잘 써도 무너지고, 바닥이 단단하면 위는 하루면 얹는다.

---

# Part 1 · Backend (Spring Boot)

## 1.1 전체 파일 목록

```
skinplate-api/
├── build.gradle
├── settings.gradle
├── docker-compose.yml
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── application-prod.yml
│   └── db/migration/  V1__init.sql · V2__drop_image_url.sql
└── src/main/java/com/skinplate/api/
    ├── SkinPlateApplication.java
    ├── global/
    │   ├── common/       ApiResponse · BaseTimeEntity
    │   ├── exception/    ErrorCode · BusinessException · GlobalExceptionHandler
    │   ├── config/       SecurityConfig · JpaConfig · WebConfig · OpenAiConfig · SwaggerConfig
    │   ├── security/     JwtTokenProvider · JwtAuthenticationFilter
    │   │                 JwtAuthenticationEntryPoint · CurrentUser
    │   └── init/         TestAccountInitializer
    ├── domain/
    │   ├── auth/
    │   │   └── dto/         SignupRequest · LoginRequest · TestLoginRequest
    │   │                    UpdateProfileRequest · AuthResponse · MeResponse
    │   ├── user/
    │   │   ├── entity/      AppUser · Role · SkinType
    │   │   ├── repository/  AppUserRepository
    │   │   └── dto/         UserSummaryDto
    │   ├── skin/
    │   │   ├── entity/      SkinAnalysis · SkinMetrics
    │   │   ├── service/     SkinScoreCalculator · SkinHighlightBuilder
    │   │   │                SkinTypeGapAnalyzer
    │   │   ├── repository/  SkinAnalysisRepository
    │   │   └── dto/         SkinAnalysisResponse · SkinMetricsDto · HighlightDto
    │   │                    SkinTypeGapDto
    │   ├── food/
    │   │   ├── entity/      FoodAnalysis · Nutrition · FoodIngredient
    │   │   │                IngredientTag · CookingMethod
    │   │   ├── service/     StandardNutrition
    │   │   ├── repository/  FoodAnalysisRepository
    │   │   └── dto/         FoodAnalysisDto · NutritionDto · IngredientDto
    │   ├── plate/
    │   │   ├── entity/      SkinPlate · SkinPlateFeedback · FeedbackType
    │   │   │                PlateActionCode
    │   │   ├── repository/  SkinPlateRepository
    │   │   ├── dto/         SkinPlateResponse · SkinPlateCreateRequest
    │   │   │                FeedbackGroupDto · FeedbackDto · ActionDto
    │   │   │                PlateSimulateRequest · PlateSimulateResponse
    │   │   └── engine/      PlateRule · PlateContext · RuleResult
    │   │       │            PlateEvaluation · PlateRuleEngine
    │   │       │            RuleConstants · SeverityCalculator
    │   │       └── rules/   SodiumRule · SpicyRednessRule · SugarTroubleRule
    │   │                    FriedOilRule · HydrationFoodRule · Omega3BarrierRule
    │   │                    ProteinRule · VitaminRule · ProbioticRule
    │   └── recommendation/
    │       ├── entity/      Recommendation · RecommendationType
    │       ├── service/     RecommendationCandidates
    │       ├── repository/  RecommendationRepository
    │       └── dto/         RecommendationResponse · RecommendedFoodDto
    └── infra/openai/dto/    OpenAiSkinResult · OpenAiFoodResult
                             (infra/storage 없음 — 이미지를 저장하지 않는다)

src/test/java/com/skinplate/api/domain/plate/engine/
└── PlateRuleEngineTest.java        # PRD 예시 60점 / 87점 재현
```

---

## 1.2 빌드 설정

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.skinplate'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

configurations {
    compileOnly { extendsFrom annotationProcessor }
}

repositories { mavenCentral() }

dependencies {
    // Web / JPA / Validation
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'   // OpenAI WebClient

    // Security + JWT
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // DB
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // Docs
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

    // Lombok
    compileOnly        'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testCompileOnly        'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
}

tasks.named('test') { useJUnitPlatform() }
```

**`settings.gradle`**

```groovy
rootProject.name = 'skinplate-api'
```

**`docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: skinplate-db
    environment:
      POSTGRES_DB: skinplate
      POSTGRES_USER: skinplate
      POSTGRES_PASSWORD: skinplate
    ports:
      - "5432:5432"
    volumes:
      - skinplate-pgdata:/var/lib/postgresql/data

volumes:
  skinplate-pgdata:
```

---

## 1.3 애플리케이션 설정

**`src/main/resources/application.yml`**

```yaml
spring:
  application.name: skinplate-api
  # 배포 시 SPRING_PROFILES_ACTIVE=prod 를 반드시 넘긴다.
  # 안 넘기면 테스트 계정 3개와 /auth/test-login 이 열린 채로 뜬다.
  profiles.active: ${SPRING_PROFILES_ACTIVE:local}

  datasource:
    # 기본값은 로컬 Docker Postgres 기준이다.
    # 배포(Supabase)에서는 DB_NAME 을 반드시 postgres 로 넘긴다 — skinplate 가 아니다.
    # 안 넘기면 기동이 FATAL: database "skinplate" does not exist 로 끝난다. (PRD §9.6)
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:skinplate}
    username: ${DB_USER:skinplate}
    password: ${DB_PASSWORD:skinplate}

  jpa:
    hibernate.ddl-auto: validate        # 스키마는 Flyway가 소유한다
    open-in-view: false
    properties:
      hibernate.format_sql: true
      hibernate.default_batch_fetch_size: 100

  flyway:
    enabled: true
    baseline-on-migrate: true

  servlet.multipart:
    max-file-size: 5MB
    max-request-size: 20MB          # 피부 분석이 5MB 짜리 세 장을 한 요청에 싣는다

  jackson:
    default-property-inclusion: non_null
    serialization.write-dates-as-timestamps: false

app:
  auth:
    jwt:
      secret: ${JWT_SECRET}                        # 32바이트 이상
      validity-seconds: 604800                     # 7일
    test-account:
      enabled: ${TEST_ACCOUNT_ENABLED:true}
      password: ${TEST_ACCOUNT_PASSWORD:test1234!}
  ai:
    api-key: ${OPENAI_API_KEY:}
    base-url: https://api.openai.com/v1
    model: gpt-4o
    timeout-seconds: 25            # 타임아웃은 재시도 없음. 429만 1회 재시도 (피부는 high 3장)
    mock: ${AI_MOCK:false}

springdoc:
  swagger-ui.path: /swagger-ui/index.html

logging.level:
  com.skinplate.api: DEBUG
```

**`src/main/resources/application-local.yml`**

```yaml
app:
  auth.test-account.enabled: true
  ai.mock: ${AI_MOCK:false}

logging.level:
  org.hibernate.SQL: DEBUG
```

**`src/main/resources/application-prod.yml`**

```yaml
app:
  auth.test-account.enabled: false     # ★ 운영에서는 테스트 계정 비활성화

logging.level:
  com.skinplate.api: INFO
```

> **`open-in-view: false`를 기본값으로 둔 이유** — 켜져 있으면 컨트롤러에서 LAZY 필드를 건드릴 때 조용히 쿼리가 나간다. 개발 중에는 편하지만, 어느 API가 몇 번 쿼리하는지 아무도 모르게 된다. 꺼두면 `LazyInitializationException`이 개발 시점에 바로 터져서 "Service 안에서 DTO로 변환해라"를 강제한다.

---

## 1.4 DB 마이그레이션

> **아래 V1 블록은 최종 스키마다.** 실제 저장소의 `V1__init.sql` 은 `image_url` 을 포함한
> 상태로 이미 커밋돼 있고(마이그레이션은 수정하지 않는다), `V2__drop_image_url.sql` 이
> 그 컬럼을 제거한다. 이 문서를 보고 새로 만들면 V1 에 애초에 컬럼이 없으므로
> V2 는 `DROP COLUMN IF EXISTS` 로 두 경우를 모두 견딘다.

**`src/main/resources/db/migration/V1__init.sql`**

```sql
-- ============================================================
-- Skin Plate 초기 스키마 (PRD v1.1 §12 ERD)
-- ============================================================

CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(100) NOT NULL,
    password        VARCHAR(100) NOT NULL,          -- BCrypt 해시
    nickname        VARCHAR(30)  NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_test_account BOOLEAN      NOT NULL DEFAULT FALSE,
    declared_skin_type VARCHAR(20),                    -- NULL = 아직 안 정함(건너뜀)
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP
);
CREATE UNIQUE INDEX idx_app_user_email ON app_user (lower(email));

CREATE TABLE skin_analysis (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id),
    skin_score       INT          NOT NULL,
    hydration        INT          NOT NULL,
    oil              INT          NOT NULL,
    redness          INT          NOT NULL,
    trouble          INT          NOT NULL,
    barrier          INT          NOT NULL,
    summary          VARCHAR(300),
    raw_ai_response  JSONB,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT ck_skin_score     CHECK (skin_score BETWEEN 0 AND 100),
    CONSTRAINT ck_skin_hydration CHECK (hydration  BETWEEN 0 AND 100),
    CONSTRAINT ck_skin_oil       CHECK (oil        BETWEEN 0 AND 100),
    CONSTRAINT ck_skin_redness   CHECK (redness    BETWEEN 0 AND 100),
    CONSTRAINT ck_skin_trouble   CHECK (trouble    BETWEEN 0 AND 100),
    CONSTRAINT ck_skin_barrier   CHECK (barrier    BETWEEN 0 AND 100)
);
CREATE INDEX idx_skin_analysis_user_created ON skin_analysis (user_id, created_at DESC);

CREATE TABLE food_analysis (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id),
    food_name       VARCHAR(100) NOT NULL,
    food_category   VARCHAR(50),
    calories_kcal   INT          NOT NULL DEFAULT 0,
    protein_g       NUMERIC(6,2) NOT NULL DEFAULT 0,
    fat_g           NUMERIC(6,2) NOT NULL DEFAULT 0,
    carb_g          NUMERIC(6,2) NOT NULL DEFAULT 0,
    sodium_mg       INT          NOT NULL DEFAULT 0,
    sugar_g         NUMERIC(6,2) NOT NULL DEFAULT 0,
    cooking_method  VARCHAR(20)  NOT NULL DEFAULT 'ETC',
    is_spicy        BOOLEAN      NOT NULL DEFAULT FALSE,
    raw_ai_response JSONB,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_food_analysis_user_created ON food_analysis (user_id, created_at DESC);

CREATE TABLE food_ingredient (
    id               BIGSERIAL PRIMARY KEY,
    food_analysis_id BIGINT       NOT NULL REFERENCES food_analysis (id) ON DELETE CASCADE,
    name             VARCHAR(50)  NOT NULL,
    tag              VARCHAR(20)  NOT NULL DEFAULT 'ETC',
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP
);
CREATE INDEX idx_food_ingredient_food ON food_ingredient (food_analysis_id);

CREATE TABLE skin_plate (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT    NOT NULL REFERENCES app_user (id),
    skin_analysis_id BIGINT    NOT NULL REFERENCES skin_analysis (id),
    food_analysis_id BIGINT    NOT NULL UNIQUE REFERENCES food_analysis (id),
    plate_score      INT       NOT NULL,
    summary          VARCHAR(300),
    applied_rules    JSONB,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT ck_plate_score CHECK (plate_score BETWEEN 0 AND 100)
);
CREATE INDEX idx_skin_plate_user_created ON skin_plate (user_id, created_at DESC);

CREATE TABLE skin_plate_feedback (
    id             BIGSERIAL PRIMARY KEY,
    skin_plate_id  BIGINT       NOT NULL REFERENCES skin_plate (id) ON DELETE CASCADE,
    type           VARCHAR(20)  NOT NULL,           -- GOOD / CAUTION / ACTION
    message        VARCHAR(200) NOT NULL,
    score_delta    INT          NOT NULL DEFAULT 0, -- GOOD / CAUTION 행
    expected_gain  INT          NOT NULL DEFAULT 0, -- ACTION 행
    rule_code      VARCHAR(30),
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP
);
CREATE INDEX idx_feedback_plate ON skin_plate_feedback (skin_plate_id);

CREATE TABLE recommendation (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id),
    skin_analysis_id BIGINT       NOT NULL REFERENCES skin_analysis (id),
    type             VARCHAR(20)  NOT NULL,          -- RECOMMEND / AVOID
    food_name        VARCHAR(100) NOT NULL,
    reason           VARCHAR(300),
    display_order    INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP
);
CREATE INDEX idx_recommendation_skin ON recommendation (skin_analysis_id);
```

> **CHECK 제약을 넣은 이유** — 0~100 범위는 애플리케이션에서도 검증하지만, AI가 이상한 값을 뱉었을 때 **DB에서 한 번 더 막힌다**. 잘못된 데이터가 저장되고 나면 원인 추적에 반나절이 든다. 저장 자체를 실패시키면 스택트레이스가 바로 나온다.
>
> `food_analysis_id`가 `UNIQUE`인 것도 의도적이다. 음식 사진 1장 = Plate 1건이라는 관계(PRD ERD의 `||--||`)를 DB가 보증한다.

---

## 1.5 애플리케이션 진입점

**`SkinPlateApplication.java`**

```java
package com.skinplate.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SkinPlateApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkinPlateApplication.class, args);
    }
}
```

---

## 1.6 global/common

**`global/common/ApiResponse.java`**

```java
package com.skinplate.api.global.common;

import com.skinplate.api.global.exception.ErrorCode;

/**
 * 모든 API의 공통 응답 래퍼.
 * 성공: { success: true,  data: {...}, error: null }
 * 실패: { success: false, data: null,  error: { code, message } }
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message));
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return fail(code, code.getMessage());
    }

    public record ErrorBody(String code, String message) {}
}
```

**`global/common/BaseTimeEntity.java`**

```java
package com.skinplate.api.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

---

## 1.7 global/exception

**`global/exception/ErrorCode.java`**

```java
package com.skinplate.api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ---- 인증 ----
    INVALID_INPUT       (HttpStatus.BAD_REQUEST,  "요청 값이 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,     "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS (HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED        (HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TOKEN_EXPIRED       (HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    TEST_LOGIN_DISABLED (HttpStatus.FORBIDDEN,    "테스트 로그인이 비활성화된 환경입니다."),
    USER_NOT_FOUND      (HttpStatus.NOT_FOUND,    "사용자를 찾을 수 없습니다."),

    // ---- 이미지 / AI ----
    INVALID_IMAGE       (HttpStatus.BAD_REQUEST,          "이미지 형식 또는 용량이 올바르지 않습니다."),
    FACE_NOT_DETECTED   (HttpStatus.UNPROCESSABLE_ENTITY, "얼굴을 인식하지 못했습니다. 밝은 곳에서 다시 촬영해 주세요."),
    FOOD_NOT_DETECTED   (HttpStatus.UNPROCESSABLE_ENTITY, "음식을 인식하지 못했습니다. 다시 촬영해 주세요."),
    AI_ANALYSIS_FAILED  (HttpStatus.BAD_GATEWAY,          "분석에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    AI_TIMEOUT          (HttpStatus.GATEWAY_TIMEOUT,      "분석이 지연되고 있습니다. 다시 시도해 주세요."),
    RATE_LIMIT_EXCEEDED (HttpStatus.TOO_MANY_REQUESTS,    "오늘 분석 가능 횟수를 모두 사용했습니다."),

    // ---- 리소스 ----
    SKIN_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."),
    FOOD_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "음식 분석 결과를 찾을 수 없습니다."),
    PLATE_NOT_FOUND        (HttpStatus.NOT_FOUND, "Skin Plate를 찾을 수 없습니다."),

    // ---- 요청 형식 ----
    RESOURCE_NOT_FOUND (HttpStatus.NOT_FOUND,          "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED (HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다."),

    // ---- 기타 ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
```

> **타인의 리소스에 접근했을 때도 `*_NOT_FOUND`(404)를 던진다.** 403을 주면 "존재하지만 권한 없음"을 알려주는 셈이라 id를 훑어 남의 데이터 개수를 셀 수 있다. 이 규칙은 Repository의 `findByIdAndUserId`와 한 쌍이다.

**`global/exception/BusinessException.java`**

```java
package com.skinplate.api.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
```

**`global/exception/GlobalExceptionHandler.java`**

```java
package com.skinplate.api.global.exception;

import com.skinplate.api.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code, e.getMessage()));
    }

    /** @Valid 검증 실패 → 첫 번째 필드 메시지를 그대로 사용자에게 보여준다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.joining(" "));

        if (message.isBlank()) message = ErrorCode.INVALID_INPUT.getMessage();

        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, message));
    }

    /**
     * 존재하지 않는 경로. Spring Boot 3.2+ 는 NoResourceFoundException 을 던지는데,
     * 아래 포괄 핸들러가 이걸 삼키면 모든 오타 경로가 500 이 된다.
     * 프론트는 "일시적인 오류가 발생했습니다"를 보고 원인을 백엔드에서 찾게 되고,
     * PRD §8.2 의 "5xx 에러율 ≤ 1%" 지표도 404 로 오염된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로: {}", e.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        log.warn("허용되지 않은 메서드: {}", e.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 요청이 잘못된 경우들. 전부 클라이언트 잘못이므로 400 이고 log.warn 이다.
     * 포괄 핸들러에 맡기면 500 + 스택트레이스가 되어, 프론트 통합 중
     * 로그가 남의 오타로 가득 찬다.
     *
     * MissingServletRequestPartException 이 특히 중요하다 —
     * multipart 의 파트 이름을 "image" 가 아닌 것으로 보내면 여기 걸린다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,          // 본문이 없거나 깨진 JSON
            MethodArgumentTypeMismatchException.class,      // /plates/abc 같은 타입 불일치
            MissingServletRequestPartException.class,       // multipart 파트 누락
            MissingServletRequestParameterException.class   // 필수 쿼리 파라미터 누락
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.INVALID_IMAGE.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_IMAGE, "이미지는 5MB 이하만 업로드할 수 있습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
```

> **검증 메시지를 사용자에게 그대로 노출한다.** DTO의 `@Pattern(message = "...")`에 이미 사용자용 한국어 문장을 써 두었기 때문이다. "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다."는 프론트가 다시 작성할 필요 없이 그대로 화면에 띄우면 된다.

---

## 1.8 global/security

**`global/security/CurrentUser.java`**

```java
package com.skinplate.api.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * 컨트롤러 파라미터에 인증된 사용자 ID(Long)를 주입한다.
 *
 *   public ResponseEntity<?> analyze(@CurrentUser Long userId, ...)
 *
 * SecurityConfig가 인증되지 않은 요청을 전부 차단하므로 null이 될 수 없다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {}
```

**`global/security/JwtTokenProvider.java`**

```java
package com.skinplate.api.global.security;

import com.skinplate.api.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;

    /** 토큰 유효기간(초). 응답의 expiresIn 필드에 그대로 사용된다. */
    @Getter
    private final long validitySeconds;

    public JwtTokenProvider(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.validity-seconds}") long validitySeconds) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "app.auth.jwt.secret 은 32바이트 이상이어야 합니다. 현재 " + bytes.length + "바이트");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validitySeconds = validitySeconds;
    }

    public String createToken(Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 userId 반환. 실패 시 JwtException 계열을 던진다. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
```

> **Secret 길이를 생성자에서 검증한다.** HS256은 32바이트 미만 키를 거부하는데, 그 예외가 첫 로그인 요청 시점에 터지면 "로그인이 안 돼요"로 보고된다. 기동 시점에 실패시키면 원인이 로그 첫 줄에 있다.

**`global/security/JwtAuthenticationFilter.java`**

```java
package com.skinplate.api.global.security;

import com.skinplate.api.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Long userId = tokenProvider.parseUserId(token);

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId,                                   // principal = @CurrentUser Long
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
            }
        }
        chain.doFilter(request, response);
    }
}
```

**`global/security/JwtAuthenticationEntryPoint.java`**

```java
package com.skinplate.api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinplate.api.global.common.ApiResponse;
import com.skinplate.api.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패 시 Spring Security 기본 HTML 대신 프로젝트 공통 JSON 포맷으로 응답한다.
 * 필터가 남긴 errorCode가 있으면 TOKEN_EXPIRED / UNAUTHORIZED를 구분해서 내려준다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object attr = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        ErrorCode code = (attr instanceof ErrorCode ec) ? ec : ErrorCode.UNAUTHORIZED;

        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code));
    }
}
```

> **만료와 무효를 구분해서 내려주는 이유** — 프론트는 `TOKEN_EXPIRED`면 조용히 로그인 화면으로 보내고, `UNAUTHORIZED`면 "다시 로그인해 주세요" 토스트를 띄운다. 둘 다 401이지만 사용자 경험은 다르다.

---

## 1.9 global/config

**`global/config/SecurityConfig.java`**

```java
package com.skinplate.api.global.config;

import com.skinplate.api.global.security.JwtAuthenticationEntryPoint;
import com.skinplate.api.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/test-login",
            "/api/v1/health",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()          // ★ 기본 차단
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JwtAuthenticationFilter가 @Component이자 Filter라서
     * Spring Boot가 서블릿 컨테이너에도 자동 등록해 버린다.
     * 지금은 OncePerRequestFilter의 중복 방지 덕에 무해하지만,
     * 누군가 @Order를 붙여 순서가 뒤집히면 체인 안 실행이 스킵되어
     * 전 요청이 401이 된다. 자동 등록을 꺼서 그 가능성을 없앤다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
```

**`global/config/JpaConfig.java`**

```java
package com.skinplate.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing          // BaseTimeEntity의 @CreatedDate / @LastModifiedDate 활성화
public class JpaConfig {}
```

**`global/config/WebConfig.java`**

```java
package com.skinplate.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")      // 해커톤: 전체 허용. 운영 시 도메인 지정
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

> **Flutter Web을 붙여도 여기는 고칠 것이 없다.** `/api/**`가 이미 전체 허용이고, 우리는 **쿠키가 아니라 `Authorization` 헤더로 토큰을 보낸다.** 그래서 `allowCredentials(true)`·`SameSite`·프리플라이트 쿠키 같은 함정이 **처음부터 발생하지 않는다.** `allowedOriginPatterns("*")`와 `allowCredentials(true)`를 같이 켜면 스프링이 예외를 던지는데, 우리는 후자가 필요 없어서 그 조합에 닿지 않는다 (PRD §9.6).

**`global/config/OpenAiConfig.java`**

```java
package com.skinplate.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiConfig {

    @Bean
    public WebClient openAiWebClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.api-key}") String apiKey) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))  // Base64 이미지
                .build();
    }
}
```

> **`maxInMemorySize`는 요청이 아니라 응답에 걸리는 한도다.** WebClient 기본값 256KB는 **디코더** 제한이라 올려도 우리가 올려 보내는 Base64 이미지와는 상관이 없다 — 피부 3장이면 요청 본문이 22MB지만 10MB 설정으로 그대로 나간다(인코더는 이 한도를 보지 않는다). 실제로 막히는 쪽은 **OpenAI 응답**인데 그건 수 KB짜리 JSON 하나다.
>
> 그래도 10MB로 두는 이유는 응답 본문이 커질 여지(에러 페이지·디버그 응답)를 남겨두는 값이 무해하기 때문이다. **"AI 분석이 안 돼요"의 원인을 여기서 찾지 마라** — 요청 크기 문제는 `spring.servlet.multipart.max-request-size`(업로드 단계)나 컨테이너 힙에서 난다.

**`global/config/SwaggerConfig.java`**

```java
package com.skinplate.api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Skin Plate API")
                        .description("오늘의 피부를 위한 오늘의 한 끼")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME, bearer));
    }
}
```

> Swagger UI 우측 상단 **Authorize** 버튼에 `/auth/test-login`으로 받은 토큰을 붙여넣으면 이후 모든 요청에 자동으로 실린다. 백엔드 단독 테스트가 훨씬 빨라진다.

---

## 1.10 global/init — 테스트 계정

**`global/init/TestAccountInitializer.java`**

```java
package com.skinplate.api.global.init;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서버 기동 시 시연·테스트용 고정 계정을 생성한다. (PRD v1.1 §16.5)
 *
 * Flyway seed SQL에 BCrypt 해시를 박지 않는 이유:
 *   - 해시는 사람이 검증할 수 없다
 *   - PasswordEncoder 설정을 바꾸면 조용히 깨진다
 *   - 앱의 인코더로 생성하면 항상 일치한다
 *
 * 이미 존재하면 건너뛰므로 몇 번 재기동해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.test-account.enabled", havingValue = "true")
public class TestAccountInitializer implements ApplicationRunner {

    /**
     * ① 슬롯 계정 — 원탭 로그인(POST /auth/test-login) 대상. is_test_account = true
     * 슬롯 1은 시연 전용이라 피부 타입을 지성으로 미리 박아둔다.
     * 실측이 건조(DRY)로 나오므로 S05 에서 갭 코멘트가 바로 뜬다.
     */
    private static final List<SlotAccount> SLOT_ACCOUNTS = List.of(
            new SlotAccount(1, "test@skinplate.app",  "테스트유저",  SkinType.OILY),
            new SlotAccount(2, "test2@skinplate.app", "테스트유저2", null),
            new SlotAccount(3, "test3@skinplate.app", "테스트유저3", null)
    );

    /**
     * ② 개발 계정 — 로그인 폼으로 이메일·비밀번호를 실제 입력해 테스트한다.
     *    is_test_account = false 라 일반 사용자와 동일한 경로를 탄다.
     *
     * 원탭 로그인만 쓰면 S01 로그인 폼과 실패 응답이 한 번도 안 돌아본다.
     * 피부 타입을 서로 다르게 둬서, 계정만 바꿔 로그인하면
     * S05 갭 카드의 네 분기를 전부 확인할 수 있다.
     *
     *   시연 지표(38/52/64/25/78) → observe() = DRY 기준
     *     slot 2·3  declared = null   → 갭 카드 없음, 인라인 선택 칩
     *     dev1      DRY == DRY        → 일치 메시지
     *     slot 1    OILY vs DRY       → SPECIAL["OILY→DRY"] 전용 문구
     *     dev2      SENSITIVE vs DRY  → SPECIAL 에 없음 → 폴백 문구
     *     dev3      UNKNOWN           → "오늘 측정 기준으로는 …에 가깝습니다"
     */
    private static final List<DevAccount> DEV_ACCOUNTS = List.of(
            new DevAccount("dev1@skinplate.app", "개발계정1", SkinType.DRY),
            new DevAccount("dev2@skinplate.app", "개발계정2", SkinType.SENSITIVE),
            new DevAccount("dev3@skinplate.app", "개발계정3", SkinType.UNKNOWN)
    );

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.test-account.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encoded = passwordEncoder.encode(password);   // 6개 계정이 같은 해시를 공유한다

        for (SlotAccount a : SLOT_ACCOUNTS) {
            create(a.email(), encoded, a.nickname(), a.skinType(), true, "슬롯 " + a.slot());
        }
        for (DevAccount a : DEV_ACCOUNTS) {
            create(a.email(), encoded, a.nickname(), a.skinType(), false, "개발 계정");
        }
    }

    private void create(String email, String encodedPassword, String nickname,
                        SkinType skinType, boolean testAccount, String label) {

        if (userRepository.existsByEmail(email)) return;      // 멱등

        AppUser user = testAccount
                ? AppUser.createTestAccount(email, encodedPassword, nickname)
                : AppUser.create(email, encodedPassword, nickname);

        if (skinType != null) user.declareSkinType(skinType);

        userRepository.save(user);
        log.info("계정 생성: {} ({})", email, label);
    }

    /**
     * slot 번호로 이메일을 찾는다. AuthService 의 test-login 에서만 쓴다.
     * 개발 계정(dev*)은 원탭 로그인 대상이 아니다 — 로그인 폼으로만 들어간다.
     */
    public static String emailOfSlot(int slot) {
        return SLOT_ACCOUNTS.stream()
                .filter(a -> a.slot() == slot)
                .findFirst()
                .map(SlotAccount::email)
                .orElse(SLOT_ACCOUNTS.get(0).email());
    }

    public record SlotAccount(int slot, String email, String nickname, SkinType skinType) {}

    public record DevAccount(String email, String nickname, SkinType skinType) {}
}
```

**① 슬롯 계정 — 원탭 로그인용** (`is_test_account = true`)

| 슬롯 | 이메일 | 비밀번호 | 피부 타입 | 용도 |
|---|---|---|---|---|
| 1 | `test@skinplate.app` | `test1234!` | `OILY` | **발표 시연 전용** — 리허설 데이터를 남기지 않는다 |
| 2 | `test2@skinplate.app` | `test1234!` | 미설정 | 팀 내부 테스트 |
| 3 | `test3@skinplate.app` | `test1234!` | 미설정 | 심사위원 직접 체험 |

**② 개발 계정 — 로그인 폼 입력용** (`is_test_account = false`)

| 이메일 | 비밀번호 | 피부 타입 | 확인 가능한 갭 분기 |
|---|---|---|---|
| `dev1@skinplate.app` | `test1234!` | `DRY` | **일치** — "평소 생각하신 건성 그대로입니다" |
| `dev2@skinplate.app` | `test1234!` | `SENSITIVE` | **불일치 폴백** — "평소 민감성이라고 생각하셨지만, 오늘 측정은 건성에…" |
| `dev3@skinplate.app` | `test1234!` | `UNKNOWN` | **모름** — "오늘 측정 기준으로는 건성에 가깝습니다" |

> **여섯 개가 같은 비밀번호를 쓴다.** 계정별로 다르게 두면 아무도 못 외우고 결국 어딘가에 적어두게 된다. `TEST_ACCOUNT_ENABLED=false`면 전부 안 생긴다.
>
> **개발 계정이 따로 필요한 이유** — 원탭 로그인만 쓰면 **S01 로그인 폼이 한 번도 안 돌아본다.** 이메일 형식 검증, 비밀번호 불일치 응답(`INVALID_CREDENTIALS`), 폼 에러 표시가 전부 미검증인 채로 Day 8까지 갈 수 있다. `dev*` 계정이 그 경로를 강제로 지나가게 한다.
>
> 피부 타입을 셋 다 다르게 둔 덕에 **계정만 바꿔 로그인하면 S05 갭 카드의 네 분기를 전부 눈으로 확인**할 수 있다. 분기마다 지표를 조작할 필요가 없다.

---

## 1.11 domain/user

**`domain/user/entity/Role.java`**

```java
package com.skinplate.api.domain.user.entity;

public enum Role {
    USER,
    ADMIN       // 현재 미사용. 관리자 기능은 Out of Scope이나 enum 자리만 확보
}
```

**`domain/user/entity/SkinType.java`**

```java
package com.skinplate.api.domain.user.entity;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

/**
 * 피부 타입. 두 가지 용도로 쓰인다.
 *
 *   declared : 사용자가 스스로 고른 값 (S01c)
 *   observed : 5개 지표에서 규칙으로 도출한 값 — AI 에게 묻지 않는다
 *
 * 어느 쪽도 Skin Plate Score 계산에는 들어가지 않는다.
 * 둘의 차이를 보여주는 것이 이 타입의 존재 이유다.
 */
public enum SkinType {

    DRY        ("건성"),
    OILY       ("지성"),
    COMBINATION("복합성"),
    SENSITIVE  ("민감성"),
    NORMAL     ("보통"),      // 관찰 전용. 선택지에는 노출하지 않는다
    UNKNOWN    ("잘 모르겠어요");

    private final String label;

    SkinType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** S01c 화면에 노출할 선택지 (NORMAL 제외) */
    public static SkinType[] selectable() {
        return new SkinType[]{ DRY, OILY, COMBINATION, SENSITIVE, UNKNOWN };
    }

    /**
     * 5개 지표에서 오늘의 관찰 타입을 도출한다. (PRD §4.4.1)
     * 같은 지표면 항상 같은 타입이 나와야 갭 코멘트도 재현 가능하다.
     */
    public static SkinType observe(SkinMetrics m) {
        if (m.getRedness() > 70)          return SENSITIVE;
        if (m.isDry() && m.isOily())      return COMBINATION;   // 수분 부족형 지성
        if (m.isOily())                   return OILY;
        if (m.isDry())                    return DRY;
        return NORMAL;
    }
}
```

> **판정 순서가 곧 우선순위다.** 홍조가 매우 높으면(>70) 다른 조건보다 먼저 `SENSITIVE`로 잡는다. 자극이 심한 상태에서 "지성이시네요"라고 말하는 건 사용자에게 도움이 안 된다.

**`domain/user/entity/AppUser.java`**

```java
package com.skinplate.api.domain.user.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Getter
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt 해시. 평문은 이 필드는 물론 로그에도 남기지 않는다. */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_test_account", nullable = false)
    private boolean testAccount;

    /**
     * 사용자가 스스로 고른 피부 타입. (PRD §4.4.1)
     *
     * NULL 과 UNKNOWN 은 다르다.
     *   NULL    = 건너뜀. 나중에 다시 물어봐도 된다
     *   UNKNOWN = "잘 모르겠어요"를 골랐다. 다시 물어보면 실례다
     *
     * Rule Engine 에는 넣지 않는다. PlateContext 는 계속 (SkinMetrics, FoodAnalysis) 뿐이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SkinType declaredSkinType;

    private LocalDateTime lastLoginAt;

    // ---- 팩토리 ----

    public static AppUser create(String email, String encodedPassword, String nickname) {
        AppUser user = new AppUser();
        user.email = normalizeEmail(email);
        user.password = encodedPassword;
        user.nickname = nickname;
        user.role = Role.USER;
        user.testAccount = false;
        return user;
    }

    public static AppUser createTestAccount(String email, String encodedPassword, String nickname) {
        AppUser user = create(email, encodedPassword, nickname);
        user.testAccount = true;
        return user;
    }

    // ---- 행위 ----

    public void markLoggedIn() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void declareSkinType(SkinType skinType) {
        this.declaredSkinType = skinType;
    }

    /**
     * 가입 시점에 소문자로 정규화한다.
     * Test@skinplate.app 으로 가입하고 test@skinplate.app 으로 로그인하려는
     * 사용자는 반드시 나온다. 저장 시점에 맞춰두면 조회 코드가 이 문제를 몰라도 된다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

**`domain/user/repository/AppUserRepository.java`**

```java
package com.skinplate.api.domain.user.repository;

import com.skinplate.api.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

> 이메일이 Entity 팩토리에서 소문자로 정규화되므로, Service에서도 조회 전에 `toLowerCase()`를 한 번 적용해야 한다. 이 한 쌍을 지키면 `lower(email)` 유니크 인덱스와 어긋나지 않는다.

**`domain/user/dto/UserSummaryDto.java`**

```java
package com.skinplate.api.domain.user.dto;

import com.skinplate.api.domain.user.entity.AppUser;

/** 인증 응답에 함께 실리는 최소 사용자 정보. */
public record UserSummaryDto(
        Long userId,
        String email,
        String nickname
) {
    public static UserSummaryDto from(AppUser user) {
        return new UserSummaryDto(user.getId(), user.getEmail(), user.getNickname());
    }
}
```

---

## 1.12 domain/skin

**`domain/skin/entity/SkinMetrics.java`**

```java
package com.skinplate.api.domain.skin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피부 5개 지표 (0~100).
 *
 *   높을수록 좋음 : hydration, barrier
 *   높을수록 나쁨 : oil, redness, trouble
 *
 * 판정 기준값을 이 클래스 안에 모아두면 Rule 구현체들이
 * metrics.isDry() 처럼 의미로 질문하게 되고, 기준 변경이 한 곳에서 끝난다.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinMetrics {

    public static final int DRY_THRESHOLD          = 40;   // 미만이면 건조
    public static final int OILY_THRESHOLD         = 70;   // 초과면 유분 과다
    public static final int REDNESS_THRESHOLD      = 60;   // 초과면 홍조
    public static final int TROUBLE_THRESHOLD      = 60;   // 초과면 트러블
    public static final int BARRIER_WEAK_THRESHOLD = 40;   // 미만이면 장벽 약화

    @Column(nullable = false) private int hydration;
    @Column(nullable = false) private int oil;
    @Column(nullable = false) private int redness;
    @Column(nullable = false) private int trouble;
    @Column(nullable = false) private int barrier;

    public static SkinMetrics of(int hydration, int oil, int redness, int trouble, int barrier) {
        return new SkinMetrics(
                clamp(hydration), clamp(oil), clamp(redness), clamp(trouble), clamp(barrier));
    }

    // ---- 상태 판정 ----

    public boolean isDry()         { return hydration < DRY_THRESHOLD; }
    public boolean isOily()        { return oil > OILY_THRESHOLD; }
    public boolean hasRedness()    { return redness > REDNESS_THRESHOLD; }
    public boolean hasTrouble()    { return trouble > TROUBLE_THRESHOLD; }
    public boolean isBarrierWeak() { return barrier < BARRIER_WEAK_THRESHOLD; }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
```

**`domain/skin/entity/SkinAnalysis.java`**

```java
package com.skinplate.api.domain.skin.entity;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "skin_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private int skinScore;

    @Embedded
    private SkinMetrics metrics;

    @Column(length = 300)
    private String summary;

    /** AI 원본 응답. 프롬프트를 바꿔도 과거 데이터를 재해석할 수 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static SkinAnalysis create(AppUser user,
                                      SkinMetrics metrics,
                                      int skinScore,
                                      String summary,
                                      String rawAiResponse) {
        SkinAnalysis analysis = new SkinAnalysis();
        analysis.user = user;
        analysis.metrics = metrics;
        analysis.skinScore = Math.max(0, Math.min(100, skinScore));
        analysis.summary = summary;
        analysis.rawAiResponse = rawAiResponse;
        return analysis;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
```

**`domain/skin/repository/SkinAnalysisRepository.java`**

```java
package com.skinplate.api.domain.skin.repository;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkinAnalysisRepository extends JpaRepository<SkinAnalysis, Long> {

    /**
     * ★ findById 를 쓰지 않는다.
     * id만으로 조회하면 /skin/analyses/1 부터 순서대로 호출해
     * 남의 피부 분석 결과를 전부 읽을 수 있다. 조건 하나로 막힌다.
     */
    Optional<SkinAnalysis> findByIdAndUserId(Long id, Long userId);

    Optional<SkinAnalysis> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
```

---

### 1.12.1 Skin Score 산출 — 산식을 코드로 못 박는다

**`domain/skin/service/SkinScoreCalculator.java`**

```java
package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.springframework.stereotype.Component;

/**
 * Skin Score = 5개 지표의 방향을 "높을수록 좋음"으로 통일한 뒤 평균. (PRD §4.1)
 *
 * 산식이 없으면 구현자가 아무 평균이나 짜고, 그때부터 문서의 예시 점수와 어긋난다.
 * S05 화면은 총점 게이지와 5개 지표 바를 한 화면에 동시에 띄우기 때문에
 * 둘이 안 맞으면 심사위원이 3초 만에 본다.
 */
@Component
public class SkinScoreCalculator {

    public int calculate(SkinMetrics m) {
        int sum = m.getHydration()          // 높을수록 좋음
                + m.getBarrier()            // 높을수록 좋음
                + (100 - m.getOil())        // 낮을수록 좋음 → 뒤집는다
                + (100 - m.getRedness())
                + (100 - m.getTrouble());

        return Math.round(sum / 5f);
    }
}
```

| 지표 | 값 | 방향 정렬 후 |
|---|---|---|
| hydration | 38 | 38 |
| oil | 52 | 48 |
| redness | 64 | 36 |
| trouble | 25 | 75 |
| barrier | 78 | 78 |
| | | **평균 = 55** |

> **가중치를 붙이고 싶어질 텐데 참아라.** 가중치를 넣는 순간 "왜 홍조가 1.5배인가"를 설명해야 하고, 근거가 없다. 단순 평균은 화면의 5개 바로 **심사위원이 직접 검산할 수 있다**는 게 더 큰 가치다.

**`domain/skin/service/SkinHighlightBuilder.java`**

```java
package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.dto.HighlightDto;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * S05 상단의 3줄 요약을 만든다. (PRD §4.1)
 *
 * 두 결정을 분리한다.
 *   무엇을 보여줄까 → 위치 (가장 좋은 것 / 두 번째로 나쁜 것 / 가장 나쁜 것)
 *   어떤 상태로 보여줄까 → 값 (60 이상 GOOD / 40 이상 WARN / 그 미만 CAUTION)
 *
 * 위치만으로 상태까지 정하면 화면이 거짓말을 한다.
 *   모든 지표가 나쁜 사용자 → "가장 좋은 것"이 장벽 25 인데 초록 GOOD "장벽 양호"
 *   모든 지표가 좋은 사용자 → "가장 나쁜 것"이 유분 10 인데 빨강 CAUTION "유분 과다"
 * Skin Score 18 점 화면에 초록 뱃지가 뜨는 것은 86 점 vs 수분 38 과 같은 종류의 사고다.
 *
 * 위치로 3개를 뽑는 이유는 레이아웃 고정이다 — 어떤 날은 3줄, 어떤 날은 0줄이 되면
 * S05 화면이 매번 다른 높이로 그려진다.
 */
@Component
public class SkinHighlightBuilder {

    //                            key             GOOD           WARN             CAUTION
    private static final Map<String, String[]> LABELS = Map.of(
            "hydration", new String[]{"수분 충분",      "약간 건조",      "건조 주의"},
            // "유분 과다"가 아니라 "유분 많음". isOily 임계(70 초과)와 CAUTION 경계(정렬점수 40 미만
            // = 유분 60 초과)가 어긋나므로, 유분 65면 R07 은 안 켜지는데 뱃지만 빨강이 된다.
            // "유분 과다라면서 왜 튀김 감점이 없죠?"에 답할 수 없다. 문구를 약하게 둔다.
            "oil",       new String[]{"유분 균형",      "약간 번들거림",  "유분 많음"},
            "redness",   new String[]{"홍조 없음",      "약간 붉음",      "홍조 주의"},
            "trouble",   new String[]{"트러블 없음",    "약간의 트러블",  "트러블 주의"},
            "barrier",   new String[]{"피부 장벽 양호", "장벽 다소 약함", "장벽 손상 주의"});

    public List<HighlightDto> build(SkinMetrics m) {
        // 전부 "높을수록 좋음" 점수로 환산한 뒤 나쁜 순으로 정렬
        List<Scored> sorted = List.of(
                        new Scored("hydration", m.getHydration()),
                        new Scored("oil",       100 - m.getOil()),
                        new Scored("redness",   100 - m.getRedness()),
                        new Scored("trouble",   100 - m.getTrouble()),
                        new Scored("barrier",   m.getBarrier()))
                .stream()
                .sorted(Comparator.comparingInt(Scored::score)
                                  .thenComparing(Scored::key))   // 동점 시 순서 고정
                .toList();

        Scored worst  = sorted.get(0);
        Scored second = sorted.get(1);
        Scored best   = sorted.get(sorted.size() - 1);

        // 위치는 "무엇을 보여줄지"만 정한다. 상태와 문구는 값이 정한다.
        return List.of(toHighlight(best), toHighlight(second), toHighlight(worst));
    }

    /**
     * 임계값은 SkinMetrics 의 판정 기준과 일치시킨다.
     *   isDry / isBarrierWeak → 40 미만       → 정렬점수 40 미만 → CAUTION
     *   isOily(70 초과)       → 정렬점수 30   → CAUTION
     *   hasRedness(60 초과)   → 정렬점수 40 미만 → CAUTION
     * 두 곳의 기준이 어긋나면 "홍조 주의라면서 뱃지는 노랑"이 된다.
     */
    private HighlightDto toHighlight(Scored s) {
        String[] labels = LABELS.get(s.key());

        if (s.score() >= 60) return HighlightDto.good(labels[0]);
        if (s.score() >= 40) return HighlightDto.warn(labels[1]);
        return HighlightDto.caution(labels[2]);
    }

    private record Scored(String key, int score) {}
}
```

**예시 검증** — `38 / 52 / 64 / 25 / 78`

| 지표 | 정렬 점수 | 선택 | 값 판정 | 결과 |
|---|---|---|---|---|
| barrier | 78 | **최선** | ≥60 | GOOD · 피부 장벽 양호 |
| hydration | 38 | 차악 | <40 | CAUTION · 건조 주의 |
| redness | 36 | **최악** | <40 | CAUTION · 홍조 주의 |
| oil | 48 | — | | |
| trouble | 75 | — | | |

**극단 케이스 검증** — 위치만으로 상태를 정했다면 여기서 화면이 거짓말을 했다.

| 케이스 | 지표 | Skin Score | 위치 기반(잘못) | 값 기반(현재) |
|---|---|---|---|---|
| 전부 나쁨 | 20/90/85/80/25 | **18** | 초록 GOOD "피부 장벽 양호"(장벽 25) | CAUTION 3개 |
| 전부 좋음 | 95/10/5/5/95 | **94** | 빨강 CAUTION "유분 과다"(유분 10) | GOOD 3개 |

> **"항상 좋은 소식 하나"를 포기했다.** 상태가 정말 나쁘면 세 줄 모두 CAUTION이 나온다. 그게 맞다 — 18점짜리 피부에 초록 뱃지를 띄우는 것보다 낫고, 심사위원이 슬롯 3으로 직접 체험할 때 들키지 않는다. 고정되는 것은 **줄 수(3줄)**이지 색이 아니다.

---

### 1.12.2 자가 진단 갭 분석

**`domain/skin/service/SkinTypeGapAnalyzer.java`**

```java
package com.skinplate.api.domain.skin.service;

import com.skinplate.api.domain.skin.dto.SkinTypeGapDto;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import com.skinplate.api.domain.user.entity.SkinType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * "평소 알고 계셨던 타입"과 "오늘 측정에서 관찰된 타입"을 비교한다. (PRD §4.4.1)
 *
 * 이 클래스는 Skin Plate Score 에 아무 영향을 주지 않는다.
 * 자가 신고값이 점수에 개입하면 "같은 사진 두 번 찍어도 같은 점수"라는
 * 주장에 사용자 입력이라는 변수가 하나 더 끼어든다.
 */
@Component
public class SkinTypeGapAnalyzer {

    /** 자주 나오는 조합만 전용 문구를 둔다. 나머지는 폴백으로 충분하다. */
    private static final Map<String, String> SPECIAL = Map.of(
            key(SkinType.OILY, SkinType.DRY),
            "지성이라고 생각하셨지만 오늘은 유분보다 수분 부족이 두드러집니다. 유분기는 수분이 모자랄 때도 늘어날 수 있습니다.",

            key(SkinType.OILY, SkinType.COMBINATION),
            "유분은 많은데 수분이 부족한 상태입니다. 흔히 '수분 부족형 지성'이라고 부릅니다.",

            key(SkinType.DRY, SkinType.OILY),
            "건성이라고 생각하셨지만 오늘은 유분이 많은 편입니다. 세안 후 수분 공급이 부족하지 않은지 살펴보세요.",

            key(SkinType.SENSITIVE, SkinType.NORMAL),
            "민감성이라고 하셨는데 오늘은 자극이 적은 안정된 상태입니다.",

            key(SkinType.COMBINATION, SkinType.DRY),
            "복합성이라고 생각하셨지만 오늘은 전반적으로 건조합니다."
    );

    /**
     * @param declared 사용자가 고른 값. null(건너뜀)이면 null 을 반환한다.
     * @return 앱이 그대로 렌더링할 수 있는 비교 결과. null 이면 앱이 선택 칩을 띄운다.
     */
    public SkinTypeGapDto analyze(SkinType declared, SkinMetrics metrics) {
        if (declared == null) return null;

        SkinType observed = SkinType.observe(metrics);

        if (declared == SkinType.UNKNOWN) {
            return new SkinTypeGapDto(declared, observed, false,
                    "오늘 측정 기준으로는 " + observed.getLabel() + "에 가깝습니다.");
        }
        if (declared == observed) {
            return new SkinTypeGapDto(declared, observed, true,
                    "평소 생각하신 " + declared.getLabel() + " 그대로입니다. 오늘 측정과 일치합니다.");
        }

        String message = SPECIAL.getOrDefault(key(declared, observed),
                "평소 " + declared.getLabel() + "이라고 생각하셨지만, "
                        + "오늘 측정은 " + observed.getLabel() + "에 가깝습니다.");

        return new SkinTypeGapDto(declared, observed, false, message);
    }

    private static String key(SkinType declared, SkinType observed) {
        return declared.name() + "→" + observed.name();
    }
}
```

**동작 확인** — 테스트 계정 슬롯 1 (`declared = OILY`) × 지표 `38/52/64/25/78`

```
observe(38, 52, 64, 25, 78)
  redness 64 > 70 ?      아니오
  dry(38<40) && oily(52>70) ?  아니오
  oily(52>70) ?          아니오
  dry(38<40) ?           예     → DRY

declared OILY ≠ observed DRY  →  SPECIAL["OILY→DRY"]
  "지성이라고 생각하셨지만 오늘은 유분보다 수분 부족이 두드러집니다.
   유분기는 수분이 모자랄 때도 늘어날 수 있습니다."
```

> **시연에서 이 문장이 나오도록 슬롯 1을 `OILY`로 박아뒀다.** 심사위원이 가장 놀라는 지점이 여기다. 그리고 이건 우리가 만들어낸 이야기가 아니라 **규칙에서 결정론적으로 도출된 결과**라, "이건 어떻게 나온 겁니까"에 그 자리에서 답할 수 있다.

> **`observed`를 DB에 저장하지 않는다.** 5개 지표에서 언제든 다시 계산되는 파생값이다. 저장해두면 판정 규칙을 바꿨을 때 과거 데이터와 어긋나고, 그때부터 "이 기록의 타입은 옛날 규칙 기준"이라는 설명이 붙어야 한다.

---

## 1.13 domain/food

**`domain/food/entity/CookingMethod.java`**

```java
package com.skinplate.api.domain.food.entity;

/** OpenAI Structured Outputs의 enum과 값이 정확히 일치해야 한다. */
public enum CookingMethod {
    FRIED,      // 튀김
    BOILED,     // 국물 요리 — "국물을 절반만 남기세요" 행동의 조건
    GRILLED,    // 구이
    RAW,        // 생식
    STEAMED,    // 찜
    ETC
}
```

**`domain/food/entity/IngredientTag.java`**

```java
package com.skinplate.api.domain.food.entity;

/**
 * 재료 태그. Rule Engine이 음식을 이해하는 유일한 통로다.
 *
 * AI가 태그를 자유롭게 만들면 룰이 아무것도 매칭하지 못한다.
 * 그래서 OpenAI JSON Schema에서도 이 목록을 enum으로 강제한다.
 * 값을 추가할 때는 스키마(food-analysis-schema.json)도 함께 고쳐야 한다.
 */
public enum IngredientTag {
    VITAMIN_C,      // 키위, 브로콜리, 파프리카
    VITAMIN_A,      // 당근, 시금치
    OMEGA3,         // 연어, 고등어, 견과류
    ANTIOXIDANT,    // 베리류, 녹차, 토마토
    PROBIOTIC,      // 김치, 된장, 요거트
    DAIRY,          // 우유, 치즈
    GLUTEN,         // 밀가루
    CAPSAICIN,      // 고춧가루, 청양고추
    CAFFEINE,       // 커피, 홍차
    ALCOHOL,
    HIGH_GI,        // 흰쌀, 설탕
    ETC
}
```

**`domain/food/entity/Nutrition.java`**

```java
package com.skinplate.api.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nutrition {

    public static final int SODIUM_THRESHOLD_MG   = 1500;   // 초과 시 감점
    public static final int PROTEIN_THRESHOLD_G   = 20;     // 이상이면 가점
    public static final int SUGAR_THRESHOLD_G     = 25;     // 초과 시 감점
    public static final int CALORIES_THRESHOLD    = 900;    // 초과 시 감점 (확장 룰)

    @Column(name = "calories_kcal", nullable = false) private int caloriesKcal;
    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2) private BigDecimal proteinG;
    @Column(name = "fat_g",     nullable = false, precision = 6, scale = 2) private BigDecimal fatG;
    @Column(name = "carb_g",    nullable = false, precision = 6, scale = 2) private BigDecimal carbG;
    @Column(name = "sodium_mg", nullable = false) private int sodiumMg;
    @Column(name = "sugar_g",   nullable = false, precision = 6, scale = 2) private BigDecimal sugarG;

    public static Nutrition of(int caloriesKcal, BigDecimal proteinG, BigDecimal fatG,
                               BigDecimal carbG, int sodiumMg, BigDecimal sugarG) {
        return new Nutrition(
                Math.max(0, caloriesKcal),
                nonNull(proteinG), nonNull(fatG), nonNull(carbG),
                Math.max(0, sodiumMg), nonNull(sugarG));
    }

    // ---- 판정 ----

    public boolean isHighSodium()  { return sodiumMg > SODIUM_THRESHOLD_MG; }
    public boolean isHighSugar()   { return sugarG.compareTo(BigDecimal.valueOf(SUGAR_THRESHOLD_G)) > 0; }
    public boolean isHighProtein() { return proteinG.compareTo(BigDecimal.valueOf(PROTEIN_THRESHOLD_G)) >= 0; }
    public boolean isHighCalorie() { return caloriesKcal > CALORIES_THRESHOLD; }

    public int sodiumExcessMg() { return Math.max(0, sodiumMg - SODIUM_THRESHOLD_MG); }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
```

> **영양 임계값을 `Nutrition` 안에 둔 이유** — `RuleConstants`에 몰아넣을 수도 있지만, "나트륨이 많은가"는 영양 정보 자신이 대답할 수 있는 질문이다. 룰은 판단(감점 몇 점, 무슨 문구)에 집중하고, 사실 확인은 값 객체가 한다.

**`domain/food/entity/FoodIngredient.java`**

```java
package com.skinplate.api.domain.food.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "food_ingredient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodIngredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_analysis_id", nullable = false)
    private FoodAnalysis foodAnalysis;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngredientTag tag;

    public static FoodIngredient of(String name, IngredientTag tag) {
        FoodIngredient ingredient = new FoodIngredient();
        ingredient.name = name;
        ingredient.tag = tag == null ? IngredientTag.ETC : tag;
        return ingredient;
    }

    /** 연관관계 편의 메서드에서만 호출한다. */
    void assignTo(FoodAnalysis foodAnalysis) {
        this.foodAnalysis = foodAnalysis;
    }
}
```

**`domain/food/entity/FoodAnalysis.java`**

```java
package com.skinplate.api.domain.food.entity;

import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "food_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 50)
    private String foodCategory;

    @Embedded
    private Nutrition nutrition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CookingMethod cookingMethod;

    @Column(name = "is_spicy", nullable = false)
    private boolean spicy;

    @OneToMany(mappedBy = "foodAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodIngredient> ingredients = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawAiResponse;

    public static FoodAnalysis create(AppUser user,
                                      String foodName,
                                      String foodCategory,
                                      Nutrition nutrition,
                                      CookingMethod cookingMethod,
                                      boolean spicy,
                                      String rawAiResponse) {
        FoodAnalysis food = new FoodAnalysis();
        food.user = user;
        food.foodName = foodName;
        food.foodCategory = foodCategory;
        food.nutrition = nutrition;
        food.cookingMethod = cookingMethod == null ? CookingMethod.ETC : cookingMethod;
        food.spicy = spicy;
        food.rawAiResponse = rawAiResponse;
        return food;
    }

    // ---- 연관관계 ----

    public void addIngredient(FoodIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.assignTo(this);
    }

    public void addIngredients(List<FoodIngredient> list) {
        list.forEach(this::addIngredient);
    }

    // ---- Rule Engine이 사용하는 질의 ----

    public boolean hasTag(IngredientTag tag) {
        return ingredients.stream().anyMatch(i -> i.getTag() == tag);
    }

    public boolean hasAnyTag(IngredientTag... tags) {
        for (IngredientTag tag : tags) {
            if (hasTag(tag)) return true;
        }
        return false;
    }

    public boolean isSoup() {
        return cookingMethod == CookingMethod.BOILED;
    }

    public boolean isFried() {
        return cookingMethod == CookingMethod.FRIED;
    }

    /** 명시적 spicy 플래그 또는 캡사이신 재료 */
    public boolean isSpicyFood() {
        return spicy || hasTag(IngredientTag.CAPSAICIN);
    }
}
```

**`domain/food/repository/FoodAnalysisRepository.java`**

```java
package com.skinplate.api.domain.food.repository;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodAnalysisRepository extends JpaRepository<FoodAnalysis, Long> {

    Optional<FoodAnalysis> findByIdAndUserId(Long id, Long userId);
}
```

---

## 1.14 domain/plate

**`domain/plate/entity/FeedbackType.java`**

```java
package com.skinplate.api.domain.plate.entity;

public enum FeedbackType {
    GOOD,       // 좋은 점    — scoreDelta 사용 (+)
    CAUTION,    // 주의사항    — scoreDelta 사용 (−)
    ACTION      // 추천 행동   — expectedGain 사용 (+)
}
```

**`domain/plate/entity/SkinPlateFeedback.java`**

```java
package com.skinplate.api.domain.plate.entity;

import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "skin_plate_feedback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinPlateFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_plate_id", nullable = false)
    private SkinPlate skinPlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type;

    @Column(nullable = false, length = 200)
    private String message;

    /** GOOD / CAUTION 행에서 사용 (± 점수) */
    @Column(nullable = false)
    private int scoreDelta;

    /** ACTION 행에서 사용 (행동 시 회복 점수) */
    @Column(nullable = false)
    private int expectedGain;

    @Column(length = 30)
    private String ruleCode;

    @Column(nullable = false)
    private int displayOrder;

    public static SkinPlateFeedback good(String ruleCode, String message, int scoreDelta, int order) {
        return of(FeedbackType.GOOD, ruleCode, message, scoreDelta, 0, order);
    }

    public static SkinPlateFeedback caution(String ruleCode, String message, int scoreDelta, int order) {
        return of(FeedbackType.CAUTION, ruleCode, message, scoreDelta, 0, order);
    }

    public static SkinPlateFeedback action(String ruleCode, String message, int expectedGain, int order) {
        return of(FeedbackType.ACTION, ruleCode, message, 0, expectedGain, order);
    }

    private static SkinPlateFeedback of(FeedbackType type, String ruleCode, String message,
                                        int scoreDelta, int expectedGain, int order) {
        SkinPlateFeedback feedback = new SkinPlateFeedback();
        feedback.type = type;
        feedback.ruleCode = ruleCode;
        feedback.message = message;
        feedback.scoreDelta = scoreDelta;
        feedback.expectedGain = expectedGain;
        feedback.displayOrder = order;
        return feedback;
    }

    void assignTo(SkinPlate skinPlate) {
        this.skinPlate = skinPlate;
    }
}
```

> **`scoreDelta`와 `expectedGain`을 합치지 않는다.** R02는 감점 −12지만 "매운 양념을 덜어내면" 회복 폭은 +6이다. 매운맛을 전부 없앨 수는 없으니 감점 전액이 돌아오지 않는다. 두 값이 다르다는 사실이 추천 행동을 정직하게 만든다.

**`domain/plate/entity/SkinPlate.java`**

```java
package com.skinplate.api.domain.plate.entity;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "skin_plate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinPlate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_analysis_id", nullable = false)
    private SkinAnalysis skinAnalysis;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_analysis_id", nullable = false, unique = true)
    private FoodAnalysis foodAnalysis;

    @Column(nullable = false)
    private int plateScore;

    @Column(length = 300)
    private String summary;

    /** 점수에 기여한 룰 코드 배열. "왜 87점인가"를 사후에 설명할 수 있게 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String appliedRules;

    @OneToMany(mappedBy = "skinPlate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<SkinPlateFeedback> feedbacks = new ArrayList<>();

    public static SkinPlate create(AppUser user,
                                   SkinAnalysis skinAnalysis,
                                   FoodAnalysis foodAnalysis,
                                   int plateScore,
                                   String summary,
                                   String appliedRulesJson) {
        SkinPlate plate = new SkinPlate();
        plate.user = user;
        plate.skinAnalysis = skinAnalysis;
        plate.foodAnalysis = foodAnalysis;
        plate.plateScore = Math.max(0, Math.min(100, plateScore));
        plate.summary = summary;
        plate.appliedRules = appliedRulesJson;
        return plate;
    }

    public void addFeedback(SkinPlateFeedback feedback) {
        feedbacks.add(feedback);
        feedback.assignTo(this);
    }

    public void addFeedbacks(List<SkinPlateFeedback> list) {
        list.forEach(this::addFeedback);
    }
}
```

**`domain/plate/repository/SkinPlateRepository.java`**

```java
package com.skinplate.api.domain.plate.repository;

import com.skinplate.api.domain.plate.entity.SkinPlate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SkinPlateRepository extends JpaRepository<SkinPlate, Long> {

    Optional<SkinPlate> findByIdAndUserId(Long id, Long userId);

    List<SkinPlate> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
           select p from SkinPlate p
            where p.user.id = :userId
              and p.createdAt >= :from
              and p.createdAt <  :to
            order by p.createdAt desc
           """)
    List<SkinPlate> findByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);
}
```

> **`@Param`을 생략하지 않는다.** Spring Boot Gradle 플러그인이 `-parameters` 컴파일 옵션을 자동으로 넣어주므로 지금은 없어도 동작하지만, IDE에서 단독 실행하거나 빌드 설정을 손대는 순간 `Could not resolve parameter name`으로 **기동 자체가 실패**한다. 원인을 찾기 어려운 축에 속한다.

---

## 1.15 domain/recommendation

**`domain/recommendation/entity/RecommendationType.java`**

```java
package com.skinplate.api.domain.recommendation.entity;

public enum RecommendationType {
    RECOMMEND,   // 오늘 추천하는 음식
    AVOID        // 오늘 주의해야 하는 음식
}
```

**`domain/recommendation/entity/Recommendation.java`**

```java
package com.skinplate.api.domain.recommendation.entity;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "recommendation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_analysis_id", nullable = false)
    private SkinAnalysis skinAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationType type;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false)
    private int displayOrder;

    public static Recommendation of(AppUser user,
                                    SkinAnalysis skinAnalysis,
                                    RecommendationType type,
                                    String foodName,
                                    String reason,
                                    int displayOrder) {
        Recommendation recommendation = new Recommendation();
        recommendation.user = user;
        recommendation.skinAnalysis = skinAnalysis;
        recommendation.type = type;
        recommendation.foodName = foodName;
        recommendation.reason = reason;
        recommendation.displayOrder = displayOrder;
        return recommendation;
    }
}
```

**`domain/recommendation/repository/RecommendationRepository.java`**

```java
package com.skinplate.api.domain.recommendation.repository;

import com.skinplate.api.domain.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findBySkinAnalysisIdAndUserIdOrderByDisplayOrderAsc(
            Long skinAnalysisId, Long userId);

    boolean existsBySkinAnalysisId(Long skinAnalysisId);
}
```

---

## 1.16 DTO — 인증

**`domain/auth/dto/SignupRequest.java`**

```java
package com.skinplate.api.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자를 넘을 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,30}$",
                 message = "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해 주세요.")
        String nickname
) {}
```

**`domain/auth/dto/LoginRequest.java`**

```java
package com.skinplate.api.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {}
```

**`domain/auth/dto/TestLoginRequest.java`**

```java
package com.skinplate.api.domain.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 본문 전체가 선택 사항이다. {} 이거나 아예 생략해도 슬롯 1로 로그인된다.
 * 컨트롤러에서 @RequestBody(required = false)로 받고 slot()을 호출한다.
 */
public record TestLoginRequest(

        @Min(value = 1, message = "slot은 1~3 사이여야 합니다.")
        @Max(value = 3, message = "slot은 1~3 사이여야 합니다.")
        Integer slot
) {
    public static final int DEFAULT_SLOT = 1;

    /** null-safe 접근자. request 자체가 null일 수도 있으므로 정적 메서드로도 제공한다. */
    public int slotOrDefault() {
        return slot == null ? DEFAULT_SLOT : slot;
    }

    public static int slotOf(TestLoginRequest request) {
        return request == null ? DEFAULT_SLOT : request.slotOrDefault();
    }
}
```

**`domain/auth/dto/UpdateProfileRequest.java`**

```java
package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.entity.SkinType;
import jakarta.validation.constraints.Size;

/**
 * PATCH /auth/me — 보낸 필드만 바꾼다.
 *
 * "건너뛰기"는 이 API 를 호출하지 않는 것이다.
 * UNKNOWN 을 대신 넣으면 "잘 모르겠다고 답한 사용자"와 구분이 사라진다.
 */
public record UpdateProfileRequest(

        SkinType declaredSkinType,

        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해 주세요.")
        String nickname
) {
    public boolean hasSkinType() { return declaredSkinType != null; }
    public boolean hasNickname() { return nickname != null && !nickname.isBlank(); }
}
```

**`domain/auth/dto/AuthResponse.java`**

```java
package com.skinplate.api.domain.auth.dto;

import com.skinplate.api.domain.user.dto.UserSummaryDto;
import com.skinplate.api.domain.user.entity.AppUser;

/**
 * 회원가입 · 로그인 · 테스트 로그인이 모두 같은 형태로 응답한다.
 *
 * refreshToken 필드를 미리 만들어 두지 않았다.
 * 쓰지 않는 필드를 null로 내려보내면 프론트가 "이거 언제 채워지나요"를 묻게 된다.
 * JSON은 필드 추가에 하위 호환이므로 Phase 2에서 넣는 편이 낫다.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryDto user
) {
    private static final String BEARER = "Bearer";

    public static AuthResponse of(String accessToken, long expiresInSeconds, AppUser user) {
        return new AuthResponse(accessToken, BEARER, expiresInSeconds, UserSummaryDto.from(user));
    }
}
```

**`domain/auth/dto/MeResponse.java`**

```java
package com.skinplate.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skinplate.api.domain.user.entity.AppUser;
import com.skinplate.api.domain.user.entity.SkinType;

import java.time.LocalDateTime;

public record MeResponse(
        Long userId,
        String email,
        String nickname,

        /* null 이면 non_null 직렬화로 키 자체가 생략된다.
           앱은 키가 없으면 "아직 안 정함"으로 보고 인라인 선택 칩을 띄운다. */
        SkinType declaredSkinType,

        /* boolean 접근자의 JSON 키는 Jackson 버전과 네이밍 전략에 따라
           isTestAccount / testAccount 로 갈릴 여지가 있다.
           프론트 DTO가 isTestAccount를 기대하므로 방어적으로 고정한다. */
        @JsonProperty("isTestAccount") boolean isTestAccount,

        LocalDateTime joinedAt
) {
    public static MeResponse from(AppUser user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getDeclaredSkinType(),
                user.isTestAccount(),
                user.getCreatedAt());
    }
}
```

> **`@JsonProperty`는 사소해 보이지만 값어치가 있다.** 현재 조합(Spring Boot 3.3 + Jackson 2.17)에서는 record 컴포넌트명이 그대로 키가 되므로 붙이지 않아도 `isTestAccount`가 나온다. 문제는 Jackson 버전이나 네이밍 전략이 바뀌면 `testAccount`로 흔들릴 수 있다는 점이고, 그때 프론트는 예외 없이 조용히 `false`를 받는다. **키가 어긋나도 앱이 죽지 않는 종류의 필드가 가장 늦게 발견된다.** 한 줄로 고정해 두는 편이 낫다.

---

## 1.17 DTO — 피부 분석

**`domain/skin/dto/SkinMetricsDto.java`**

```java
package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

public record SkinMetricsDto(
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier
) {
    public static SkinMetricsDto from(SkinMetrics metrics) {
        return new SkinMetricsDto(
                metrics.getHydration(),
                metrics.getOil(),
                metrics.getRedness(),
                metrics.getTrouble(),
                metrics.getBarrier());
    }
}
```

**`domain/skin/dto/HighlightDto.java`**

```java
package com.skinplate.api.domain.skin.dto;

/** 결과 화면(S05) 상단에 3줄로 보여주는 요약 뱃지. */
public record HighlightDto(String label, HighlightStatus status) {

    public static HighlightDto good(String label)    { return new HighlightDto(label, HighlightStatus.GOOD); }
    public static HighlightDto warn(String label)    { return new HighlightDto(label, HighlightStatus.WARN); }
    public static HighlightDto caution(String label) { return new HighlightDto(label, HighlightStatus.CAUTION); }

    public enum HighlightStatus {
        GOOD,       // 초록  — "피부 장벽 양호"
        WARN,       // 노랑  — "약간 건조"
        CAUTION     // 빨강  — "홍조 주의"
    }
}
```

**`domain/skin/dto/SkinTypeGapDto.java`**

```java
package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.user.entity.SkinType;

/**
 * "평소 알고 계셨던 타입"과 "오늘 측정에서 관찰된 타입"의 비교. (PRD §4.4.1)
 *
 * 선언 타입이 없으면 이 객체 자체가 null 이고, 앱은 그 자리에
 * "평소 본인 피부는?" 인라인 선택 칩을 띄운다.
 */
public record SkinTypeGapDto(
        SkinType declared,
        SkinType observed,
        boolean matched,
        String message
) {}
```

**`domain/skin/dto/SkinAnalysisResponse.java`**

```java
package com.skinplate.api.domain.skin.dto;

import com.skinplate.api.domain.skin.entity.SkinAnalysis;

import java.time.LocalDateTime;
import java.util.List;

public record SkinAnalysisResponse(
        Long skinAnalysisId,
        int skinScore,
        SkinMetricsDto metrics,
        String summary,
        List<HighlightDto> highlights,
        SkinTypeGapDto skinTypeGap,       // 선언 타입이 없으면 null → 키 생략
        LocalDateTime analyzedAt
) {
    public static SkinAnalysisResponse from(SkinAnalysis entity,
                                            List<HighlightDto> highlights,
                                            SkinTypeGapDto skinTypeGap) {
        return new SkinAnalysisResponse(
                entity.getId(),
                entity.getSkinScore(),
                SkinMetricsDto.from(entity.getMetrics()),
                entity.getSummary(),
                highlights,
                skinTypeGap,
                entity.getCreatedAt());
    }
}
```

---

## 1.18 DTO — 음식 · Skin Plate

**`domain/food/dto/NutritionDto.java`**

```java
package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.Nutrition;

import java.math.BigDecimal;

public record NutritionDto(
        int caloriesKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbG,
        int sodiumMg,
        BigDecimal sugarG
) {
    public static NutritionDto from(Nutrition nutrition) {
        return new NutritionDto(
                nutrition.getCaloriesKcal(),
                nutrition.getProteinG(),
                nutrition.getFatG(),
                nutrition.getCarbG(),
                nutrition.getSodiumMg(),
                nutrition.getSugarG());
    }
}
```

**`domain/food/dto/IngredientDto.java`**

```java
package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.FoodIngredient;

public record IngredientDto(String name, String tag) {

    public static IngredientDto from(FoodIngredient ingredient) {
        return new IngredientDto(ingredient.getName(), ingredient.getTag().name());
    }
}
```

**`domain/food/dto/FoodAnalysisDto.java`**

```java
package com.skinplate.api.domain.food.dto;

import com.skinplate.api.domain.food.entity.FoodAnalysis;

import java.util.List;

public record FoodAnalysisDto(
        Long foodAnalysisId,
        String foodName,
        String foodCategory,
        String cookingMethod,
        boolean spicy,
        List<IngredientDto> ingredients,
        NutritionDto nutrition
) {
    public static FoodAnalysisDto from(FoodAnalysis entity) {
        return new FoodAnalysisDto(
                entity.getId(),
                entity.getFoodName(),
                entity.getFoodCategory(),
                entity.getCookingMethod().name(),
                entity.isSpicy(),
                entity.getIngredients().stream().map(IngredientDto::from).toList(),
                NutritionDto.from(entity.getNutrition()));
    }
}
```

**`domain/plate/dto/FeedbackDto.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

/** GOOD / CAUTION 행. */
public record FeedbackDto(String message, int scoreDelta, String ruleCode) {

    public static FeedbackDto from(SkinPlateFeedback feedback) {
        return new FeedbackDto(feedback.getMessage(), feedback.getScoreDelta(), feedback.getRuleCode());
    }
}
```

**`domain/plate/dto/ActionDto.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

/** ACTION 행. expectedGain은 scoreDelta의 절댓값이 아니라 별도 값이다. */
public record ActionDto(String message, int expectedGain, String ruleCode) {

    public static ActionDto from(SkinPlateFeedback feedback) {
        return new ActionDto(feedback.getMessage(), feedback.getExpectedGain(), feedback.getRuleCode());
    }
}
```

**`domain/plate/dto/FeedbackGroupDto.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

import java.util.List;

/**
 * 좋은 점 / 주의사항 / 추천 행동을 미리 3개 배열로 나눠서 내려준다.
 * Flutter가 타입별로 필터링하지 않고 바로 3개 섹션에 렌더링할 수 있다.
 */
public record FeedbackGroupDto(
        List<FeedbackDto> good,
        List<FeedbackDto> caution,
        List<ActionDto> action
) {
    public static FeedbackGroupDto from(List<SkinPlateFeedback> all) {
        return new FeedbackGroupDto(
                all.stream().filter(f -> f.getType() == FeedbackType.GOOD)
                   .map(FeedbackDto::from).toList(),
                all.stream().filter(f -> f.getType() == FeedbackType.CAUTION)
                   .map(FeedbackDto::from).toList(),
                all.stream().filter(f -> f.getType() == FeedbackType.ACTION)
                   .map(ActionDto::from).toList());
    }
}
```

**`domain/plate/dto/SkinPlateResponse.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.food.dto.FoodAnalysisDto;
import com.skinplate.api.domain.plate.engine.RuleConstants;
import com.skinplate.api.domain.plate.entity.SkinPlate;

import java.time.LocalDateTime;
import java.util.List;

public record SkinPlateResponse(
        Long plateId,

        // 앱은 S07 결과에서 S08 추천으로 넘어가는데(PRD §6), 추천 조회가 이 값을 요구한다.
        // 응답에 없으면 앱이 "최신 피부 분석"을 대신 쓰는 수밖에 없고,
        // 그러면 과거 Plate 를 다시 열었을 때 엉뚱한 날짜의 추천이 뜬다.
        Long skinAnalysisId,

        int plateScore,
        int baseScore,        // 항상 RuleConstants.BASE_SCORE(70). 계산 내역 카드 첫 줄
        String summary,
        FoodAnalysisDto food,
        FeedbackGroupDto feedbacks,
        List<String> appliedRules,
        LocalDateTime createdAt
) {
    /**
     * appliedRules는 엔티티에 JSON 문자열로 저장돼 있으므로
     * Service에서 ObjectMapper로 파싱한 결과를 넘겨받는다.
     * DTO가 ObjectMapper를 들고 있지 않게 하기 위한 선택이다.
     */
    public static SkinPlateResponse from(SkinPlate entity, List<String> appliedRules) {
        return new SkinPlateResponse(
                entity.getId(),
                entity.getSkinAnalysis().getId(),
                entity.getPlateScore(),
                RuleConstants.BASE_SCORE,
                entity.getSummary(),
                FoodAnalysisDto.from(entity.getFoodAnalysis()),
                FeedbackGroupDto.from(entity.getFeedbacks()),
                appliedRules,
                entity.getCreatedAt());
    }
}
```

**`domain/plate/dto/SkinPlateCreateRequest.java`**

```java
package com.skinplate.api.domain.plate.dto;

/**
 * multipart 요청의 image 외 파트.
 * skinAnalysisId가 null이면 Service가 해당 사용자의 최신 피부 분석을 사용한다.
 */
public record SkinPlateCreateRequest(Long skinAnalysisId) {}
```

---

## 1.19 DTO — 추천

**`domain/recommendation/dto/RecommendedFoodDto.java`**

```java
package com.skinplate.api.domain.recommendation.dto;

import com.skinplate.api.domain.recommendation.entity.Recommendation;

public record RecommendedFoodDto(String foodName, String reason) {

    public static RecommendedFoodDto from(Recommendation recommendation) {
        return new RecommendedFoodDto(recommendation.getFoodName(), recommendation.getReason());
    }
}
```

**`domain/recommendation/dto/RecommendationResponse.java`**

```java
package com.skinplate.api.domain.recommendation.dto;

import com.skinplate.api.domain.recommendation.entity.Recommendation;
import com.skinplate.api.domain.recommendation.entity.RecommendationType;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(
        Long skinAnalysisId,
        List<RecommendedFoodDto> recommend,
        List<RecommendedFoodDto> avoid,
        LocalDateTime generatedAt
) {
    public static RecommendationResponse from(Long skinAnalysisId, List<Recommendation> all) {
        return new RecommendationResponse(
                skinAnalysisId,
                filter(all, RecommendationType.RECOMMEND),
                filter(all, RecommendationType.AVOID),
                all.isEmpty() ? null : all.get(0).getCreatedAt());
    }

    private static List<RecommendedFoodDto> filter(List<Recommendation> all, RecommendationType type) {
        return all.stream()
                .filter(r -> r.getType() == type)
                .map(RecommendedFoodDto::from)
                .toList();
    }
}
```

---

## 1.19.1 DTO · Enum — 행동 시뮬레이션

**`domain/plate/entity/PlateActionCode.java`**

```java
package com.skinplate.api.domain.plate.entity;

import com.skinplate.api.domain.food.entity.CookingMethod;
import com.skinplate.api.domain.food.entity.IngredientTag;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 추천 행동을 실행했을 때 음식 정보가 어떻게 바뀌는지 정의한다.
 * 룰 엔진에 다시 넣기 위한 변환 규칙이며, 서버에 저장되지 않는다.
 */
public enum PlateActionCode {

    HALVE_SOUP    ("국물을 절반만 남기기"),
    LESS_SPICY    ("매운 양념 덜어내기"),
    NO_SUGAR_DRINK("단 음료 대신 물"),
    REMOVE_BATTER ("튀김옷 일부 제거");
    // LESS_RICE 는 넣지 않는다. R10(고열량)이 미구현이라 버튼이 붙을 카드가 없다.
    // R10 을 구현하면 그때 함께 추가한다.

    private final String label;

    PlateActionCode(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** 이 행동이 룰 코드와 어떻게 대응되는지 (UI에서 버튼을 어느 카드에 붙일지 결정) */
    public String relatedRuleCode() {
        return switch (this) {
            case HALVE_SOUP     -> "R04";
            case LESS_SPICY     -> "R02";
            case NO_SUGAR_DRINK -> "R03";
            case REMOVE_BATTER  -> "R07";
        };
    }
}
```

**`domain/plate/dto/PlateSimulateRequest.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlateSimulateRequest(
        @NotEmpty(message = "실행할 행동을 하나 이상 선택해 주세요.")
        List<PlateActionCode> actions
) {}
```

**`domain/plate/dto/PlateSimulateResponse.java`**

```java
package com.skinplate.api.domain.plate.dto;

import com.skinplate.api.domain.plate.entity.PlateActionCode;

import java.util.List;

public record PlateSimulateResponse(
        Long plateId,
        int beforeScore,
        int afterScore,
        List<String> appliedActions,
        List<String> removedRules,     // 행동으로 사라진 감점 룰
        String summary
) {
    public static PlateSimulateResponse of(Long plateId, int before, int after,
                                           List<PlateActionCode> actions,
                                           List<String> removedRules,
                                           String summary) {
        return new PlateSimulateResponse(
                plateId, before, after,
                actions.stream().map(Enum::name).toList(),
                removedRules, summary);
    }
}
```

> **저장하지 않는 이유** — `skin_plate.food_analysis_id`에 `UNIQUE`가 걸려 있어(음식 사진 1장 = Plate 1건) 같은 `FoodAnalysis`로 두 번째 Plate를 만들 수 없다. 애초에 저장할 이유도 없다. `PlateRuleEngine`은 DB를 건드리지 않는 순수 계산이라 **재호출이 사실상 공짜**다.

### 1.19.2 시뮬레이션 구현 — 관리 엔티티를 절대 만지지 않는다

**여기가 이 기능에서 유일하게 위험한 지점이다.**

가장 짧은 구현은 `plate.getFoodAnalysis()`로 꺼낸 엔티티를 그 자리에서 고치는 것이다. 그러면 이런 일이 벌어진다.

| 액션 | 짧은 구현 | 결과 |
|---|---|---|
| `LESS_SPICY` | `food.getIngredients().removeIf(i -> i.getTag() == CAPSAICIN)` | `orphanRemoval = true` 이므로 커밋 시 **`food_ingredient`에서 고춧가루 행이 DELETE** 된다 |
| `HALVE_SOUP` | `nutrition.sodiumMg /= 2` | `@Embedded` 필드라 **`food_analysis.sodium_mg`가 925로 영구 변경** 된다 |

무대에서 **[매운 양념 덜어내기]를 누르면 60 → 72가 뜨고, 뒤로 갔다 다시 들어오면 원래 점수가 72다.** 시연 데이터가 조용히 파괴되고, 원인을 그 자리에서 찾을 수 없다.

**반드시 detached 복사본으로 계산한다.**

```java
@Transactional(readOnly = true)   // ★ 안전망 — FlushMode.MANUAL 이라 dirty checking 결과가 DB로 안 나간다
public PlateSimulateResponse simulate(Long userId, Long plateId, List<PlateActionCode> actions) {

    SkinPlate plate = plateRepository.findByIdAndUserId(plateId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLATE_NOT_FOUND));

    FoodAnalysis origin = plate.getFoodAnalysis();
    FoodAnalysis copy = simulate(origin, actions);          // 아래 헬퍼

    SkinMetrics skin = plate.getSkinAnalysis().getMetrics();

    // plate.getAppliedRules()는 JSON 문자열이다. 파싱하는 것보다 원본으로 한 번 더 부르는 게 싸다.
    // 엔진은 DB를 건드리지 않는 순수 계산이므로 재호출이 사실상 공짜다.
    PlateEvaluation before = engine.evaluate(new PlateContext(skin, origin));
    PlateEvaluation after  = engine.evaluate(new PlateContext(skin, copy));

    return PlateSimulateResponse.of(
            plate.getId(), plate.getPlateScore(), after.score(),
            actions, removedRules(before, after), buildActionSummary(actions));
}

/** 행동으로 사라진 감점 룰. S07 에서 "나트륨 과다 카드가 없어졌다"를 보여주는 데 쓴다. */
private List<String> removedRules(PlateEvaluation before, PlateEvaluation after) {
    return before.appliedRuleCodes().stream()
            .filter(code -> !after.appliedRuleCodes().contains(code))
            .toList();
}

/** user = null 인 복사본. 영속성 컨텍스트에 들어가지 않으므로 저장될 길이 없다. */
private FoodAnalysis simulate(FoodAnalysis origin, List<PlateActionCode> actions) {
    boolean lessSpicy     = actions.contains(PlateActionCode.LESS_SPICY);
    boolean removeBatter  = actions.contains(PlateActionCode.REMOVE_BATTER);

    FoodAnalysis copy = FoodAnalysis.create(
            null,                                   // ← 저장 불가 상태로 만든다
            origin.getFoodName(),
            origin.getFoodCategory(),
            adjustNutrition(origin.getNutrition(), actions),
            removeBatter ? CookingMethod.GRILLED : origin.getCookingMethod(),
            !lessSpicy && origin.isSpicy(),
            "{}");

    origin.getIngredients().stream()
            .filter(i -> !(lessSpicy && i.getTag() == IngredientTag.CAPSAICIN))
            .forEach(i -> copy.addIngredient(FoodIngredient.of(i.getName(), i.getTag())));

    return copy;
}

private Nutrition adjustNutrition(Nutrition n, List<PlateActionCode> actions) {
    int sodium    = actions.contains(PlateActionCode.HALVE_SOUP)     ? n.getSodiumMg() / 2 : n.getSodiumMg();
    BigDecimal sugar = actions.contains(PlateActionCode.NO_SUGAR_DRINK)
            ? n.getSugarG().multiply(new BigDecimal("0.4")) : n.getSugarG();

    return Nutrition.of(n.getCaloriesKcal(), n.getProteinG(), n.getFatG(),
                        n.getCarbG(), sodium, sugar);
}
```

> **`readOnly = true`가 핵심 안전망이다.** 누군가 실수로 원본을 건드려도 Hibernate가 `FlushMode.MANUAL`로 동작해 변경이 DB로 나가지 않는다. 주석에 "저장하지 않는다"라고 적어두는 것만으로는 부족하다 — **문제는 저장 여부가 아니라 관리 엔티티를 만지는 것 자체**다.
>
> `REMOVE_BATTER`의 영양 조정에서 `fatG`는 손대지 않았다. **어떤 룰도 `fatG`를 보지 않기 때문에 점수에 영향이 0이다.** 실제 효과는 `cookingMethod = GRILLED`로 R07이 꺼지는 것뿐이다. 없는 효과를 문서에 적어두면 나중에 "왜 지방을 줄였는데 점수가 그대로냐"를 디버깅하게 된다.

---

## 1.20 DTO — OpenAI 응답 매핑

**`infra/openai/dto/OpenAiSkinResult.java`**

```java
package com.skinplate.api.infra.openai.dto;

/**
 * OpenAI Structured Outputs(json_schema) 응답을 그대로 받는 DTO.
 * 필드명이 skin-analysis-schema.json과 1:1로 일치해야 한다.
 */
public record OpenAiSkinResult(
        boolean faceDetected,
        int hydration,
        int oil,
        int redness,
        int trouble,
        int barrier,
        String summary
) {}
```

**`infra/openai/dto/OpenAiFoodResult.java`**

```java
package com.skinplate.api.infra.openai.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpenAiFoodResult(
        boolean foodDetected,
        String foodName,
        String foodCategory,
        String cookingMethod,          // CookingMethod enum 이름과 일치
        boolean spicy,
        List<Ingredient> ingredients,
        Nutrition nutrition
) {
    public record Ingredient(String name, String tag) {}   // tag는 IngredientTag 이름

    public record Nutrition(
            int caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbG,
            int sodiumMg,
            BigDecimal sugarG
    ) {}
}
```

> **AI 응답 DTO를 도메인 Entity와 분리한다.** 프롬프트나 스키마를 바꾸면 이 DTO만 고치면 되고, Entity는 그대로다. 반대로 이 둘을 합치면 프롬프트 실험 한 번에 DB 스키마가 흔들린다.

---

## 1.21 Rule Engine

### 1.21.1 상수

**`domain/plate/engine/RuleConstants.java`**

```java
package com.skinplate.api.domain.plate.engine;

/**
 * 룰 튜닝은 전부 이 파일에서 한다. (PRD v1.1 §18.6)
 *
 * Day 8 캘리브레이션 때 표준 음식 10종으로 값을 조정하게 되는데,
 * 상수가 각 Rule 클래스에 흩어져 있으면 조정이 9개 파일 수정으로 번진다.
 */
public final class RuleConstants {

    private RuleConstants() {}

    /** 모든 Plate가 여기서 출발한다. */
    public static final int BASE_SCORE = 70;

    // ---- 룰별 기본 델타 (severityFactor 적용 전) ----
    public static final int R01_HYDRATION_FOOD   =  8;   // 건조 × 수분/오메가3
    public static final int R02_SPICY_REDNESS    = -10;  // 홍조 × 매운 음식
    public static final int R03_SUGAR_TROUBLE    = -12;  // 트러블 × 당류 과다
    public static final int R04_SODIUM           = -8;   // 나트륨 과다
    public static final int R05_PROTEIN          =  6;   // 단백질 충분
    public static final int R06_VITAMIN          =  5;   // 비타민/항산화
    public static final int R07_FRIED_OIL        = -10;  // 유분 × 튀김
    public static final int R08_OMEGA3_BARRIER   =  7;   // 장벽 약화 × 오메가3
    public static final int R09_PROBIOTIC        =  4;   // 발효식품
    // ---- R10(고열량)은 미구현이다. 델타와 회복 점수를 쌍으로 남겨둔다. ----
    // 문서 §18.6 룰표가 R10을 "확장"으로 명시하고 있으므로 상수도 함께 남긴다.
    // 나중에 넣을 때 값을 다시 정하지 않아도 되고, 지금 지우면 GAIN_LESS_RICE 만
    // 고아가 되거나(둘은 한 쌍이다) 룰표와 코드가 또 어긋난다.
    public static final int R10_HIGH_CALORIE     = -5;   // 고열량 (확장 · 미구현)

    // ---- 추천 행동 시 회복 점수 ----
    public static final int GAIN_SOUP_HALF       = 8;
    public static final int GAIN_LESS_SPICY      = 6;
    public static final int GAIN_WATER_NOT_SODA  = 7;
    public static final int GAIN_REMOVE_BATTER   = 5;
    public static final int GAIN_LESS_RICE       = 4;   // R10 쌍 (확장 · 미구현)

    // ---- 나트륨 초과량 비례 감점 ----
    public static final int SODIUM_STEP_MG       = 500;  // 500mg 초과마다 1점 추가 감점
    public static final int SODIUM_MAX_PENALTY   = 15;

    // ---- 점수 범위 ----
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;
}
```

### 1.21.2 심각도 계수

**`domain/plate/engine/SeverityCalculator.java`**

```java
package com.skinplate.api.domain.plate.engine;

/**
 * 피부 지표가 심각할수록 룰 델타를 키운다.
 *
 * 홍조 62인 사람과 88인 사람에게 같은 라면 점수를 주면 "개인화"라는 말이 무너진다.
 * 심사에서 가장 먼저 나오는 질문이 "이게 진짜 내 피부에 맞춘 건가요?"이고,
 * 이 계수 하나가 그 질문에 대한 답이다.
 */
public final class SeverityCalculator {

    private SeverityCalculator() {}

    private static final double SEVERE   = 1.5;
    private static final double MODERATE = 1.2;
    private static final double NORMAL   = 1.0;

    private static final int SEVERE_THRESHOLD   = 80;
    private static final int MODERATE_THRESHOLD = 60;

    /**
     * @param metricValue   0~100 지표 값
     * @param higherIsWorse oil / redness / trouble 이면 true,
     *                      hydration / barrier 이면 false
     */
    public static double of(int metricValue, boolean higherIsWorse) {
        int severity = higherIsWorse ? metricValue : (100 - metricValue);

        if (severity >= SEVERE_THRESHOLD)   return SEVERE;
        if (severity >= MODERATE_THRESHOLD) return MODERATE;
        return NORMAL;
    }

    /**
     * 델타에 계수를 적용하고 반올림한다.
     *
     * Math.round 는 .5 를 항상 양의 무한대 쪽으로 올린다(-16.5 → -16).
     * 그래서 감점에서 .5 가 나오면 의도보다 1점 약해진다.
     *
     * 지금은 안전하다 — ×1.2 는 정수에 곱해 .5 가 나올 수 없고(6n/5 는 항상 .0/.2/.4/.6/.8),
     * ×1.5 에서 .5 가 나오려면 델타가 홀수여야 하는데 현재 홀수 델타는 R08(+7)뿐이고
     * 양수라 무해하다.
     *
     * 문제는 Day 8 튜닝이다. R02 를 -10 → -11 로 바꾸는 순간 -16.5 → -16 이 되어
     * 룰표보다 1점 약해지고, 그 사실이 아무 데도 안 나타난다.
     * "감점 상수는 짝수로 유지" 같은 관례는 밤 11시에 기억나지 않는다.
     * 절댓값으로 반올림하고 부호를 되돌리면 관례 자체가 필요 없어진다.
     */
    public static int apply(int delta, int metricValue, boolean higherIsWorse) {
        double raw = delta * of(metricValue, higherIsWorse);
        return (int) (raw < 0 ? -Math.round(-raw) : Math.round(raw));
    }
}
```

### 1.21.3 입력 · 출력 타입

**`domain/plate/engine/PlateContext.java`**

```java
package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.food.entity.FoodAnalysis;
import com.skinplate.api.domain.food.entity.Nutrition;
import com.skinplate.api.domain.skin.entity.SkinAnalysis;
import com.skinplate.api.domain.skin.entity.SkinMetrics;

/**
 * 룰 평가에 필요한 모든 입력. 룰은 이것 외에 아무것도 보지 않는다.
 * DB도, 외부 API도 건드리지 않으므로 순수 함수처럼 테스트할 수 있다.
 */
public record PlateContext(SkinMetrics skin, FoodAnalysis food) {

    public static PlateContext of(SkinAnalysis skinAnalysis, FoodAnalysis food) {
        return new PlateContext(skinAnalysis.getMetrics(), food);
    }

    public Nutrition nutrition() {
        return food.getNutrition();
    }
}
```

**`domain/plate/engine/RuleResult.java`**

```java
package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.plate.entity.FeedbackType;

/**
 * 룰 1개의 평가 결과.
 *
 * delta        : 점수에 더해지는 값 (+ 가점 / − 감점)
 * actionMessage: 감점 룰이 제시하는 개선 행동 (없으면 null)
 * expectedGain : 그 행동을 했을 때 회복되는 점수. delta의 절댓값과 다를 수 있다.
 */
public record RuleResult(
        String ruleCode,
        int delta,
        FeedbackType type,
        String message,
        String actionMessage,
        int expectedGain
) {
    public static RuleResult good(String ruleCode, int delta, String message) {
        return new RuleResult(ruleCode, delta, FeedbackType.GOOD, message, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION, message, null, 0);
    }

    public static RuleResult caution(String ruleCode, int delta, String message,
                                     String actionMessage, int expectedGain) {
        return new RuleResult(ruleCode, delta, FeedbackType.CAUTION,
                              message, actionMessage, expectedGain);
    }

    public boolean hasAction() {
        return actionMessage != null && !actionMessage.isBlank();
    }
}
```

**`domain/plate/engine/PlateEvaluation.java`**

```java
package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.plate.entity.FeedbackType;
import com.skinplate.api.domain.plate.entity.SkinPlateFeedback;

import java.util.ArrayList;
import java.util.List;

/** 엔진 실행 결과. Service는 이 값을 SkinPlate 엔티티로 옮기기만 하면 된다. */
public record PlateEvaluation(
        int score,
        List<RuleResult> results,
        String summary
) {
    public List<String> appliedRuleCodes() {
        return results.stream().map(RuleResult::ruleCode).toList();
    }

    /**
     * RuleResult 목록을 화면 표시 순서대로 SkinPlateFeedback 엔티티로 변환한다.
     * 좋은 점 → 주의사항 → 추천 행동 순서가 그대로 displayOrder가 된다.
     */
    public List<SkinPlateFeedback> toFeedbacks() {
        List<SkinPlateFeedback> feedbacks = new ArrayList<>();
        int order = 0;

        for (RuleResult r : results) {
            if (r.type() == FeedbackType.GOOD) {
                feedbacks.add(SkinPlateFeedback.good(r.ruleCode(), r.message(), r.delta(), order++));
            }
        }
        for (RuleResult r : results) {
            if (r.type() == FeedbackType.CAUTION) {
                feedbacks.add(SkinPlateFeedback.caution(r.ruleCode(), r.message(), r.delta(), order++));
            }
        }
        for (RuleResult r : results) {
            if (r.hasAction()) {
                feedbacks.add(SkinPlateFeedback.action(
                        r.ruleCode(), r.actionMessage(), r.expectedGain(), order++));
            }
        }
        return feedbacks;
    }
}
```

### 1.21.4 룰 인터페이스와 엔진

**`domain/plate/engine/PlateRule.java`**

```java
package com.skinplate.api.domain.plate.engine;

/**
 * 룰 하나를 추가하려면 이 인터페이스를 구현한 @Component 클래스를 만들면 끝이다.
 * 엔진 코드도, 기존 룰도 건드리지 않는다.
 */
public interface PlateRule {

    /** "R04" 같은 고유 코드. 응답의 appliedRules와 피드백 추적에 쓰인다. */
    String code();

    /** 낮을수록 먼저 평가된다. 화면 노출 우선순위와 같다. */
    default int priority() { return 100; }

    /** 이 룰이 적용되는 상황인가 */
    boolean supports(PlateContext context);

    /** supports가 true일 때만 호출된다 */
    RuleResult apply(PlateContext context);
}
```

**`domain/plate/engine/PlateRuleEngine.java`**

```java
package com.skinplate.api.domain.plate.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

/**
 * Skin Plate Score 계산기.
 *
 * 점수를 LLM에 맡기지 않는 이유:
 *   같은 사진을 두 번 찍으면 다른 점수가 나오고, 그러면 무대에서 설명할 수 없다.
 *   규칙 기반 계산은 항상 같은 결과를 낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlateRuleEngine {

    /** Spring이 PlateRule 구현체를 전부 주입한다. 룰 추가 = 클래스 추가. */
    private final List<PlateRule> rules;

    public PlateEvaluation evaluate(PlateContext context) {

        List<RuleResult> applied = rules.stream()
                .sorted(Comparator.comparingInt(PlateRule::priority)
                                  .thenComparing(PlateRule::code))   // 순서 고정
                .filter(rule -> rule.supports(context))
                .map(rule -> rule.apply(context))
                .toList();

        int raw = BASE_SCORE + applied.stream().mapToInt(RuleResult::delta).sum();
        int score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, raw));

        log.debug("Plate 평가 결과 {}점 (원점수 {}), 적용 룰 {}",
                  score, raw, applied.stream().map(RuleResult::ruleCode).toList());

        return new PlateEvaluation(score, applied, buildSummary(applied));
    }

    /** 좋은 점 1개 + 주의사항 1개를 엮어 한 문장으로 만든다. */
    private String buildSummary(List<RuleResult> results) {
        String goods = results.stream()
                .filter(r -> r.delta() > 0)
                .map(RuleResult::message)
                .limit(2)
                .collect(Collectors.joining(", "));

        String cautions = results.stream()
                .filter(r -> r.delta() < 0)
                .map(RuleResult::message)
                .limit(2)
                .collect(Collectors.joining(", "));

        if (goods.isBlank() && cautions.isBlank()) return "특별한 주의사항이 없는 무난한 한 끼입니다.";
        if (cautions.isBlank()) return goods + ". 오늘 피부에 잘 맞는 선택입니다.";
        if (goods.isBlank())    return cautions + ". 오늘 피부에는 부담이 될 수 있습니다.";
        return goods + ". 다만 " + cautions + ".";
    }
}
```

### 1.21.5 룰 구현 (9종)

**`domain/plate/engine/rules/SodiumRule.java`** — R04

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.PlateContext;
import com.skinplate.api.domain.plate.engine.PlateRule;
import com.skinplate.api.domain.plate.engine.RuleResult;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SodiumRule implements PlateRule {

    @Override public String code()    { return "R04"; }
    @Override public int    priority() { return 10; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighSodium();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int excess = context.nutrition().sodiumExcessMg();
        int penalty = Math.min(SODIUM_MAX_PENALTY, Math.abs(R04_SODIUM) + excess / SODIUM_STEP_MG);

        String action = context.food().isSoup()
                ? "국물을 절반만 남기면 Skin Plate 점수가 상승합니다."
                : "간이 센 반찬은 절반만 드셔보세요.";

        return RuleResult.caution(code(), -penalty, "나트륨 과다", action, GAIN_SOUP_HALF);
    }
}
```

**`domain/plate/engine/rules/SpicyRednessRule.java`** — R02

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SpicyRednessRule implements PlateRule {

    @Override public String code()    { return "R02"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().hasRedness() && context.food().isSpicyFood();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(
                R02_SPICY_REDNESS, context.skin().getRedness(), true);

        return RuleResult.caution(code(), delta,
                "매운맛 자극",
                "매운 양념을 덜어내고 드셔보세요.", GAIN_LESS_SPICY);
    }
}
```

**`domain/plate/engine/rules/SugarTroubleRule.java`** — R03

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class SugarTroubleRule implements PlateRule {

    @Override public String code()    { return "R03"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().hasTrouble() && context.nutrition().isHighSugar();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(
                R03_SUGAR_TROUBLE, context.skin().getTrouble(), true);

        return RuleResult.caution(code(), delta,
                "당류 과다",
                "단 음료 대신 물을 곁들이세요.", GAIN_WATER_NOT_SODA);
    }
}
```

**`domain/plate/engine/rules/FriedOilRule.java`** — R07

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class FriedOilRule implements PlateRule {

    @Override public String code()    { return "R07"; }
    @Override public int    priority() { return 20; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isOily() && context.food().isFried();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(R07_FRIED_OIL, context.skin().getOil(), true);

        return RuleResult.caution(code(), delta,
                "튀김 조리",
                "튀김옷을 일부 제거해 보세요.", GAIN_REMOVE_BATTER);
    }
}
```

**`domain/plate/engine/rules/HydrationFoodRule.java`** — R01

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class HydrationFoodRule implements PlateRule {

    @Override public String code()    { return "R01"; }
    @Override public int    priority() { return 30; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isDry()
                && context.food().hasAnyTag(IngredientTag.OMEGA3, IngredientTag.VITAMIN_A);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        // hydration은 낮을수록 나쁘므로 higherIsWorse = false
        int delta = SeverityCalculator.apply(
                R01_HYDRATION_FOOD, context.skin().getHydration(), false);

        return RuleResult.good(code(), delta, "수분 보충 재료");
    }
}
```

**`domain/plate/engine/rules/Omega3BarrierRule.java`** — R08

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.*;

@Component
public class Omega3BarrierRule implements PlateRule {

    @Override public String code()    { return "R08"; }
    @Override public int    priority() { return 30; }

    @Override
    public boolean supports(PlateContext context) {
        return context.skin().isBarrierWeak() && context.food().hasTag(IngredientTag.OMEGA3);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        int delta = SeverityCalculator.apply(
                R08_OMEGA3_BARRIER, context.skin().getBarrier(), false);

        return RuleResult.good(code(), delta, "오메가3 함유");
    }
}
```

**`domain/plate/engine/rules/ProteinRule.java`** — R05

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R05_PROTEIN;

@Component
public class ProteinRule implements PlateRule {

    @Override public String code()    { return "R05"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.nutrition().isHighProtein();
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R05_PROTEIN, "단백질 충분");
    }
}
```

**`domain/plate/engine/rules/VitaminRule.java`** — R06

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R06_VITAMIN;

@Component
public class VitaminRule implements PlateRule {

    @Override public String code()    { return "R06"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasAnyTag(
                IngredientTag.VITAMIN_C, IngredientTag.VITAMIN_A, IngredientTag.ANTIOXIDANT);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R06_VITAMIN, "비타민 풍부");
    }
}
```

**`domain/plate/engine/rules/ProbioticRule.java`** — R09

```java
package com.skinplate.api.domain.plate.engine.rules;

import com.skinplate.api.domain.food.entity.IngredientTag;
import com.skinplate.api.domain.plate.engine.*;
import org.springframework.stereotype.Component;

import static com.skinplate.api.domain.plate.engine.RuleConstants.R09_PROBIOTIC;

@Component
public class ProbioticRule implements PlateRule {

    @Override public String code()    { return "R09"; }
    @Override public int    priority() { return 40; }

    @Override
    public boolean supports(PlateContext context) {
        return context.food().hasTag(IngredientTag.PROBIOTIC);
    }

    @Override
    public RuleResult apply(PlateContext context) {
        return RuleResult.good(code(), R09_PROBIOTIC, "발효식품 포함");
    }
}
```

> **R10(고열량, 확장)은 넣지 않았다.** 룰이 많아질수록 점수 설명이 길어지고, 해커톤 시연에서는 피드백 4~5개가 화면에 딱 맞는다. 필요하면 클래스 하나만 추가하면 된다 — 그게 이 구조의 요점이다.

### 1.21.6 검증 — 예시 계산 재현 테스트

**`src/test/java/com/skinplate/api/domain/plate/engine/PlateRuleEngineTest.java`**

```java
package com.skinplate.api.domain.plate.engine;

import com.skinplate.api.domain.food.entity.*;
import com.skinplate.api.domain.plate.engine.rules.*;
import com.skinplate.api.domain.skin.entity.SkinMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD v1.1 §18.7 예시 계산을 그대로 재현한다.
 * 룰 임계값을 튜닝할 때 이 테스트가 깨지면 문서도 함께 고쳐야 한다는 신호다.
 */
class PlateRuleEngineTest {

    /** 건조 38 · 유분 52 · 홍조 64 · 트러블 25 · 장벽 78 */
    private static final SkinMetrics SKIN = SkinMetrics.of(38, 52, 64, 25, 78);

    private final PlateRuleEngine engine = new PlateRuleEngine(List.of(
            new SodiumRule(), new SpicyRednessRule(), new SugarTroubleRule(),
            new FriedOilRule(), new HydrationFoodRule(), new Omega3BarrierRule(),
            new ProteinRule(), new VitaminRule(), new ProbioticRule()));

    @Test
    @DisplayName("예시 A · 돼지고기 김치찌개 → 60점")
    void kimchiStew() {
        FoodAnalysis food = food("돼지고기 김치찌개", CookingMethod.BOILED, true,
                nutrition(520, "28.5", 1850, "6.2"),
                List.of(FoodIngredient.of("김치", IngredientTag.PROBIOTIC),
                        FoodIngredient.of("고춧가루", IngredientTag.CAPSAICIN)));

        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, food));

        // 70 + R05(+6) + R09(+4) + R04(-8) + R02(-12) = 60
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.appliedRuleCodes()).containsExactlyInAnyOrder("R02", "R04", "R05", "R09");
    }

    @Test
    @DisplayName("예시 B · 연어구이 정식 → 87점")
    void grilledSalmon() {
        FoodAnalysis food = food("연어구이 정식", CookingMethod.GRILLED, false,
                nutrition(610, "32.0", 1600, "4.0"),
                List.of(FoodIngredient.of("연어", IngredientTag.OMEGA3),
                        FoodIngredient.of("브로콜리", IngredientTag.ANTIOXIDANT),
                        FoodIngredient.of("된장", IngredientTag.PROBIOTIC)));

        PlateEvaluation result = engine.evaluate(new PlateContext(SKIN, food));

        // 70 + R01(+10) + R05(+6) + R06(+5) + R09(+4) + R04(-8) = 87
        assertThat(result.score()).isEqualTo(87);
    }

    @Test
    @DisplayName("나트륨 초과량에 비례해 감점이 커지고 -15에서 잘린다")
    void sodiumScalesWithExcess() {
        // 두 예시(1850·1600)는 초과량이 500 미만이라 비례 분기가 한 번도 돌지 않는다.
        // Day 8 에 만질 로직이므로 여기서 덮어둔다.
        FoodAnalysis mild = food("간장국", CookingMethod.BOILED, false,
                nutrition(300, "5.0", 2100, "2.0"), List.of());     // excess 600 → 8 + 1 = 9
        FoodAnalysis extreme = food("소금덩어리", CookingMethod.BOILED, false,
                nutrition(300, "5.0", 9000, "2.0"), List.of());     // excess 7500 → 8 + 15 → 15로 잘림

        SkinMetrics neutral = SkinMetrics.of(50, 50, 50, 50, 50);   // 다른 룰이 안 걸리는 지표

        assertThat(engine.evaluate(new PlateContext(neutral, mild)).score())
                .isEqualTo(70 - 9);
        assertThat(engine.evaluate(new PlateContext(neutral, extreme)).score())
                .isEqualTo(70 - 15);
    }

    @Test
    @DisplayName("같은 음식이라도 홍조가 심하면 더 크게 감점된다")
    void severityMatters() {
        FoodAnalysis spicy = food("라면", CookingMethod.BOILED, true,
                nutrition(500, "10.0", 1800, "5.0"), List.of());

        int mild   = engine.evaluate(new PlateContext(SkinMetrics.of(50, 50, 62, 30, 60), spicy)).score();
        int severe = engine.evaluate(new PlateContext(SkinMetrics.of(50, 50, 88, 30, 60), spicy)).score();

        assertThat(severe).isLessThan(mild);
    }

    // ---- helpers ----

    private static FoodAnalysis food(String name, CookingMethod method, boolean spicy,
                                     Nutrition nutrition, List<FoodIngredient> ingredients) {
        FoodAnalysis food = FoodAnalysis.create(
                null, name, "한식", nutrition, method, spicy, "{}");
        food.addIngredients(ingredients);
        return food;
    }

    private static Nutrition nutrition(int kcal, String protein, int sodium, String sugar) {
        return Nutrition.of(kcal, new BigDecimal(protein), BigDecimal.ZERO,
                            BigDecimal.ZERO, sodium, new BigDecimal(sugar));
    }
}
```

> **이 테스트가 스켈레톤에서 가장 값어치 있는 파일이다.** 룰 임계값을 조정하다 보면 문서의 예시와 실제 계산이 조용히 어긋난다. 그러면 발표 때 "87점이라고 했는데 83점이 나왔다"가 무대 위에서 드러난다. 테스트가 먼저 깨지면 그럴 일이 없다.

---

## 1.22 정적 데이터 — 위치를 지금 정해둔다

문서에 표로만 있고 "어느 클래스에 둘지"가 정해지지 않은 데이터가 둘 있다. Day 7에 그 자리에서 정하면 반나절이 설계에 쓰인다.

### 1.22.1 시연용 표준 영양 테이블

**`domain/food/service/StandardNutrition.java`**

```java
package com.skinplate.api.domain.food.service;

import com.skinplate.api.domain.food.entity.Nutrition;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI가 사진에서 추정한 영양값을 표준값으로 덮어쓴다. (시연 음식 한정)
 *
 * 왜 필요한가:
 *   룰 엔진은 nutrition.sodiumMg 를 1500과 비교한다. 그런데 그 1850mg 은
 *   AI가 사진을 보고 추정한 값이라 호출할 때마다 흔들린다.
 *     1400 → R04 미발동 → 68점
 *     1850 → 60점
 *     2100 → 59점
 *   같은 사진, 같은 사람, 세 번 다른 점수다. 심사위원의 첫 질문이 이것이고,
 *   현장에서 두 번 찍으면 들킨다.
 *
 * 정직성:
 *   숨기지 않는다. 화면에 "표준 영양 DB 기준"이라고 표기하고,
 *   "AI는 무슨 음식인지 판단하고, 영양값은 표준 DB에서 가져옵니다"라고 설명한다.
 *   오히려 이게 강점이 된다 — 세 단계 모두 재현 가능해진다.
 */
public final class StandardNutrition {

    private StandardNutrition() {}

    /**
     * LinkedHashMap 이어야 한다. Map.of 는 반복 순서가 JVM 실행마다 달라져서,
     * "김치라면" 같은 이름이 오면 findFirst() 가 김치찌개를 잡을지 라면을 잡을지
     * 실행할 때마다 바뀐다. 재현성을 위해 만든 테이블이 재현 불가가 되는 셈이다.
     * 위에 있을수록 우선한다 — 더 구체적인 이름을 먼저 둔다.
     */
    private static final Map<String, Nutrition> TABLE = new LinkedHashMap<>() {{
        put("김치찌개", n(520, "28.5", "24.0", "32.0", 1850, "6.2"));
        put("연어구이", n(610, "32.0", "28.0", "45.0", 1600, "4.0"));
        put("라면",     n(500, "10.0", "17.0", "73.0", 1800, "5.0"));
    }};

    /** 음식명에 표준 키가 포함되면 표준값을 반환한다. */
    public static Optional<Nutrition> find(String foodName) {
        if (foodName == null) return Optional.empty();
        return TABLE.entrySet().stream()
                .filter(e -> foodName.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public static boolean isStandard(String foodName) {
        return find(foodName).isPresent();
    }

    private static Nutrition n(int kcal, String protein, String fat,
                               String carb, int sodium, String sugar) {
        return Nutrition.of(kcal, new BigDecimal(protein), new BigDecimal(fat),
                            new BigDecimal(carb), sodium, new BigDecimal(sugar));
    }
}
```

`FoodAnalysisService`에서 AI 응답을 받은 직후 한 줄로 적용한다.

```java
Nutrition nutrition = StandardNutrition.find(ai.foodName())
                                       .orElseGet(() -> toNutrition(ai.nutrition()));
```

> **표준 음식 3종은 Day 5 이전에 확정하라.** Day 8 캘리브레이션과 발표 시연 음식이 이 목록에 달려 있다. 지금 정하면 둘 다 미리 준비된다.

### 1.22.2 추천 후보 매핑

**`domain/recommendation/service/RecommendationCandidates.java`**

```java
package com.skinplate.api.domain.recommendation.service;

import com.skinplate.api.domain.skin.entity.SkinMetrics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 취약 항목 → 후보 음식 매핑. (PRD §18.9)
 *
 * 음식 선정은 규칙, 문장 생성만 AI.
 * LLM이 매번 다른 음식을 추천하면 데모마다 결과가 달라져 설명할 수 없다.
 */
public final class RecommendationCandidates {

    private RecommendationCandidates() {}

    public enum Concern { DRY, REDNESS, TROUBLE, OILY, BARRIER_WEAK }

    public record Candidates(List<String> recommend, List<String> avoid) {}

    private static final Map<Concern, Candidates> TABLE = Map.of(
            Concern.DRY,          new Candidates(List.of("연어", "아보카도", "오이", "견과류"),
                                                 List.of("커피", "술")),
            Concern.REDNESS,      new Candidates(List.of("브로콜리", "녹차", "토마토"),
                                                 List.of("매운 음식", "술")),
            Concern.TROUBLE,      new Candidates(List.of("키위", "고구마", "견과류"),
                                                 List.of("탄산음료", "초콜릿", "튀김")),
            Concern.OILY,         new Candidates(List.of("채소", "두부", "흰살생선"),
                                                 List.of("튀김", "라면", "패스트푸드")),
            Concern.BARRIER_WEAK, new Candidates(List.of("연어", "달걀", "아몬드"),
                                                 List.of("인스턴트", "가공육")));

    public static Candidates of(Concern concern) {
        return TABLE.get(concern);
    }

    /**
     * <b>실제로 취약한</b> 항목만 심각한 순으로 최대 N개. 없으면 빈 목록이다.
     *
     * 판정은 SkinMetrics 의 판정자를 그대로 쓴다. 여기에 임계값을 다시 적으면
     * 같은 뜻의 숫자가 두 곳에 생기고, Rule Engine 은 "건조하지 않다"고 보는 지표를
     * 추천만 "건조하다"고 보는 날이 온다.
     *
     * 거르지 않으면 피부가 멀쩡해도 상위 두 개가 뽑힌다 — 심사위원이 본인 얼굴로
     * 찍어 보는 순간이 정확히 그 경우다.
     */
    public static List<Concern> topConcerns(SkinMetrics m, int n) {
        record Scored(Concern concern, boolean present, int severity) {}

        return List.of(
                        new Scored(Concern.DRY,          m.isDry(),         100 - m.getHydration()),
                        new Scored(Concern.BARRIER_WEAK, m.isBarrierWeak(), 100 - m.getBarrier()),
                        new Scored(Concern.OILY,         m.isOily(),        m.getOil()),
                        new Scored(Concern.REDNESS,      m.hasRedness(),    m.getRedness()),
                        new Scored(Concern.TROUBLE,      m.hasTrouble(),    m.getTrouble()))
                .stream()
                .filter(Scored::present)
                // 동점이면 순서가 흔들려 같은 지표에 다른 추천이 나온다. 이름으로 고정한다.
                .sorted(Comparator.comparingInt(Scored::severity).reversed()
                                  .thenComparing(scored -> scored.concern().name()))
                .limit(n)
                .map(Scored::concern)
                .toList();
    }

    /**
     * 음식별 추천 문구. 후보 표의 24개 음식이 각자의 문장을 갖는다.
     * 항목별로 한 문장씩 두면 같은 취약 항목에서 나온 음식들이 글자까지 같은 문장을
     * 달고 줄줄이 뜬다 — S08 은 영상에 나가는 화면이다. (전문은 구현 파일 참조)
     */
    private static final Map<String, String> REASONS = Map.ofEntries(/* 음식명 → 문구 24개 */);

    /** 표에 없는 음식이면 이름만 남긴다 — 문구가 없다고 추천이 사라지면 안 된다. */
    public static String reasonOf(String foodName) {
        return REASONS.getOrDefault(foodName, "");
    }
}
```

> **AI 문장 생성이 실패해도 화면은 비지 않는다.** 후보 음식이 코드에 있으므로 정적 이유 문구로 폴백하면 된다. PRD의 G4 축소 경로("추천을 정적 문구로 대체")가 그제서야 실제로 동작하는 경로가 된다.

---

# Part 2 · Frontend (Flutter)

## 2.1 전체 파일 목록

```
lib/
├── core/
│   ├── config/env.dart
│   ├── result/result.dart
│   ├── error/failure.dart
│   ├── storage/token_storage.dart
│   └── network/
│       ├── api_envelope.dart
│       ├── auth_interceptor.dart
│       ├── unauthorized_interceptor.dart
│       └── dio_client.dart
├── shared/enums/
│   ├── skin_type.dart
│   ├── plate_action_code.dart
│   ├── feedback_type.dart
│   ├── highlight_status.dart
│   ├── ingredient_tag.dart
│   ├── cooking_method.dart
│   └── recommendation_type.dart
└── features/
    ├── auth/
    │   ├── domain/entities/auth_user.dart          # AuthUser · AuthSession
    │   ├── domain/repositories/auth_repository.dart
    │   ├── presentation/pages/skin_type_page.dart  # S01c (건너뛰기 가능)
    │   └── data/models/auth_dtos.dart
    ├── skin_analysis/
    │   ├── domain/entities/skin_analysis.dart
    │   ├── domain/repositories/skin_repository.dart
    │   └── data/models/skin_dtos.dart
    ├── skin_plate/
    │   ├── domain/entities/skin_plate.dart
    │   ├── domain/repositories/plate_repository.dart
    │   └── data/models/plate_dtos.dart
    └── recommendation/
        ├── domain/entities/recommendation.dart
        ├── domain/repositories/recommendation_repository.dart
        └── data/models/recommendation_dtos.dart
```

> **DTO와 Entity를 왜 나누는가** — DTO는 서버 JSON의 모양을 그대로 따른다. Entity는 화면이 쓰기 편한 모양이다. 지금은 거의 같아 보이지만, 서버가 필드 하나를 바꾸면 그 충격이 DTO에서 멈춘다. 화면 20곳을 고치는 대신 `toEntity()` 한 줄을 고치면 된다.

---

## 2.2 pubspec.yaml

```yaml
name: skinplate
description: 오늘의 피부를 위한 오늘의 한 끼
publish_to: 'none'
version: 0.1.0+1

environment:
  sdk: '>=3.5.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter

  # 상태관리
  flutter_riverpod: ^2.5.1
  riverpod_annotation: ^2.3.5

  # 라우팅
  go_router: ^14.2.7

  # 네트워크
  dio: ^5.7.0

  # 모델
  freezed_annotation: ^2.4.4
  json_annotation: ^4.9.0

  # 저장소
  flutter_secure_storage: ^9.2.2

  # 카메라 · 이미지
  image_picker: ^1.1.2
  camera: ^0.11.0                       # 실시간 얼굴 게이트용 프리뷰
  flutter_image_compress: ^2.3.0
  image: ^4.2.0                         # 얼굴 크롭 + 평균 휘도 계산

  # 온디바이스 얼굴 감지 (게이트 + 크롭 전용, 피부 판정에는 쓰지 않는다)
  google_mlkit_face_detection: ^0.11.1

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^4.0.0
  build_runner: ^2.4.13
  freezed: ^2.5.7
  json_serializable: ^6.8.0
  riverpod_generator: ^2.4.3

flutter:
  uses-material-design: true
```

코드 생성:

```bash
dart run build_runner build --delete-conflicting-outputs
```

---

## 2.3 core/config

**`lib/core/config/env.dart`**

```dart
/// 빌드 시점에 --dart-define 으로 주입한다.
///
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
///
/// Android 에뮬레이터의 호스트는 10.0.2.2, iOS 시뮬레이터는 localhost다.
/// 이 한 줄 때문에 반나절을 잃는 팀이 매번 나온다.
class Env {
  const Env._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',
  );

  /// 서버 없이 UI만 확인할 때 사용하는 로컬 목업 모드.
  static const bool mockMode = bool.fromEnvironment('MOCK_MODE');

  static const Duration connectTimeout = Duration(seconds: 10);

  /// 서버 AI 타임아웃이 25초(단발, 재시도 없음)이므로 32초면 충분하다.
  ///
  /// 이 값이 서버보다 짧으면 서버는 살아서 GPT를 붙들고 있는데 앱만 포기한 상태가
  /// 되고, 사용자가 재시도를 누르면 같은 일이 반복된다. 서버보다 길되,
  /// 무제한(0)으로 두면 네트워크가 끊겼을 때 로딩 화면에서 못 빠져나온다.
  static const Duration receiveTimeout = Duration(seconds: 32);
}
```

---

## 2.4 core/result · error

**`lib/core/error/failure.dart`**

```dart
/// 화면이 분기해야 하는 실패 종류만 정의한다.
/// 서버 에러 코드를 그대로 노출하지 않고 여기서 한 번 번역한다.
sealed class Failure {
  const Failure(this.message);
  final String message;
}

/// 네트워크 연결 자체가 안 됨 → "인터넷 연결을 확인해 주세요" + 재시도 버튼
class NetworkFailure extends Failure {
  const NetworkFailure([super.message = '인터넷 연결을 확인해 주세요.']);
}

/// 인증 만료·무효 → 조용히 로그인 화면으로
class AuthFailure extends Failure {
  const AuthFailure(super.message, {this.expired = false});
  final bool expired;
}

/// 서버가 비즈니스 에러를 명시적으로 내려준 경우 → 메시지 그대로 노출
class ServerFailure extends Failure {
  const ServerFailure(this.code, super.message);
  final String code;
}

/// AI 분석 실패 (얼굴/음식 미인식, 타임아웃) → 재촬영 유도
class AnalysisFailure extends Failure {
  const AnalysisFailure(this.code, super.message);
  final String code;

  bool get shouldRetakePhoto =>
      code == 'FACE_NOT_DETECTED' || code == 'FOOD_NOT_DETECTED';
}

class UnknownFailure extends Failure {
  const UnknownFailure([super.message = '일시적인 오류가 발생했습니다.']);
}
```

**`lib/core/result/result.dart`**

```dart
import '../error/failure.dart';

/// 예외를 던지지 않고 성공/실패를 값으로 다룬다.
/// UseCase가 Result를 반환하면 화면은 try-catch 없이 when으로 분기하면 된다.
sealed class Result<T> {
  const Result();

  R when<R>({
    required R Function(T data) success,
    required R Function(Failure failure) failure,
  }) =>
      switch (this) {
        Success<T>(:final data) => success(data),
        FailureResult<T>(:final error) => failure(error),
      };

  T? get dataOrNull => this is Success<T> ? (this as Success<T>).data : null;

  bool get isSuccess => this is Success<T>;
}

class Success<T> extends Result<T> {
  const Success(this.data);
  final T data;
}

class FailureResult<T> extends Result<T> {
  const FailureResult(this.error);
  final Failure error;
}
```

---

## 2.5 core/storage

**`lib/core/storage/token_storage.dart`**

```dart
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// JWT를 기기 보안 저장소(iOS Keychain / Android Keystore)에 보관한다.
///
/// SharedPreferences를 쓰지 않는 이유: 평문으로 남는다.
/// 코드량 차이는 거의 없으므로 처음부터 안전한 쪽을 쓴다.
class TokenStorage {
  static const _accessTokenKey = 'access_token';

  final FlutterSecureStorage _storage;

  const TokenStorage([
    this._storage = const FlutterSecureStorage(
      aOptions: AndroidOptions(encryptedSharedPreferences: true),
    ),
  ]);

  Future<void> save(String token) =>
      _storage.write(key: _accessTokenKey, value: token);

  Future<String?> read() => _storage.read(key: _accessTokenKey);

  Future<void> clear() => _storage.delete(key: _accessTokenKey);

  Future<bool> get hasToken async => (await read()) != null;
}
```

> **웹에서는 보안 저장소가 없다.** `flutter_secure_storage`는 웹에서 브라우저 저장소(localStorage 수준)로 폴백한다 — API는 그대로라 코드를 나눌 필요는 없지만, **보안 등급이 앱과 다르다는 사실은 알고 있어야 한다**(PRD §6.1). 시연 범위에서는 문제가 아니다. 웹은 심사위원 체험용이고 토큰 유효기간이 7일이며, 그 안에 담기는 것은 피부 분석 기록뿐이다.

---

## 2.6 core/network

**`lib/core/network/api_envelope.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

part 'api_envelope.freezed.dart';
part 'api_envelope.g.dart';

/// 서버 공통 응답 래퍼.
///   { "success": true, "data": {...}, "error": null }
@Freezed(genericArgumentFactories: true)
class ApiEnvelope<T> with _$ApiEnvelope<T> {
  const factory ApiEnvelope({
    required bool success,
    T? data,
    ApiErrorBody? error,
  }) = _ApiEnvelope<T>;

  factory ApiEnvelope.fromJson(
    Map<String, dynamic> json,
    T Function(Object?) fromJsonT,
  ) =>
      _$ApiEnvelopeFromJson(json, fromJsonT);
}

@freezed
class ApiErrorBody with _$ApiErrorBody {
  const factory ApiErrorBody({
    required String code,
    required String message,
  }) = _ApiErrorBody;

  factory ApiErrorBody.fromJson(Map<String, dynamic> json) =>
      _$ApiErrorBodyFromJson(json);
}
```

**`lib/core/network/auth_interceptor.dart`**

```dart
import 'package:dio/dio.dart';

import '../storage/token_storage.dart';

/// 모든 요청에 Authorization 헤더를 자동으로 붙인다.
/// 각 DataSource가 헤더를 챙기면 언젠가 한 곳을 빠뜨린다.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokenStorage);

  final TokenStorage _tokenStorage;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _tokenStorage.read();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }
}
```

**`lib/core/network/unauthorized_interceptor.dart`**

```dart
import 'package:dio/dio.dart';

import '../storage/token_storage.dart';

/// 401을 한 곳에서 처리한다.
///
/// 만료 토큰으로 앱을 열었을 때 화면마다 에러 토스트가 뜨는 대신,
/// 토큰을 지우고 조용히 로그인 화면으로 넘어가게 만든다.
/// (실제 화면 전환은 AuthNotifier의 상태 변화를 go_router가 감지해서 수행)
class UnauthorizedInterceptor extends Interceptor {
  UnauthorizedInterceptor(this._tokenStorage, this._onUnauthorized);

  final TokenStorage _tokenStorage;
  final void Function() _onUnauthorized;

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    // /auth/ 요청은 제외한다.
    // 로그인 실패도 401(INVALID_CREDENTIALS)이라 그대로 두면,
    // 비밀번호를 한 번 틀렸을 뿐인데 토큰이 지워지고 화면이 리다이렉트되어
    // 입력하던 폼과 에러 메시지가 함께 사라진다.
    final isAuthRequest = err.requestOptions.path.contains('/auth/');

    if (err.response?.statusCode == 401 && !isAuthRequest) {
      await _tokenStorage.clear();
      _onUnauthorized();
    }
    handler.next(err);
  }
}
```

**`lib/core/network/dio_client.dart`**

```dart
import 'package:dio/dio.dart';

import '../config/env.dart';
import '../error/failure.dart';
import '../storage/token_storage.dart';
import 'auth_interceptor.dart';
import 'unauthorized_interceptor.dart';

class DioClient {
  DioClient({
    required TokenStorage tokenStorage,
    required void Function() onUnauthorized,
  }) : dio = Dio(BaseOptions(
          baseUrl: Env.apiBaseUrl,
          connectTimeout: Env.connectTimeout,
          receiveTimeout: Env.receiveTimeout,
          contentType: 'application/json',
          // validateStatus는 건드리지 않는다. Dio 기본값(2xx만 성공)이어야
          // 4xx가 DioException으로 흘러 UnauthorizedInterceptor와
          // mapToFailure의 에러 코드 분기가 동작한다.
        )) {
    dio.interceptors.addAll([
      AuthInterceptor(tokenStorage),
      UnauthorizedInterceptor(tokenStorage, onUnauthorized),
    ]);
  }

  final Dio dio;
}

/// DioException과 서버 error 본문을 화면이 이해하는 Failure로 번역한다.
/// 이 매핑이 한 곳에 있어야 에러 처리 UX가 화면마다 달라지지 않는다.
Failure mapToFailure(Object error) {
  if (error is! DioException) return const UnknownFailure();

  switch (error.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.connectionError:
      return const NetworkFailure();
    case DioExceptionType.receiveTimeout:
    case DioExceptionType.sendTimeout:
      return const AnalysisFailure('AI_TIMEOUT', '분석이 지연되고 있습니다. 다시 시도해 주세요.');
    default:
      break;
  }

  final body = error.response?.data;
  if (body is Map<String, dynamic>) {
    final errorBody = body['error'];
    if (errorBody is Map<String, dynamic>) {
      final code = errorBody['code'] as String? ?? 'INTERNAL_ERROR';
      final message = errorBody['message'] as String? ?? '일시적인 오류가 발생했습니다.';

      return switch (code) {
        'TOKEN_EXPIRED' => AuthFailure(message, expired: true),
        'UNAUTHORIZED' || 'INVALID_CREDENTIALS' => AuthFailure(message),
        'FACE_NOT_DETECTED' ||
        'FOOD_NOT_DETECTED' ||
        'AI_ANALYSIS_FAILED' ||
        'AI_TIMEOUT' =>
          AnalysisFailure(code, message),
        _ => ServerFailure(code, message),
      };
    }
  }
  return const UnknownFailure();
}
```

> **`validateStatus`를 손대고 싶은 유혹을 참아야 한다.** "4xx도 정상 응답으로 받아서 `error.message`를 편하게 읽자"는 발상은 그럴듯하지만, 그 순간 4xx가 `DioException`이 되지 않는다. 그러면 `UnauthorizedInterceptor.onError`가 영영 호출되지 않아 **토큰 만료 시 로그인 화면으로 넘어가지 않고**, `mapToFailure`의 `TOKEN_EXPIRED` · `FACE_NOT_DETECTED` · `EMAIL_ALREADY_EXISTS` 분기가 전부 도달 불가 코드가 된다. 기본값을 그대로 두면 `error.response?.data`로 본문을 읽을 수 있으므로 잃는 것도 없다.

---

## 2.7 shared/enums

**`lib/shared/enums/feedback_type.dart`**

```dart
enum FeedbackType {
  good,      // 좋은 점
  caution,   // 주의사항
  action;    // 추천 행동 — 화면에서 가장 강조되는 카드

  static FeedbackType fromJson(String value) => switch (value) {
        'GOOD' => FeedbackType.good,
        'CAUTION' => FeedbackType.caution,
        'ACTION' => FeedbackType.action,
        _ => FeedbackType.caution,
      };
}
```

**`lib/shared/enums/highlight_status.dart`**

```dart
/// 피부 결과 화면(S05)의 요약 뱃지 색상.
enum HighlightStatus {
  good,      // 초록
  warn,      // 노랑
  caution;   // 빨강

  static HighlightStatus fromJson(String value) => switch (value) {
        'GOOD' => HighlightStatus.good,
        'WARN' => HighlightStatus.warn,
        'CAUTION' => HighlightStatus.caution,
        // 모르는 값을 good으로 떨어뜨리면 사용자에게 잘못된 안심을 준다.
        // 피부 상태 문구라 더 민감하다. 중립인 warn으로 보낸다.
        _ => HighlightStatus.warn,
      };
}
```

**`lib/shared/enums/cooking_method.dart`**

```dart
enum CookingMethod {
  fried, boiled, grilled, raw, steamed, etc;

  static CookingMethod fromJson(String value) => switch (value) {
        'FRIED' => CookingMethod.fried,
        'BOILED' => CookingMethod.boiled,
        'GRILLED' => CookingMethod.grilled,
        'RAW' => CookingMethod.raw,
        'STEAMED' => CookingMethod.steamed,
        _ => CookingMethod.etc,
      };

  String get label => switch (this) {
        CookingMethod.fried => '튀김',
        CookingMethod.boiled => '국물',
        CookingMethod.grilled => '구이',
        CookingMethod.raw => '생식',
        CookingMethod.steamed => '찜',
        CookingMethod.etc => '기타',
      };
}
```

**`lib/shared/enums/ingredient_tag.dart`**

```dart
/// 서버 IngredientTag enum과 값이 일치해야 한다.
/// 서버가 새 태그를 추가해도 앱이 죽지 않도록 기본값을 etc로 둔다.
enum IngredientTag {
  vitaminC, vitaminA, omega3, antioxidant, probiotic,
  dairy, gluten, capsaicin, caffeine, alcohol, highGi, etc;

  static IngredientTag fromJson(String value) => switch (value) {
        'VITAMIN_C' => IngredientTag.vitaminC,
        'VITAMIN_A' => IngredientTag.vitaminA,
        'OMEGA3' => IngredientTag.omega3,
        'ANTIOXIDANT' => IngredientTag.antioxidant,
        'PROBIOTIC' => IngredientTag.probiotic,
        'DAIRY' => IngredientTag.dairy,
        'GLUTEN' => IngredientTag.gluten,
        'CAPSAICIN' => IngredientTag.capsaicin,
        'CAFFEINE' => IngredientTag.caffeine,
        'ALCOHOL' => IngredientTag.alcohol,
        'HIGH_GI' => IngredientTag.highGi,
        _ => IngredientTag.etc,
      };
}
```

**`lib/shared/enums/skin_type.dart`**

```dart
/// 서버 SkinType enum과 이름이 정확히 같아야 한다.
enum SkinType {
  dry('DRY', '건성'),
  oily('OILY', '지성'),
  combination('COMBINATION', '복합성'),
  sensitive('SENSITIVE', '민감성'),
  normal('NORMAL', '보통'),
  unknown('UNKNOWN', '잘 모르겠어요');

  const SkinType(this.wire, this.label);

  final String wire;
  final String label;

  /// S01c 선택 화면에 노출할 칩. normal 은 관찰 전용이라 뺀다.
  static const selectable = [dry, oily, combination, sensitive, unknown];

  /// null / 미지원 값은 null 로 흘려보낸다.
  /// "아직 안 정함"과 "잘 모르겠어요(unknown)"는 다르므로 기본값을 두지 않는다.
  static SkinType? fromJson(String? value) {
    if (value == null) return null;
    for (final type in SkinType.values) {
      if (type.wire == value) return type;
    }
    return null;
  }
}
```

> **다른 enum과 달리 기본값(`_ =>`)이 없다.** 여기서는 `null`이 의미 있는 값이기 때문이다. `null`이면 앱이 "평소 본인 피부는?" 선택 칩을 띄우고, 값이 있으면 갭 카드를 띄운다. 모르는 값을 `unknown`으로 뭉개면 건너뛴 사용자와 "잘 모르겠어요"를 고른 사용자가 섞인다.

**`lib/shared/enums/recommendation_type.dart`**

```dart
enum RecommendationType {
  recommend,
  avoid;

  static RecommendationType fromJson(String value) => switch (value) {
        'AVOID' => RecommendationType.avoid,
        _ => RecommendationType.recommend,
      };
}
```

> **모든 enum 파서에 기본값(`_ =>`)이 있다.** 서버가 값을 하나 추가했는데 앱이 파싱 예외로 죽으면, 그 시점에 배포된 앱은 전부 먹통이 된다. 알 수 없는 값은 흘려보내는 편이 항상 낫다.
>
> 다만 **기본값을 무엇으로 두는지가 중요하다.** `IngredientTag`는 `etc`가 중립이라 안전하지만, `HighlightStatus`를 `good`으로 떨어뜨리면 알 수 없는 경고가 초록 뱃지로 표시된다. 그래서 `warn`을 기본값으로 뒀다.
>
> `FeedbackType`과 `RecommendationType`은 **현재 앱에서 호출되지 않는다.** 서버가 `feedbacks{good,caution,action}`과 `recommend/avoid`로 이미 나눠서 내려주기 때문이다. Phase 2에서 히스토리 화면이 평면 배열을 받게 되면 그때 쓰인다.

---

## 2.8 features/auth

**`lib/features/auth/domain/entities/auth_user.dart`**

```dart
import '../../../../shared/enums/skin_type.dart';

class AuthUser {
  const AuthUser({
    required this.userId,
    required this.email,
    required this.nickname,
    this.declaredSkinType,
    this.isTestAccount = false,
    this.joinedAt,
  });

  final int userId;
  final String email;
  final String nickname;

  /// null = 아직 안 정함(건너뜀). SkinType.unknown 과 다르다.
  final SkinType? declaredSkinType;

  final bool isTestAccount;
  final DateTime? joinedAt;

  bool get needsSkinTypePrompt => declaredSkinType == null;
}

/// 로그인 결과 = 토큰 + 사용자
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.expiresIn,
    required this.user,
  });

  final String accessToken;
  final int expiresIn; // 초
  final AuthUser user;
}
```

**`lib/features/auth/data/models/auth_dtos.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../shared/enums/skin_type.dart';
import '../../domain/entities/auth_user.dart';

part 'auth_dtos.freezed.dart';
part 'auth_dtos.g.dart';

// ---------- Request ----------

@freezed
class SignupRequestDto with _$SignupRequestDto {
  const factory SignupRequestDto({
    required String email,
    required String password,
    required String nickname,
  }) = _SignupRequestDto;

  factory SignupRequestDto.fromJson(Map<String, dynamic> json) =>
      _$SignupRequestDtoFromJson(json);
}

@freezed
class LoginRequestDto with _$LoginRequestDto {
  const factory LoginRequestDto({
    required String email,
    required String password,
  }) = _LoginRequestDto;

  factory LoginRequestDto.fromJson(Map<String, dynamic> json) =>
      _$LoginRequestDtoFromJson(json);
}

@freezed
class TestLoginRequestDto with _$TestLoginRequestDto {
  /// slot 1 = 발표 시연 전용 / 2·3 = 팀 테스트
  const factory TestLoginRequestDto({@Default(1) int slot}) = _TestLoginRequestDto;

  factory TestLoginRequestDto.fromJson(Map<String, dynamic> json) =>
      _$TestLoginRequestDtoFromJson(json);
}

// ---------- Response ----------

@freezed
class UserSummaryDto with _$UserSummaryDto {
  const factory UserSummaryDto({
    required int userId,
    required String email,
    required String nickname,
  }) = _UserSummaryDto;

  factory UserSummaryDto.fromJson(Map<String, dynamic> json) =>
      _$UserSummaryDtoFromJson(json);
}

@freezed
class AuthResponseDto with _$AuthResponseDto {
  const factory AuthResponseDto({
    required String accessToken,
    required String tokenType,
    required int expiresIn,
    required UserSummaryDto user,
  }) = _AuthResponseDto;

  factory AuthResponseDto.fromJson(Map<String, dynamic> json) =>
      _$AuthResponseDtoFromJson(json);
}

@freezed
class MeResponseDto with _$MeResponseDto {
  const factory MeResponseDto({
    required int userId,
    required String email,
    required String nickname,
    String? declaredSkinType,          // 미선택이면 서버가 키를 생략한다
    @JsonKey(name: 'isTestAccount') @Default(false) bool isTestAccount,
    DateTime? joinedAt,
  }) = _MeResponseDto;

  factory MeResponseDto.fromJson(Map<String, dynamic> json) =>
      _$MeResponseDtoFromJson(json);
}

// ---------- DTO → Entity ----------

extension AuthResponseDtoX on AuthResponseDto {
  AuthSession toEntity() => AuthSession(
        accessToken: accessToken,
        expiresIn: expiresIn,
        user: AuthUser(
          userId: user.userId,
          email: user.email,
          nickname: user.nickname,
        ),
      );
}

extension MeResponseDtoX on MeResponseDto {
  AuthUser toEntity() => AuthUser(
        userId: userId,
        email: email,
        nickname: nickname,
        declaredSkinType: SkinType.fromJson(declaredSkinType),
        isTestAccount: isTestAccount,
        joinedAt: joinedAt,
      );
}
```

**`lib/features/auth/domain/repositories/auth_repository.dart`**

```dart
import '../../../../core/result/result.dart';
import '../../../../shared/enums/skin_type.dart';
import '../entities/auth_user.dart';

abstract interface class AuthRepository {
  Future<Result<AuthSession>> signup({
    required String email,
    required String password,
    required String nickname,
  });

  Future<Result<AuthSession>> login({
    required String email,
    required String password,
  });

  /// 로그인 화면의 "테스트 계정으로 시작하기" 버튼
  Future<Result<AuthSession>> loginWithTestAccount({int slot = 1});

  /// 앱 시작 시 저장된 토큰의 유효성을 확인하며 사용자 정보를 가져온다
  Future<Result<AuthUser>> getMe();

  /// 피부 타입을 선택·변경한다. (S01c 또는 S05 인라인 선택)
  ///
  /// 건너뛰기는 이 메서드를 호출하지 않는 것이다.
  /// SkinType.unknown 을 대신 보내면 "잘 모르겠다고 답한 사용자"와 구분이 사라진다.
  Future<Result<AuthUser>> updateSkinType(SkinType skinType);

  /// 서버는 상태를 갖지 않으므로 로컬 토큰 삭제가 곧 로그아웃이다
  Future<void> logout();
}
```

> **`logout()`만 `Result`를 반환하지 않는다.** 로컬 토큰을 지우는 일은 실패할 수 없고, 실패해도 사용자가 할 수 있는 게 없다. 실패할 수 없는 동작에 실패 분기를 만들면 호출부가 의미 없는 코드를 쓰게 된다.

---

## 2.9 features/skin_analysis

**`lib/features/skin_analysis/domain/entities/skin_analysis.dart`**

```dart
import '../../../../shared/enums/highlight_status.dart';
import '../../../../shared/enums/skin_type.dart';

class SkinAnalysis {
  const SkinAnalysis({
    required this.id,
    required this.skinScore,
    required this.metrics,
    required this.summary,
    required this.highlights,
    this.skinTypeGap,
    required this.analyzedAt,
  });

  final int id;
  final int skinScore;
  final SkinMetrics metrics;
  final String summary;
  final List<Highlight> highlights;

  /// null = 사용자가 아직 피부 타입을 안 골랐다.
  /// 이 경우 S05 는 갭 카드 대신 "평소 본인 피부는?" 선택 칩을 띄운다.
  final SkinTypeGap? skinTypeGap;

  final DateTime analyzedAt;
}

/// 자가 진단 ↔ 오늘 측정 비교. 서버가 계산해서 내려준다.
class SkinTypeGap {
  const SkinTypeGap({
    required this.declared,
    required this.observed,
    required this.matched,
    required this.message,
  });

  final SkinType declared;
  final SkinType observed;
  final bool matched;
  final String message;
}

class SkinMetrics {
  const SkinMetrics({
    required this.hydration,
    required this.oil,
    required this.redness,
    required this.trouble,
    required this.barrier,
  });

  final int hydration;
  final int oil;
  final int redness;
  final int trouble;
  final int barrier;

  /// 화면에서 5개 바를 그릴 때 쓰는 순서 고정 리스트.
  ///
  /// higherIsBetter가 반드시 필요하다 — hydration 30은 나쁘고 redness 30은 좋다.
  /// 이 정보 없이 게이지 색을 칠하면 홍조가 심할수록 초록으로 표시된다.
  List<({String key, String label, int value, bool higherIsBetter})> toBars() => [
        (key: 'hydration', label: '수분',   value: hydration, higherIsBetter: true),
        (key: 'oil',       label: '유분',   value: oil,       higherIsBetter: false),
        (key: 'redness',   label: '홍조',   value: redness,   higherIsBetter: false),
        (key: 'trouble',   label: '트러블', value: trouble,   higherIsBetter: false),
        (key: 'barrier',   label: '장벽',   value: barrier,   higherIsBetter: true),
      ];
}

class Highlight {
  const Highlight({required this.label, required this.status});

  final String label;
  final HighlightStatus status;
}
```

**`lib/features/skin_analysis/data/models/skin_dtos.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../shared/enums/highlight_status.dart';
import '../../../../shared/enums/skin_type.dart';
import '../../domain/entities/skin_analysis.dart';

part 'skin_dtos.freezed.dart';
part 'skin_dtos.g.dart';

@freezed
class SkinMetricsDto with _$SkinMetricsDto {
  const factory SkinMetricsDto({
    required int hydration,
    required int oil,
    required int redness,
    required int trouble,
    required int barrier,
  }) = _SkinMetricsDto;

  factory SkinMetricsDto.fromJson(Map<String, dynamic> json) =>
      _$SkinMetricsDtoFromJson(json);
}

@freezed
class HighlightDto with _$HighlightDto {
  const factory HighlightDto({
    required String label,
    required String status,     // GOOD / WARN / CAUTION
  }) = _HighlightDto;

  factory HighlightDto.fromJson(Map<String, dynamic> json) =>
      _$HighlightDtoFromJson(json);
}

@freezed
class SkinTypeGapDto with _$SkinTypeGapDto {
  const factory SkinTypeGapDto({
    required String declared,
    required String observed,
    @Default(false) bool matched,
    @Default('') String message,
  }) = _SkinTypeGapDto;

  factory SkinTypeGapDto.fromJson(Map<String, dynamic> json) =>
      _$SkinTypeGapDtoFromJson(json);
}

@freezed
class SkinAnalysisDto with _$SkinAnalysisDto {
  const factory SkinAnalysisDto({
    required int skinAnalysisId,
    required int skinScore,
    required SkinMetricsDto metrics,
    @Default('') String summary,
    @Default(<HighlightDto>[]) List<HighlightDto> highlights,
    SkinTypeGapDto? skinTypeGap,        // 미선택이면 서버가 키를 생략한다
    required DateTime analyzedAt,
  }) = _SkinAnalysisDto;

  factory SkinAnalysisDto.fromJson(Map<String, dynamic> json) =>
      _$SkinAnalysisDtoFromJson(json);
}

extension SkinAnalysisDtoX on SkinAnalysisDto {
  SkinAnalysis toEntity() => SkinAnalysis(
        id: skinAnalysisId,
        skinScore: skinScore,
        metrics: SkinMetrics(
          hydration: metrics.hydration,
          oil: metrics.oil,
          redness: metrics.redness,
          trouble: metrics.trouble,
          barrier: metrics.barrier,
        ),
        summary: summary,
        highlights: highlights
            .map((h) => Highlight(
                  label: h.label,
                  status: HighlightStatus.fromJson(h.status),
                ))
            .toList(),
        skinTypeGap: skinTypeGap?.toEntity(),
        analyzedAt: analyzedAt,
      );
}

extension SkinTypeGapDtoX on SkinTypeGapDto {
  /// declared/observed 는 서버가 보낸 값이므로 null 이 될 수 없다.
  /// 그래도 알 수 없는 값이 오면 unknown 으로 흘려보내 앱이 죽지 않게 한다.
  SkinTypeGap toEntity() => SkinTypeGap(
        declared: SkinType.fromJson(declared) ?? SkinType.unknown,
        observed: SkinType.fromJson(observed) ?? SkinType.unknown,
        matched: matched,
        message: message,
      );
}
```

**`lib/features/skin_analysis/domain/repositories/skin_repository.dart`**

```dart
import 'dart:io';

import '../../../../core/result/result.dart';
import '../entities/skin_analysis.dart';

abstract interface class SkinRepository {
  /// 정면·왼쪽·오른쪽 세 장을 한 번에 올리고 분석 결과 하나를 받는다. (multipart)
  ///
  /// 파트 이름이 곧 방향이다 — `front` · `left` · `right`. 한 장이라도 빠지면
  /// 서버가 400 을 돌려주므로, 세 장이 다 모이기 전에는 호출하지 않는다.
  /// 촬영 단계마다 부르면 분석이 세 건 생기고 그중 무엇이 오늘의 점수인지 알 수 없다.
  Future<Result<SkinAnalysis>> analyze({
    required File front,
    required File left,
    required File right,
  });

  /// 홈 화면(S02)의 "오늘의 Skin Score" 카드용. 없으면 Success(null).
  Future<Result<SkinAnalysis?>> getLatest();

  Future<Result<SkinAnalysis>> getById(int id);
}
```

---

## 2.10 features/skin_plate

**`lib/features/skin_plate/domain/entities/skin_plate.dart`**

```dart
import '../../../../shared/enums/cooking_method.dart';
import '../../../../shared/enums/ingredient_tag.dart';
import '../../../../shared/enums/plate_action_code.dart';

class SkinPlate {
  const SkinPlate({
    required this.id,
    required this.plateScore,
    this.baseScore = 70,
    required this.summary,
    required this.food,
    required this.good,
    required this.caution,
    required this.actions,
    required this.appliedRules,
    required this.createdAt,
  });

  final int id;
  final int plateScore;

  /// 계산 내역 카드의 첫 줄("기본 70"). 앱이 하드코딩하면 클램프된 점수에서 역산이 틀린다.
  final int baseScore;

  final String summary;
  final FoodAnalysis food;

  final List<PlateFeedback> good;
  final List<PlateFeedback> caution;

  /// 화면에서 가장 강조되는 카드. 이 제품의 존재 이유다.
  final List<PlateAction> actions;

  final List<String> appliedRules;
  final DateTime createdAt;

  /// 여기에 potentialScore(= plateScore + expectedGain 합산) 같은 게터를 만들지 마라.
  /// 합산은 실제 재계산과 일치하지 않는다.
  ///
  ///   예시 A 합산: 60 + 8 + 6 = 74   ← 어디에도 없는 숫자
  ///   실제 재계산: 국물만 절반 → 68  (나트륨 925mg이 되어 R04가 아예 미발동)
  ///                둘 다 실행  → 80
  ///
  /// "실행하면 몇 점"은 POST /plates/{id}/simulate 로 서버에 물어본다.
}

class PlateFeedback {
  const PlateFeedback({
    required this.message,
    required this.scoreDelta,
    required this.ruleCode,
  });

  final String message;
  final int scoreDelta;
  final String? ruleCode;
}

class PlateAction {
  const PlateAction({
    required this.message,
    required this.expectedGain,
    required this.ruleCode,
  });

  final String message;
  final int expectedGain;
  final String? ruleCode;
}

class FoodAnalysis {
  const FoodAnalysis({
    required this.id,
    required this.foodName,
    required this.foodCategory,
    required this.cookingMethod,
    required this.spicy,
    required this.ingredients,
    required this.nutrition,
  });

  final int id;
  final String foodName;
  final String? foodCategory;
  final CookingMethod cookingMethod;
  final bool spicy;
  final List<Ingredient> ingredients;
  final Nutrition nutrition;
}

class Ingredient {
  const Ingredient({required this.name, required this.tag});

  final String name;
  final IngredientTag tag;
}

class Nutrition {
  const Nutrition({
    required this.caloriesKcal,
    required this.proteinG,
    required this.fatG,
    required this.carbG,
    required this.sodiumMg,
    required this.sugarG,
  });

  final int caloriesKcal;
  final double proteinG;
  final double fatG;
  final double carbG;
  final int sodiumMg;
  final double sugarG;
}

/// 추천 행동 실행 시뮬레이션 결과. POST /plates/{id}/simulate 응답.
class PlateSimulation {
  const PlateSimulation({
    required this.plateId,
    required this.beforeScore,
    required this.afterScore,
    required this.appliedActions,
    required this.removedRules,
    required this.summary,
  });

  final int plateId;
  final int beforeScore;
  final int afterScore;
  final List<PlateActionCode> appliedActions;
  final List<String> removedRules;
  final String summary;

  int get gain => afterScore - beforeScore;
}
```

**`lib/shared/enums/plate_action_code.dart`**

```dart
/// 서버 PlateActionCode enum과 이름이 정확히 같아야 한다.
/// 앱이 이 이름을 그대로 보내므로 한쪽만 바꾸면 400이 난다.
enum PlateActionCode {
  halveSoup('HALVE_SOUP', '국물을 절반만 남기기'),
  lessSpicy('LESS_SPICY', '매운 양념 덜어내기'),
  noSugarDrink('NO_SUGAR_DRINK', '단 음료 대신 물'),
  removeBatter('REMOVE_BATTER', '튀김옷 일부 제거');

  const PlateActionCode(this.wire, this.label);

  /// 서버로 보내는 값
  final String wire;

  /// 버튼에 표시할 문구
  final String label;

  static PlateActionCode? fromJson(String value) {
    for (final code in PlateActionCode.values) {
      if (code.wire == value) return code;
    }
    return null;   // 서버가 새 액션을 추가해도 앱이 죽지 않는다
  }
}
```

> **`ruleCode`로 버튼을 붙인다.** `PlateAction.ruleCode`가 `R04`면 `HALVE_SOUP` 버튼, `R02`면 `LESS_SPICY` 버튼을 그 카드에 단다. 매핑은 서버 `PlateActionCode.relatedRuleCode()`와 같은 표다.

**`lib/features/skin_plate/data/models/plate_dtos.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../shared/enums/cooking_method.dart';
import '../../../../shared/enums/ingredient_tag.dart';
import '../../../../shared/enums/plate_action_code.dart';
import '../../domain/entities/skin_plate.dart' as domain;

part 'plate_dtos.freezed.dart';
part 'plate_dtos.g.dart';

@freezed
class NutritionDto with _$NutritionDto {
  const factory NutritionDto({
    @Default(0) int caloriesKcal,
    @Default(0) num proteinG,
    @Default(0) num fatG,
    @Default(0) num carbG,
    @Default(0) int sodiumMg,
    @Default(0) num sugarG,
  }) = _NutritionDto;

  factory NutritionDto.fromJson(Map<String, dynamic> json) =>
      _$NutritionDtoFromJson(json);
}

@freezed
class IngredientDto with _$IngredientDto {
  const factory IngredientDto({
    required String name,
    @Default('ETC') String tag,
  }) = _IngredientDto;

  factory IngredientDto.fromJson(Map<String, dynamic> json) =>
      _$IngredientDtoFromJson(json);
}

@freezed
class FoodAnalysisDto with _$FoodAnalysisDto {
  const factory FoodAnalysisDto({
    required int foodAnalysisId,
    required String foodName,
    String? foodCategory,
    @Default('ETC') String cookingMethod,
    @Default(false) bool spicy,
    @Default(<IngredientDto>[]) List<IngredientDto> ingredients,
    required NutritionDto nutrition,
  }) = _FoodAnalysisDto;

  factory FoodAnalysisDto.fromJson(Map<String, dynamic> json) =>
      _$FoodAnalysisDtoFromJson(json);
}

@freezed
class FeedbackDto with _$FeedbackDto {
  const factory FeedbackDto({
    required String message,
    @Default(0) int scoreDelta,
    String? ruleCode,
  }) = _FeedbackDto;

  factory FeedbackDto.fromJson(Map<String, dynamic> json) =>
      _$FeedbackDtoFromJson(json);
}

@freezed
class ActionDto with _$ActionDto {
  const factory ActionDto({
    required String message,
    @Default(0) int expectedGain,
    String? ruleCode,
  }) = _ActionDto;

  factory ActionDto.fromJson(Map<String, dynamic> json) =>
      _$ActionDtoFromJson(json);
}

/// 서버가 good / caution / action 3개 배열로 나눠서 내려주므로
/// 앱은 타입 필터링 없이 3개 섹션에 바로 렌더링하면 된다.
@freezed
class FeedbackGroupDto with _$FeedbackGroupDto {
  const factory FeedbackGroupDto({
    @Default(<FeedbackDto>[]) List<FeedbackDto> good,
    @Default(<FeedbackDto>[]) List<FeedbackDto> caution,
    @Default(<ActionDto>[]) List<ActionDto> action,
  }) = _FeedbackGroupDto;

  factory FeedbackGroupDto.fromJson(Map<String, dynamic> json) =>
      _$FeedbackGroupDtoFromJson(json);
}

@freezed
class SkinPlateDto with _$SkinPlateDto {
  const factory SkinPlateDto({
    required int plateId,
    required int plateScore,
    @Default(70) int baseScore,
    @Default('') String summary,
    required FoodAnalysisDto food,
    required FeedbackGroupDto feedbacks,
    @Default(<String>[]) List<String> appliedRules,
    required DateTime createdAt,
  }) = _SkinPlateDto;

  factory SkinPlateDto.fromJson(Map<String, dynamic> json) =>
      _$SkinPlateDtoFromJson(json);
}

// ---------- DTO → Entity ----------

extension SkinPlateDtoX on SkinPlateDto {
  domain.SkinPlate toEntity() => domain.SkinPlate(
        id: plateId,
        plateScore: plateScore,
        baseScore: baseScore,
        summary: summary,
        food: food.toEntity(),
        good: feedbacks.good.map((f) => f.toEntity()).toList(),
        caution: feedbacks.caution.map((f) => f.toEntity()).toList(),
        actions: feedbacks.action.map((a) => a.toEntity()).toList(),
        appliedRules: appliedRules,
        createdAt: createdAt,
      );
}

extension FeedbackDtoX on FeedbackDto {
  domain.PlateFeedback toEntity() => domain.PlateFeedback(
        message: message,
        scoreDelta: scoreDelta,
        ruleCode: ruleCode,
      );
}

extension ActionDtoX on ActionDto {
  domain.PlateAction toEntity() => domain.PlateAction(
        message: message,
        expectedGain: expectedGain,
        ruleCode: ruleCode,
      );
}

extension FoodAnalysisDtoX on FoodAnalysisDto {
  domain.FoodAnalysis toEntity() => domain.FoodAnalysis(
        id: foodAnalysisId,
        foodName: foodName,
        foodCategory: foodCategory,
        cookingMethod: CookingMethod.fromJson(cookingMethod),
        spicy: spicy,
        ingredients: ingredients
            .map((i) => domain.Ingredient(
                  name: i.name,
                  tag: IngredientTag.fromJson(i.tag),
                ))
            .toList(),
        nutrition: domain.Nutrition(
          caloriesKcal: nutrition.caloriesKcal,
          proteinG: nutrition.proteinG.toDouble(),
          fatG: nutrition.fatG.toDouble(),
          carbG: nutrition.carbG.toDouble(),
          sodiumMg: nutrition.sodiumMg,
          sugarG: nutrition.sugarG.toDouble(),
        ),
      );
}
```

> **영양 수치를 DTO에서 `num`으로 받는 이유** — 서버가 `28.5`를 보내면 Dart는 `double`로, `28`을 보내면 `int`로 파싱한다. `double`로 선언해 두면 후자에서 `type 'int' is not a subtype of type 'double'` 예외가 난다. 김치찌개는 되는데 삶은 달걀은 앱이 죽는 식이다. `num`으로 받고 `.toDouble()`로 변환하면 끝난다.

**`lib/features/skin_plate/domain/repositories/plate_repository.dart`**

```dart
import 'dart:io';

import '../../../../core/result/result.dart';
import '../../../../shared/enums/plate_action_code.dart';
import '../entities/skin_plate.dart';

abstract interface class PlateRepository {
  /// 음식 사진을 업로드하고 Skin Plate Score를 받는다.
  /// [skinAnalysisId]를 생략하면 서버가 최신 피부 분석을 자동으로 사용한다.
  Future<Result<SkinPlate>> create(File image, {int? skinAnalysisId});

  Future<Result<SkinPlate>> getById(int id);

  /// 추천 행동을 실행했다고 가정하고 점수를 다시 계산한다. 서버에 저장되지 않는다.
  Future<Result<PlateSimulation>> simulate(int plateId, List<PlateActionCode> actions);
}
```

---

### 2.10.1 시뮬레이션 DTO

```dart
@freezed
class PlateSimulationDto with _$PlateSimulationDto {
  const factory PlateSimulationDto({
    required int plateId,
    required int beforeScore,
    required int afterScore,
    @Default(<String>[]) List<String> appliedActions,
    @Default(<String>[]) List<String> removedRules,
    @Default('') String summary,
  }) = _PlateSimulationDto;

  factory PlateSimulationDto.fromJson(Map<String, dynamic> json) =>
      _$PlateSimulationDtoFromJson(json);
}

extension PlateSimulationDtoX on PlateSimulationDto {
  domain.PlateSimulation toEntity() => domain.PlateSimulation(
        plateId: plateId,
        beforeScore: beforeScore,
        afterScore: afterScore,
        appliedActions: appliedActions
            .map(PlateActionCode.fromJson)
            .whereType<PlateActionCode>()   // 모르는 액션은 조용히 버린다
            .toList(),
        removedRules: removedRules,
        summary: summary,
      );
}
```

---

## 2.11 features/recommendation

**`lib/features/recommendation/domain/entities/recommendation.dart`**

```dart
class DailyRecommendation {
  const DailyRecommendation({
    required this.skinAnalysisId,
    required this.recommend,
    required this.avoid,
    this.generatedAt,
  });

  final int skinAnalysisId;
  final List<RecommendedFood> recommend;
  final List<RecommendedFood> avoid;
  final DateTime? generatedAt;

  bool get isEmpty => recommend.isEmpty && avoid.isEmpty;
}

class RecommendedFood {
  const RecommendedFood({required this.foodName, required this.reason});

  final String foodName;
  final String reason;
}
```

**`lib/features/recommendation/data/models/recommendation_dtos.dart`**

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../domain/entities/recommendation.dart';

part 'recommendation_dtos.freezed.dart';
part 'recommendation_dtos.g.dart';

@freezed
class RecommendedFoodDto with _$RecommendedFoodDto {
  const factory RecommendedFoodDto({
    required String foodName,
    @Default('') String reason,
  }) = _RecommendedFoodDto;

  factory RecommendedFoodDto.fromJson(Map<String, dynamic> json) =>
      _$RecommendedFoodDtoFromJson(json);
}

@freezed
class RecommendationDto with _$RecommendationDto {
  const factory RecommendationDto({
    required int skinAnalysisId,
    @Default(<RecommendedFoodDto>[]) List<RecommendedFoodDto> recommend,
    @Default(<RecommendedFoodDto>[]) List<RecommendedFoodDto> avoid,
    DateTime? generatedAt,
  }) = _RecommendationDto;

  factory RecommendationDto.fromJson(Map<String, dynamic> json) =>
      _$RecommendationDtoFromJson(json);
}

extension RecommendationDtoX on RecommendationDto {
  DailyRecommendation toEntity() => DailyRecommendation(
        skinAnalysisId: skinAnalysisId,
        recommend: recommend.map((r) => r.toEntity()).toList(),
        avoid: avoid.map((r) => r.toEntity()).toList(),
        generatedAt: generatedAt,
      );
}

extension RecommendedFoodDtoX on RecommendedFoodDto {
  RecommendedFood toEntity() =>
      RecommendedFood(foodName: foodName, reason: reason);
}
```

**`lib/features/recommendation/domain/repositories/recommendation_repository.dart`**

```dart
import '../../../../core/result/result.dart';
import '../entities/recommendation.dart';

abstract interface class RecommendationRepository {
  Future<Result<DailyRecommendation>> getBySkinAnalysis(int skinAnalysisId);
}
```

---

## 2.12 온디바이스 얼굴 게이트 (ML Kit)

PRD §9.5의 게이트를 구현한다. **판정이 아니라 게이트와 크롭 전용**이다.

### 2.12.1 왜 파일만 갈라서는 안 되는가

웹에는 ML Kit이 없다. 그런데 **`kIsWeb`으로는 막을 수 없다.**

```dart
if (kIsWeb) return _pickFromFiles();   // ← 이걸로는 웹 빌드가 안 살아난다
```

`kIsWeb`은 **런타임 분기**이고 `import`는 **컴파일 타임**이다. 웹 번들을 만들 때 컴파일러는 도달 가능한 모든 import를 따라간다. 그래서 게이트 구현 파일이 `google_mlkit_face_detection`을 import하고, 그 파일을 웹에서도 닿는 코드가 import하면 — 분기를 아무리 걸어도 빌드가 깨진다.

**파일을 둘로 나누는 것만으로도 부족하다.** 게이트의 시그니처가 ML Kit 타입에 오염돼 있으면 호출부가 그 타입을 만들어 넘겨야 하고, 그러면 **호출부가 ML Kit을 import하게 된다.**

```dart
Future<FaceGateResult> check(InputImage image, ...)   // InputImage = google_mlkit_commons
```

`InputImage`는 `dart:io`의 `File`을 참조한다. 웹 빌드는 컴파일 단계에서 죽고, 그 시점에 `kIsWeb`은 이미 늦었다.

**경계를 파일이 아니라 타입 수준까지 밀어야 한다.**

### 2.12.2 조건부 import 팩토리 구조 — 파일 4개

| 파일 | 역할 | ML Kit |
|---|---|---|
| `domain/entities/face_gate_result.dart` | 결과 타입. 양쪽이 공유 | ❌ 순수 Dart |
| `data/datasources/face_gate.dart` | 공용 인터페이스 + 조건부 import | ❌ 모른다 |
| `data/datasources/face_gate_stub.dart` | 웹 기본값 | ❌ 없음 |
| `data/datasources/face_gate_mlkit.dart` | 모바일 구현 | ✅ **여기서만** |

호출부는 `createFaceGate()` 하나만 부르고 ML Kit 타입을 한 번도 만나지 않는다.

**`lib/features/skin_analysis/domain/entities/face_gate_result.dart`**

```dart
import 'dart:ui' show Rect;   // dart:ui 는 웹에도 있다. 플랫폼 중립이다.

/// 촬영 버튼을 켤지 말지, 못 켠다면 뭐라고 안내할지.
sealed class FaceGateResult {
  const FaceGateResult();
}

class FaceGateOk extends FaceGateResult {
  const FaceGateOk(this.faceRect);
  /// 크롭에 쓸 얼굴 영역 (여백 20% 포함)
  final Rect faceRect;
}

class FaceGateBlocked extends FaceGateResult {
  const FaceGateBlocked(this.reason, this.guide);
  final FaceGateReason reason;
  /// 화면에 그대로 띄우는 안내 문구
  final String guide;
}

/// 웹처럼 게이트를 쓸 수 없는 환경. 촬영 버튼은 항상 열려 있다.
class FaceGateUnavailable extends FaceGateResult {
  const FaceGateUnavailable();
}

enum FaceGateReason { noFace, multipleFaces, tooSmall, notFrontal, tooDark }
```

**`lib/features/skin_analysis/data/datasources/face_gate.dart`**

```dart
import 'package:camera/camera.dart' show CameraImage;

import '../../domain/entities/face_gate_result.dart';

// dart.library.io 가 있으면 모바일 구현, 없으면(=웹) 스텁을 가져온다.
// 이 한 줄이 웹 번들에서 ML Kit 을 완전히 걷어낸다.
import 'face_gate_stub.dart'
    if (dart.library.io) 'face_gate_mlkit.dart';

/// 공용 인터페이스. ML Kit 타입이 하나도 등장하지 않는다.
/// CameraImage 는 camera_platform_interface 타입이라 웹에서도 import 된다.
abstract interface class FaceGate {
  Future<FaceGateResult> check(CameraImage frame, int frameHeight);
  void dispose();
}

/// 호출부가 쓰는 유일한 진입점. 두 구현 파일이 같은 이름으로 제공한다.
FaceGate faceGate() => createFaceGate();
```

**`lib/features/skin_analysis/data/datasources/face_gate_stub.dart`** — 웹

```dart
import 'package:camera/camera.dart' show CameraImage;

import '../../domain/entities/face_gate_result.dart';
import 'face_gate.dart';

/// 웹에는 ML Kit 이 없다. 게이트를 걸지 않고 그대로 통과시킨다.
/// 얼굴이 아닌 사진이 올라와도 서버의 faceDetected:false 폴백이 받아 준다(PRD §6.1).
FaceGate createFaceGate() => _NoGate();

class _NoGate implements FaceGate {
  @override
  Future<FaceGateResult> check(CameraImage frame, int frameHeight) async =>
      const FaceGateUnavailable();

  @override
  void dispose() {}
}
```

**`lib/features/skin_analysis/data/datasources/face_gate_mlkit.dart`** — Android/iOS

```dart
import 'package:camera/camera.dart' show CameraImage;
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';

import '../../domain/entities/face_gate_result.dart';
import 'face_gate.dart';

FaceGate createFaceGate() => MlKitFaceGate();

class MlKitFaceGate implements FaceGate {
  MlKitFaceGate()
      : _detector = FaceDetector(
          options: FaceDetectorOptions(
            performanceMode: FaceDetectorMode.fast,   // 실시간 프리뷰용
            enableLandmarks: false,                   // 게이트에는 불필요
            enableClassification: false,              // 웃음·눈뜸 확률 안 쓴다
          ),
        );

  final FaceDetector _detector;

  // ---- 게이트 기준 (PRD §9.5) ----
  static const double _minFaceHeightRatio = 0.40;  // 프레임 높이의 40% 이상
  static const double _maxHeadAngleDeg    = 15.0;  // 정면 허용 범위
  static const int    _minLuminance       = 60;    // 0~255

  /// InputImage 생성이 이 파일 안에서 끝난다 — 호출부는 CameraImage 만 넘긴다.
  @override
  Future<FaceGateResult> check(CameraImage frame, int frameHeight) async {
    final faces = await _detector.processImage(_toInputImage(frame));

    if (faces.isEmpty) {
      return const FaceGateBlocked(
          FaceGateReason.noFace, '얼굴이 화면 안에 들어오게 해주세요');
    }
    if (faces.length > 1) {
      return const FaceGateBlocked(
          FaceGateReason.multipleFaces, '얼굴이 한 명만 보이게 해주세요');
    }

    final face = faces.first;

    if (face.boundingBox.height / frameHeight < _minFaceHeightRatio) {
      return const FaceGateBlocked(
          FaceGateReason.tooSmall, '조금 더 가까이 와주세요');
    }

    final yaw  = (face.headEulerAngleY ?? 0).abs();
    final roll = (face.headEulerAngleZ ?? 0).abs();
    if (yaw > _maxHeadAngleDeg || roll > _maxHeadAngleDeg) {
      return const FaceGateBlocked(
          FaceGateReason.notFrontal, '정면을 봐주세요');
    }

    // ML Kit 은 밝기를 주지 않는다. 검출된 얼굴 영역으로 직접 계산한다.
    if (_faceLuminance(frame, face.boundingBox) < _minLuminance) {
      return const FaceGateBlocked(
          FaceGateReason.tooDark, '조금 더 밝은 곳에서 촬영해주세요');
    }

    return FaceGateOk(_withMargin(face.boundingBox, 0.2));
  }

  @override
  void dispose() => _detector.close();
}
```

**`_toInputImage` — 이 함수가 게이트에서 가장 잘 깨지는 곳이다** (같은 파일 안)

`InputImage.fromBytes` 는 **플랫폼마다 다른 포맷**을 요구한다. 그리고 어긋나도 예외가 안 난다 — 검출이 그냥 0개로 나와서 "얼굴이 화면 안에 들어오게 해주세요"만 계속 뜬다. 카메라는 멀쩡히 사람 얼굴을 비추고 있는데. 원인을 게이트 조건에서 찾게 되는 종류의 실패다.

| 플랫폼 | `ImageFormatGroup` | `planes[0]` |
|---|---|---|
| Android | **`nv21`** | 휘도(Y) — 밝기 계산에 그대로 쓴다 |
| iOS | **`bgra8888`** | BGRA 인터리브 — 밝기는 변환해서 구해야 한다 |

```dart
import 'dart:io' show Platform;
import 'dart:ui' show Size;

/// CameraController 는 반드시 아래 포맷으로 만든다. 양쪽을 yuv420 으로 통일하면
/// 밝기 계산은 편해지지만 iOS 에서 ML Kit 이 프레임을 못 읽는다.
///
///   CameraController(camera, ResolutionPreset.high,
///       imageFormatGroup: Platform.isAndroid
///           ? ImageFormatGroup.nv21 : ImageFormatGroup.bgra8888)
InputImage? _toInputImage(CameraImage frame, CameraDescription camera) {
  final format = InputImageFormatValue.fromRawValue(frame.format.raw);
  if (format == null) return null;

  // 센서 방향을 안 넘기면 세로로 든 폰에서 얼굴이 90도 누운 채로 들어가고,
  // ML Kit 은 누운 얼굴을 잘 못 찾는다. 게이트가 상시 막히는 원인 1순위다.
  final rotation =
      InputImageRotationValue.fromRawValue(camera.sensorOrientation);
  if (rotation == null) return null;

  final plane = frame.planes.first;

  return InputImage.fromBytes(
    bytes: plane.bytes,
    metadata: InputImageMetadata(
      size: Size(frame.width.toDouble(), frame.height.toDouble()),
      rotation: rotation,
      format: format,
      bytesPerRow: plane.bytesPerRow,
    ),
  );
}

/// 검출된 얼굴에 여백을 붙인다. 딱 맞게 자르면 이마와 턱이 잘리는데,
/// 그 두 곳이 유분·트러블 판정에서 정보량이 가장 많은 영역이다.
Rect _withMargin(Rect face, double ratio) {
  final dx = face.width * ratio;
  final dy = face.height * ratio;
  return Rect.fromLTRB(
      face.left - dx, face.top - dy, face.right + dx, face.bottom + dy);
}
```

> **`check()` 는 `_toInputImage` 가 `null` 이면 `FaceGateUnavailable` 을 돌려준다.** 포맷을 못 읽는 기기에서 사용자를 가두지 않는다 — 게이트는 열어 주고 서버 폴백에 맡긴다.
>
> **완료 조건에 실기기 확인을 넣는다.** 이 함수는 에뮬레이터에서 통과해도 실기기에서 깨질 수 있다. Android 1대 · iOS 1대에서 **얼굴을 비췄을 때 게이트가 실제로 열리는지**를 커밋 조건으로 둔다.

**밝기 계산 — 플랫폼마다 planes[0] 의 의미가 다르다** (같은 파일 안)

```dart
/// Android(nv21) 는 planes[0] 이 곧 휘도(Y)라 그대로 읽으면 된다.
/// iOS(bgra8888) 는 인터리브라 픽셀마다 4바이트를 건너뛰며 휘도를 만든다.
///
/// image 패키지로 img.Image 를 만들어 평균을 내면 매 프레임 전체 변환이 들어간다.
/// 1080p 면 프레임당 200만 픽셀을 Dart 에서 도는 셈이라 프리뷰 FPS 가 눈에 띄게 떨어진다.
/// 8픽셀 간격 샘플링이면 계산량이 1/64 이고, 조도 판정에는 차고 넘친다.
int _faceLuminance(CameraImage frame, Rect face, {int step = 8}) {
  final plane = frame.planes.first;
  final isBgra = frame.format.group == ImageFormatGroup.bgra8888;
  var sum = 0, count = 0;

  final top    = face.top.toInt().clamp(0, frame.height - 1);
  final bottom = face.bottom.toInt().clamp(0, frame.height);
  final left   = face.left.toInt().clamp(0, frame.width - 1);
  final right  = face.right.toInt().clamp(0, frame.width);

  for (var row = top; row < bottom; row += step) {
    final rowStart = row * plane.bytesPerRow;
    for (var col = left; col < right; col += step) {
      if (isBgra) {
        final i = rowStart + col * 4;                       // B G R A
        // Rec.601 근사. 정확한 계수보다 임계값 60 과의 일관성이 중요하다.
        sum += (plane.bytes[i + 2] * 77 +
                plane.bytes[i + 1] * 150 +
                plane.bytes[i]     * 29) >> 8;
      } else {
        sum += plane.bytes[rowStart + col];                 // Y 평면
      }
      count++;
    }
  }
  return count == 0 ? 0 : sum ~/ count;
}
```

> **양 플랫폼을 `yuv420` 으로 통일하지 않는다.** 밝기 계산은 편해지지만 iOS 에서 ML Kit 이 프레임을 못 읽어 검출이 0개가 된다. **밝기 쪽을 분기하는 게 맞다** — 게이트가 아예 안 열리는 것보다 낫다.
>
> `image` 패키지는 **업로드 직전 크롭 한 번**에만 쓴다. 실시간 경로에는 넣지 않는다.

**업로드 직전 크롭 — 프리뷰 좌표를 사진에 그대로 쓰면 안 된다**

`check()` 가 돌려주는 `faceRect` 는 **프리뷰 스트림**(예: 1280×720) 좌표다. 그런데 실제로 업로드하는 건 `takePicture()` 가 만든 **원본 사진**(예: 4032×3024)이고, 해상도도 방향도 다르다. 프리뷰 좌표를 그대로 넘기면 사진 왼쪽 위 귀퉁이의 엉뚱한 영역이 잘려 나가고, **그 조각이 OpenAI 로 간다.** 게이트는 초록불이었는데 결과만 이상해지는, 원인 찾기 가장 어려운 형태의 버그다.

**배율을 계산해 맞추지 않는다.** 종횡비가 다르면 레터박스를 고려해야 하고, EXIF 방향까지 겹치면 경우의 수가 늘어난다. **사진에서 한 번 더 검출하는 쪽이 짧고 정확하다.** 촬영 1회당 50~100ms 이고 실시간 경로가 아니다.

```dart
/// faceRect 는 프리뷰 오버레이를 그리는 데만 쓴다. 크롭 좌표로는 쓰지 않는다.
Future<File> prepareSkinPhoto(File original) async {
  final detector = FaceDetector(
      options: FaceDetectorOptions(
        performanceMode: FaceDetectorMode.accurate,   // 정지 이미지 1장이라 여유가 있다
      ));

  try {
    // fromFilePath 는 EXIF 방향까지 알아서 처리한다.
    final faces = await detector.processImage(InputImage.fromFilePath(original.path));
    final decoded = img.decodeImage(await original.readAsBytes())!;

    // 사진에서 얼굴을 못 찾으면 크롭을 포기하고 원본을 올린다.
    // 게이트를 통과한 사용자를 여기서 막으면 촬영을 처음부터 다시 시키게 된다.
    final source = faces.isEmpty
        ? decoded
        : _crop(decoded, _withMargin(faces.first.boundingBox, 0.2));

    // 얼굴만 1024px → OpenAI detail:"high" 에서 실효 해상도가 3배 이상 올라간다
    final resized = img.copyResize(source, width: 1024);

    final path = '${original.parent.path}/skin_${original.uri.pathSegments.last}';
    return File(path)..writeAsBytesSync(img.encodeJpg(resized, quality: 80));
  } finally {
    detector.close();   // 안 닫으면 촬영할 때마다 네이티브 검출기가 쌓인다
  }
}

/// 여백을 붙인 사각형이 사진 밖으로 나갈 수 있다. 그대로 넘기면 copyCrop 이 던진다.
img.Image _crop(img.Image photo, Rect rect) {
  final left   = rect.left.toInt().clamp(0, photo.width - 1);
  final top    = rect.top.toInt().clamp(0, photo.height - 1);
  final right  = rect.right.toInt().clamp(left + 1, photo.width);
  final bottom = rect.bottom.toInt().clamp(top + 1, photo.height);

  return img.copyCrop(photo,
      x: left, y: top, width: right - left, height: bottom - top);
}
```

### 2.12.3 두 구현은 같은 API 를 노출해야 한다

`createFaceGate()` 의 이름·인자·반환 타입이 두 파일에서 **하나라도 다르면 한쪽 플랫폼에서만 컴파일이 깨진다.** 그리고 그건 그 플랫폼을 빌드해 봐야 안다 — 모바일만 돌려보고 있으면 웹이 깨진 걸 며칠 뒤에 발견한다.

`FaceGate` 인터페이스를 양쪽이 `implements` 하게 둔 것이 그 방어다. 시그니처가 어긋나면 해당 파일 자체가 컴파일되지 않는다.

### 2.12.4 게이트에는 반드시 탈출구가 있어야 한다

게이트는 실패를 줄이려고 넣은 것이다. 탈출구가 없으면 **실패를 새로 만든다.**

| 경로 | 게이트 |
|---|---|
| 카메라 촬영 (모바일) | 적용. 4개 조건을 모두 통과해야 버튼 활성화 |
| **갤러리 업로드** | **우회한다.** 얼굴 검출은 하되 크롭에만 쓰고, 실패해도 원본을 그대로 올린다 |
| 게이트 3회 연속 실패 | **"그래도 촬영" 버튼 노출.** 크롭 없이 전체 프레임 업로드 |
| **웹** | 스텁이 `FaceGateUnavailable` 을 돌려주므로 항상 열려 있다 |

```dart
if (_consecutiveFailures >= 3) {
  // 조명이 나쁜 환경에서 사용자를 가두지 않는다.
  // 서버의 faceDetected:false 폴백이 여전히 남아 있으므로 최악의 경우도 안전하다.
  showForceCaptureButton();
}
```

> 발표장 조명은 대개 천장에서 내리쬐어 눈 아래와 코 밑에 그림자를 만든다. 휘도 기준(60)에 걸리면 촬영 버튼이 안 눌린다. **영상 촬영은 재촬영하면 되지만 현장 시연은 그럴 수 없다.**

> **쓰지 말아야 할 곳** — `enableClassification: false`로 둔 것은 의도적이다. ML Kit이 주는 `smilingProbability`·`leftEyeOpenProbability`로 트러블이나 홍조를 추정하려 들면, "AI는 인식, 로직은 Backend"라는 구조가 무너지고 근거 없는 숫자가 하나 더 생긴다. **게이트와 크롭까지가 전부다.**
>
> **작업 시점은 Day 5~6.** 네이티브 의존성이 추가되는 작업이라 빌드가 깨지면 복구에 시간이 든다. Day 8 이후에는 붙이지 마라.

> **웹 빌드는 게이트를 붙이기 전에도, 붙인 후에도 통과해야 한다.**
> `flutter build web` 을 `FaceGate` 커밋의 완료 조건에 포함한다.
> 게이트를 붙인 커밋에서 웹 빌드를 안 돌리면 며칠 뒤에 발견하게 된다.

---

# Part 3 · 계약 대조표

백엔드 DTO와 프론트 DTO의 JSON 키가 어긋나면 조용히 `null`이 되고, 화면에는 빈 값이 뜬다. 통합 전에 이 표로 한 번 맞춰본다.

| API | 서버 DTO | JSON 키 | 앱 DTO |
|---|---|---|---|
| `POST /auth/signup`<br>`POST /auth/login`<br>`POST /auth/test-login` | `AuthResponse` | `accessToken` · `tokenType` · `expiresIn` · `user{userId,email,nickname}` | `AuthResponseDto` |
| `GET /auth/me`<br>`PATCH /auth/me` | `MeResponse` | `userId` · `email` · `nickname` · **`declaredSkinType`**(미선택 시 키 생략) · **`isTestAccount`** · `joinedAt` | `MeResponseDto` |
| `POST /skin/analyses`<br>`GET /skin/analyses/latest`<br>`GET /skin/analyses/{id}` | `SkinAnalysisResponse` | `skinAnalysisId` · `skinScore` · `metrics{5}` · `summary` · `highlights[{label,status}]` · **`skinTypeGap{declared,observed,matched,message}`**(미선택 시 키 생략) · `analyzedAt` | `SkinAnalysisDto` |
| `POST /plates`<br>`GET /plates/{id}` | `SkinPlateResponse` | `plateId` · **`skinAnalysisId`** · `plateScore` · **`baseScore`** · `summary` · `food{...}` · `feedbacks{good,caution,action}` · `appliedRules[]` · `createdAt` | `SkinPlateDto` |
| `POST /plates/{id}/simulate` | `PlateSimulateResponse` | `plateId` · `beforeScore` · `afterScore` · `appliedActions[]` · `removedRules[]` · `summary` | `PlateSimulationDto` |
| `GET /recommendations` | `RecommendationResponse` | `skinAnalysisId` · `recommend[]` · `avoid[]` · `generatedAt` | `RecommendationDto` |

**어긋나기 쉬운 지점 5개**

| # | 위험 | 대응 |
|---|---|---|
| 1 | `isTestAccount` — Jackson 버전·네이밍 전략에 따라 키가 흔들릴 수 있음 | 서버에 `@JsonProperty("isTestAccount")` 명시 |
| 2 | `proteinG` 등 — 정수로 오면 `double` 캐스팅 실패 | 앱 DTO에서 `num`으로 받고 `.toDouble()` |
| 3 | `cookingMethod` · `tag` · `actions` — 서버가 새 값 추가 | 앱 enum 파서에 `_ =>` 기본값 (단, 기본값 선택에 주의) |
| 4 | `expectedGain` ≠ `scoreDelta.abs()` | 서버 엔티티에 별도 컬럼, 앱 `ActionDto`에 별도 필드. **합산으로 "실행 후 점수"를 만들지 말 것** |
| 5 | `PlateActionCode` · `SkinType` 이름 | 서버 enum 이름(`HALVE_SOUP`, `OILY` 등)을 앱이 그대로 보낸다. 한쪽만 이름을 바꾸면 400이 난다 |
| 6 | `declaredSkinType` · `skinTypeGap` 이 **없는 것**과 **`UNKNOWN`인 것** | 앱 파서에 기본값을 두지 않는다. `null`이면 선택 칩, 값이 있으면 갭 카드 |

---

# Part 4 · 다음 단계

이 스켈레톤 위에 얹을 것들을 우선순위 순으로 적는다.

| 순서 | 만들 것 | 위치 | Day | 비고 |
|---|---|---|---|---|
| 1 | `AuthService` · `AuthController` | `domain/auth/` | 2 | `/auth/*` 5종(`PATCH /auth/me` 포함). `testLogin()` 첫 줄에 `if (!enabled) throw TEST_LOGIN_DISABLED` |
| 3 | `OpenAiVisionClient` + 프롬프트 · JSON Schema | `infra/openai/` | 3~4 | 피부는 `detail:"high"` 3장 1회, 음식은 `"low"`. 25초 단발, 재시도 없음 |
| 4 | `MockOpenAiVisionClient` | `infra/openai/` | 3 | `@ConditionalOnProperty("app.ai.mock")`. **3번과 같은 날 만든다** — 발표 백업 플랜은 나중에 붙이면 안 붙는다 |
| 5 | `SkinAnalysisService` | `domain/skin/` | 4 | `SkinScoreCalculator`·`SkinHighlightBuilder`(§1.12.1)·`SkinTypeGapAnalyzer`(§1.12.2)는 완성돼 있다. 조립만. **AI 호출은 트랜잭션 밖** |
| 6 | `FoodAnalysisService` · `SkinPlateService` | `domain/food/` · `domain/plate/` | 5~6 | 엔진은 이미 있으므로 조립만. `StandardNutrition.find()` 한 줄 적용 |
| 7 | `SkinPlateService.simulate()` | `domain/plate/` | 6 | 엔진 재호출만. **저장하지 않는다** |
| 8 | `RecommendationService` | `domain/recommendation/` | 7 | `RecommendationCandidates` 사용. **lazy 동기 생성** (비동기 아님) |
| 9 | Flutter `DataSource` · `RepositoryImpl` · `UseCase` | 각 feature `data/` | 3~7 | 인터페이스가 이미 있으므로 채우기만 |
| 10 | `FaceGate` + 카메라 프리뷰 연동 | `skin_analysis/data/` | 5~6 | **Day 8 이후에는 붙이지 마라.** 네이티브 의존성이라 빌드가 깨지면 복구에 시간이 든다 |
| 11 | S01c 피부 타입 선택 화면 + S05 갭 카드 | `auth/presentation/` · `skin_analysis/presentation/` | 2 · 4 | 칩 5개 + 건너뛰기. 갭 카드는 서버가 문장까지 만들어 주므로 렌더링만 |
| 12 | S07 "왜 60점인가" 카드 + 시뮬레이션 버튼 | `skin_plate/presentation/` | 7~8 | **차별점을 화면으로 옮기는 작업.** 지금은 문서와 백엔드 로그에만 있다 |
| 13 | **`kIsWeb` 카메라 분기 + `ConstrainedBox(maxWidth: 430)`** | `skin_analysis/` · `app/` | 7 | **웹 대응. 둘 합쳐 반나절**(PRD §19.2). 웹은 게이트·프리뷰를 건너뛰고 파일 선택으로 간다(§2.12) |

**해커톤 범위에서 제외** — `GET /plates?date=` · S09 히스토리 · 결과 공유 · 온보딩 애니메이션 · 지표 추이 차트.

> ~~API 컨테이너화~~ 는 제외 목록에서 **뺐다.** 노트북 시연 전제였고, PaaS 배포로 바뀌면서 **Dockerfile 이 필수가 됐다**(PRD §9.6).

**시작하기**

```bash
# ---------- 백엔드 ----------
docker compose up -d postgres

# .env 를 만들어 팀이 공유한다 (.gitignore 등록 필수)
# JWT_SECRET 은 한 번만 만들어 고정한다.
# 매번 새로 만들면 재기동 때마다 시연 폰 전부가 로그아웃된다.
cat > .env <<'EOF'
OPENAI_API_KEY=sk-...
JWT_SECRET=<openssl rand -base64 48 로 한 번 생성한 고정값>
SPRING_PROFILES_ACTIVE=local
TEST_ACCOUNT_ENABLED=true
EOF

set -a && source .env && set +a
./gradlew bootRun
#  → "테스트 계정 생성: test@skinplate.app (슬롯 1)" 로그가 뜨면 정상

# 룰 엔진 검증 (PRD 예시 60점 / 87점 재현)
./gradlew test --tests '*PlateRuleEngineTest'

# ---------- 프론트 ----------
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1

# ---------- 웹 (Day 7 · PRD §9.6) ----------
# <백엔드> 는 Day 4 에 확정된 호스팅 도메인
flutter build web --release --dart-define=API_BASE_URL=https://<백엔드>/api/v1
npx wrangler pages deploy build/web --project-name=skinplate
```

> **`android/app/src/main/res/xml/network_security_config.xml`을 Day 2에 넣어라.** 로컬 개발은 HTTP이고 Flutter 디버그 매니페스트는 cleartext를 켜주지 않는다. 빠뜨리면 Day 3 첫 API 호출이 막히고, 그때는 서버를 뒤지게 된다(PRD §9.6).

---

## 부록. 리뷰 반영 이력

### v1.8 (2026-08-13 · 피부 분석 3방향 입력 — PRD v1.7 대응)

게이트에는 FRONT·LEFT·RIGHT 판정이 다 있는데 계약이 정면 한 장이라 나머지 둘이 쓰이지 못하고 있었다. 계약을 셋으로 넓혔다.

| 항목 | 변경 |
|---|---|
| **`VisionClient.analyzeSkin` [수정]** | `(String base64, String mediaType)` → **`(List<FacePhoto> photos)`**. 세 장이 한 요청에 실린다. 음식은 그대로 한 장 |
| **`FacePhoto` · `FacePhotoType` [신설]** | `infra/openai/dto/`. 방향(`FRONT`·`LEFT`·`RIGHT`) + Base64 + 판별된 mediaType. `label` 이 프롬프트에서 사진 앞에 붙는 `[정면]` `[왼쪽 얼굴]` `[오른쪽 얼굴]` 이다 |
| **`SkinAnalysisService.analyze` [수정]** | 파라미터 3개(`front`·`left`·`right`). 방향이 **파라미터 자리에서 정해지므로** 클라이언트가 보낸 순서를 신뢰할 일이 없다. 형식 판별 실패 시 **어느 방향인지 메시지에 담는다** — 아니면 사용자가 셋 다 다시 찍는다 |
| **`SkinAnalysisPrompt.USER` [수정]** | 3방향 설명으로 확장. **`SYSTEM` 과 `SCHEMA` 는 손대지 않았다** — 잣대가 바뀌면 과거 분석과 비교가 안 된다 |
| **`SkinRepository.analyze` [수정]** | `analyze(File image)` → **`analyze({required front, required left, required right})`**. 세 장이 모이기 전에는 호출하지 않는다. 단계마다 부르면 분석이 세 건 생기고 그중 무엇이 오늘의 점수인지 알 수 없다 |
| **`Env.receiveTimeout`** | 25초 → **32초** (서버 25초보다 길되 과하지 않게) |
| **`§1.3` 업로드 상한** | `max-request-size` 10MB → **20MB**. 게이트가 없는 웹은 원본 세 장이 그대로 올라온다 |
| **Entity · DDL · 응답 DTO** | **변경 없음.** 세 장이 `SkinAnalysis` 한 건을 만든다. 마이그레이션도 추가하지 않았다 |
| **`WebConfig` [누락 발견]** | **§1.9 에 적혀 있는데 구현이 없었다.** v1.5 이력이 "코드 변경 없음"이라고 적은 것은 클래스가 있다는 전제였는데, 스켈레톤에서 빠진 채로 넘어왔다. `SecurityConfig` 의 `.cors(withDefaults())` 는 `CorsConfigurationSource` 빈이 없으면 빈 설정으로 풀려 **`Access-Control-Allow-Origin` 을 내보내지 않는다.** APK 는 영향이 없고 **웹만 전부 막힌다.** §1.9 코드를 그대로 옮기고 `WebConfigTest` 로 고정했다 |

---

### v1.7 (2026-08-10 · §2.12 얼굴 게이트 — 호출되지만 없던 함수 채움)

**PRD 변경 없음.** 게이트 구조(v1.6)는 그대로 두고, `check()` 가 부르는데 정의가 없던 두 함수와 크롭 경로를 채웠다. Day 5 에 FE-A 가 빈칸부터 시작하지 않게 하는 것이 목적이다.

| 항목 | 변경 |
|---|---|
| **`_toInputImage` [신설]** | 호출만 있고 본문이 없었다. **플랫폼별 포맷이 다르다** — Android `nv21` · iOS `bgra8888`. 어긋나도 예외가 안 나고 **검출이 0개로 나와** "얼굴이 화면 안에 들어오게 해주세요"만 계속 뜬다. 센서 방향(`rotation`) 누락도 같은 증상이라 원인을 게이트 조건에서 찾게 된다. 포맷을 못 읽으면 `FaceGateUnavailable` 로 열어 준다 |
| **`_withMargin` [신설]** | 호출만 있고 본문이 없었다. 딱 맞게 자르면 **이마와 턱이 잘리는데 그 둘이 유분·트러블 판정 정보량이 가장 많은 영역**이다 |
| **`_faceLuminance` 수정** | 기존 주기가 "양 플랫폼을 `yuv420` 으로 통일하라"였는데, 그러면 **iOS 에서 ML Kit 이 프레임을 못 읽는다.** 포맷 통일 대신 **밝기 쪽을 분기**한다 — `nv21` 은 Y 평면, `bgra8888` 은 Rec.601 근사 |
| **⚠️ 크롭 좌표계 [수정]** | `faceRect` 는 **프리뷰(1280×720) 좌표**인데 크롭 대상은 **원본 사진(4032×3024)** 이라, 그대로 쓰면 사진 귀퉁이의 엉뚱한 조각이 잘려 OpenAI 로 간다. **게이트는 초록불인데 결과만 이상해진다.** 배율 계산 대신 **사진에서 한 번 더 검출**한다 — 촬영 1회당 50~100ms 이고 EXIF 방향까지 `fromFilePath` 가 처리한다. `faceRect` 는 프리뷰 오버레이 전용으로 격하 |
| **`prepareSkinPhoto` 보강** | `File(...)` 자리표시자를 실제 경로로. 사진에서 얼굴을 못 찾으면 **크롭을 포기하고 원본을 올린다**(게이트 통과한 사용자를 여기서 막지 않는다). 크롭 사각형 경계 클램프, `detector.close()` 를 `finally` 로 |
| **완료 조건 추가** | **실기기 Android 1대 · iOS 1대에서 게이트가 실제로 열리는지** 확인을 커밋 조건에 포함. 에뮬레이터 통과는 근거가 안 된다 |

---

### v1.5 (2026-08-10 · PRD v1.6 대응 — 배포 구성 · 웹 추가)

> **백엔드 호스팅은 이 개정에서 확정하지 않았다. Day 4 배포 착수 전 결정한다.**

**코드 변경은 없다.** 배포 구성이 바뀌었고 플랫폼에 웹이 추가되어, 그 사실이 걸리는 자리에만 주석·주기를 달았다.

| 항목 | 변경 |
|---|---|
| **§1.3 DB 설정** | **배포는 Supabase 무료 Postgres.** `DB_NAME` 에 주석 추가 — **배포에서는 `postgres`, `skinplate` 가 아니다.** 안 넘기면 기동이 `FATAL: database "skinplate" does not exist` 로 끝난다. 기본값(로컬 Docker 기준)은 그대로 |
| **§1.9 `WebConfig`** | **코드 변경 없음.** Flutter Web을 붙여도 고칠 것이 없다는 사실을 명시 — `/api/**` 전체 허용이 이미 있고, **쿠키가 아니라 `Authorization` 헤더**를 쓰므로 `allowCredentials`·`SameSite` 함정에 닿지 않는다 |
| **§2.5 `TokenStorage`** | **코드 변경 없음.** 웹에서는 `flutter_secure_storage` 가 브라우저 저장소(localStorage 수준)로 폴백한다는 주기 추가. API는 동일하므로 분기 불필요 |
| **§2.12 얼굴 게이트** | **웹에서는 적용되지 않는다.** ML Kit은 Android/iOS 전용. **`kIsWeb` 이면 게이트를 건너뛰고 파일 선택 경로**를 쓴다. 서버의 `faceDetected:false` 폴백이 받쳐 준다 |
| **Part 4 · 13번** | **`kIsWeb` 분기 + `ConstrainedBox(maxWidth: 430)` 추가.** Day 7, 둘 합쳐 반나절 |
| **Part 4 · 시작하기** | **웹 빌드·배포 2줄 추가** (`flutter build web` → `wrangler pages deploy`) |

---

### v1.4 이하 (2026-08-09)

외부 리뷰 30건을 검증해 유효 20건을 반영했다. 주요 변경만 적는다.

| 항목 | 변경 |
|---|---|
| **v1.3 · 시뮬레이션 안전** | detached 복사본 + `readOnly=true` (§1.19.2). 관리 엔티티를 만지면 `orphanRemoval`이 재료를 DELETE 한다 |
| **v1.3 · Highlights** | 위치는 선택만, **상태는 값(60/40)이 결정**. 18점에 초록, 94점에 빨강이 뜨던 결함 |
| **v1.3 · 얼굴 게이트** | `check(..., int Function(Rect) luminanceOf)` 콜백으로 순환 해소. 휘도는 **YUV `planes[0]`** 직접 읽기. 갤러리 우회 + 3회 실패 탈출구 |
| **v1.3 · 기타** | `StandardNutrition` → `LinkedHashMap` · `LESS_RICE` 제거 · `baseScore` 추가 · §2.11/§2.12 순서 · 파일 목록 갱신 |
| Skin Score | 산식 확정(§1.12.1). 예시 86 → **55** (지표 방향 정렬 평균) |
| Highlights | 위치 기반 산출 규칙 확정 — 항상 GOOD 1 + WARN 1 + CAUTION 1 |
| Mock 스위치 | `@Profile("mock")` → **`@ConditionalOnProperty("app.ai.mock")`** 로 통일 |
| OpenAI | 피부만 `detail:"high"`. 서버 18초 **단발**(재시도 제거), `TimeoutException` 별도 분기 |
| Flutter 타임아웃 | 30초 → **25초** (서버 18초보다 길되 과하지 않게) |
| 이미지 저장 | **v1.4에서 제거.** 서버는 이미지를 저장하지 않고 Base64로 OpenAI에 보내고 버린다. `imageUrl`·`ImageStorage`·리소스 핸들러·`STORAGE_BASE_URL` 전부 삭제 (PRD §9.6) |
| 401 처리 | `/auth/` 요청은 인터셉터에서 제외 — 로그인 실패가 리다이렉트를 유발하던 문제 |
| 트랜잭션 | AI 호출은 트랜잭션 밖, DTO 변환은 `@Transactional(readOnly=true)` 안 |
| 룰 메시지 | 전부 명사구로 통일 — `buildSummary`가 "…자극합니다입니다" 비문을 만들던 문제 |
| 룰 정렬 | `thenComparing(code)` 추가 — 동일 priority에서 순서가 비결정이던 문제 |
| `potentialScore` | **제거.** 합산(74)이 실제 재계산(68/80)과 달랐다 → `POST /plates/{id}/simulate` |
| 정적 데이터 | `StandardNutrition`·`RecommendationCandidates` 위치 확정 |
| 얼굴 게이트 | ML Kit 기반 게이트·크롭 추가(§2.12). OpenCV·MediaPipe는 채택하지 않음 |
| **피부 타입 선택** | `SkinType` enum · `app_user.declared_skin_type`(NULL 허용) · `PATCH /auth/me` · `SkinTypeGapAnalyzer`(§1.12.2) 추가. **`PlateContext`는 그대로** — 점수 계산에는 개입하지 않는다 |
| 범위 축소 | `GET /plates?date=` 제외 |

---

*문서 끝 · Skin Plate DTO & 도메인 구조 v1.6 (PRD v1.6 기준) — 구현 착수본*
