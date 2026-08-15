-- 개인화 피부 인사이트.
--
-- 주제 선정·우선순위는 백엔드 규칙이 정하고 AI 는 문장만 만든다(PRD §18.10).
-- 그래서 저장하는 것도 문장과 "무엇을 근거로 골랐는가"뿐이다 — 점수는 여기 없다.

-- 수분 섭취 자가 신고. NULL = 미선택 (습관 3종과 같은 의미론).
ALTER TABLE app_user ADD COLUMN water_intake VARCHAR(20);

-- skin_analysis_id UNIQUE = 분석 1건당 인사이트 1건.
-- 생성 1회 고정이 정책인데 애플리케이션 검사만으로는 READ COMMITTED 동시 요청을
-- 막을 수 없다(V3 가 추천에서 겪은 그대로다). 락으로 줄을 세우고 이 제약이 뒤를 받친다.
--
-- snapshot_* 는 생성 당시 프로필이다. 프로필을 나중에 바꿔도 과거 인사이트의 근거는
-- 안 바뀌어야 한다 — 문장은 그때의 습관을 말하고 있는데 근거만 새 값으로 보이면
-- "왜 이런 말이 나왔나"를 설명할 수 없다.
CREATE TABLE skin_insight (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    skin_analysis_id        BIGINT       NOT NULL UNIQUE REFERENCES skin_analysis (id) ON DELETE CASCADE,
    summary                 VARCHAR(300) NOT NULL,
    snapshot_sleep_pattern  VARCHAR(20),
    snapshot_stress_level   VARCHAR(20),
    snapshot_exercise_habit VARCHAR(20),
    snapshot_water_intake   VARCHAR(20),
    snapshot_concerns       VARCHAR(200) NOT NULL DEFAULT '',
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP
);

-- 주제별 항목 (최대 3). display_order 0/1/2 가 그대로 우선순위 HIGH/MEDIUM/LOW 다 —
-- 우선순위를 따로 컬럼으로 두면 순서와 등급이 어긋나는 상태가 표현 가능해진다.
CREATE TABLE skin_insight_item (
    id              BIGSERIAL PRIMARY KEY,
    skin_insight_id BIGINT       NOT NULL REFERENCES skin_insight (id) ON DELETE CASCADE,
    category        VARCHAR(20)  NOT NULL,
    title           VARCHAR(50)  NOT NULL,
    description     VARCHAR(300) NOT NULL,
    action_title    VARCHAR(100) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_skin_insight_item_insight ON skin_insight_item (skin_insight_id);
