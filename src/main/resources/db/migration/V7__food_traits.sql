-- ============================================================
-- 음식 관찰 특성 5종 (AI 스키마 v2 · PRD §17.3)
--
--   food_group        음식 분류 enum — 자유 문자열 food_category 는 표시용으로 남긴다
--   portion_size      섭취량 추정(SMALL/MEDIUM/LARGE/UNKNOWN)
--   spiciness         매운맛 강도 — R02 감점의 강도 계수
--   oiliness          기름진 정도 — R07 이 튀김 아닌 기름진 음식도 보게 된다
--   processing_level  가공도(NOVA 4단계) — 아직 점수에 안 쓴다. 패턴 분석용 축적
--
-- 전부 NULL 허용이다. 이 컬럼이 없던 시절 행은 null 로 남고, 코드가 null 을
-- UNKNOWN 으로 읽어(FoodTraits.getter) 룰·리포트가 기존과 동일하게 동작한다 —
-- 하위 호환의 불변식이라 backfill 하지 않는다.
--
-- portion_size 는 점수에 쓰지 않는다(리포트 영양 환산 전용). 표준 음식 테이블이
-- 확보한 "같은 사진 = 같은 점수" 재현성과 충돌하기 때문이다.
-- ============================================================

alter table food_analysis add column food_group       varchar(20);
alter table food_analysis add column portion_size     varchar(10);
alter table food_analysis add column spiciness        varchar(10);
alter table food_analysis add column oiliness         varchar(10);
alter table food_analysis add column processing_level varchar(20);
