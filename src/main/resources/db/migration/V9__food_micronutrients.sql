-- ============================================================
-- 표준 영양 컬럼 확장 (2026-08-18)
--
--   saturated_fat_g  포화지방 — R11 이 총지방 대신 이것을 본다
--   fiber_g          식이섬유 — R15 가 100kcal 당 밀도로 본다
--   vitamin_a_ug     비타민A(μg RAE) — R06 이 재료 태그와 OR 로 본다
--   vitamin_c_mg     비타민C(mg)     — 같음
--   zinc_mg          아연 — 저장만 한다. 밀도가 육류에 쏠려 가점 룰을 만들지 않았다
--
-- 원본(전국통합식품영양성분정보 음식표준데이터)은 160 컬럼인데 여섯 개만 가져오고
-- 있었다. 그래서 룰이 포화지방을 못 봐 연어와 삼겹살을 같은 지방으로 취급했고,
-- 채소를 알아볼 방법이 재료 태그밖에 없어 1,443종 중 42종만 비타민 가점을 받았다.
--
-- NOT NULL DEFAULT 0 인 이유 — 기존 여섯 컬럼과 같은 규약이다. 0 은 "없다"가 아니라
-- "모른다"로 읽히고, 이 다섯을 쓰는 룰이 전부 "값이 클수록 발동"이라 모르는 값은
-- 어느 쪽으로도 점수를 움직이지 않는다. 표준 테이블에 없는 음식(AI 추정만 있는 경우)도
-- 같은 자리로 떨어진다 — AI 스키마에는 이 다섯이 없기 때문이다.
-- ============================================================

alter table food_analysis add column saturated_fat_g numeric(6,2) not null default 0;
alter table food_analysis add column fiber_g         numeric(6,2) not null default 0;
alter table food_analysis add column vitamin_a_ug     integer      not null default 0;
alter table food_analysis add column vitamin_c_mg     numeric(6,2) not null default 0;
alter table food_analysis add column zinc_mg          numeric(6,2) not null default 0;
