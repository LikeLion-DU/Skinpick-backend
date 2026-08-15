-- 피부 프로필 (목업 "피부설정") — 자가 신고 고민·생활 습관.
-- 습관 3종은 NULL = 미선택 (declared_skin_type 과 같은 의미론).
-- 고민은 복수 선택이라 별도 테이블. PK(user_id, concern) 가 중복 선택을 DB 에서 막는다.
ALTER TABLE app_user ADD COLUMN sleep_pattern  VARCHAR(20);
ALTER TABLE app_user ADD COLUMN stress_level   VARCHAR(20);
ALTER TABLE app_user ADD COLUMN exercise_habit VARCHAR(20);

CREATE TABLE user_skin_concern (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    concern VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, concern)
);
