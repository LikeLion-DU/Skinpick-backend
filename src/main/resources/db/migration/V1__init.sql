-- ============================================================
-- Skin Plate 초기 스키마 (PRD v1.4 §12 ERD)
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
    image_url        VARCHAR(500) NOT NULL,
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
    image_url       VARCHAR(500) NOT NULL,
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
