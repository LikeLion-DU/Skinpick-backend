-- ============================================================
-- 점수 입력을 표준 음식표로 고정한다 (2026-08-18)
--
--   food_analysis.standard_food_name  매칭된 표준 음식 이름. NULL = 매칭 실패
--   food_ingredient.from_standard     이 재료 태그가 표준표에서 온 것인가
--
-- 같은 사진을 세 번 분석하면 점수가 50 / 55 / 58 로 갈렸다. 영양값은 표준표가
-- 이미 고정하고 있었지만 **재료 태그와 매운맛 강도가 AI 유래라 회차마다 달랐다** —
-- ANTIOXIDANT 가 왔다 가면 R06 이 ±5, PROBIOTIC 이면 R09 가 ±4, 강도가
-- HOT↔MEDIUM 이면 R02 가 ±4 움직인다.
--
-- 표준 음식으로 확정된 한 끼는 이제 점수 입력을 전부 표준표에서만 받는다.
-- AI 재료는 화면 표시와 AI 코멘트에 그대로 쓰인다 — 점수와 설명을 분리하는 것이
-- 이 컬럼 둘의 목적이다.
--
-- 이 컬럼이 없던 시절 행은 standard_food_name 이 NULL 이라 "매칭 실패" 로 읽힌다.
-- 그 행들은 태그 룰이 꺼진 채로 재평가되므로 저장된 점수와 시뮬레이션의 before 가
-- 갈릴 수 있다 — 이미 문서화된 동작이다(룰을 고치면 옛 기록은 옛 점수를 유지한다).
--
-- **태그만이 아니다.** 미매칭으로 읽히면 scoringTraits() 가 저장된 AI 관찰값을 돌려주므로
-- 강도 계수도 함께 되살아난다 — Spiciness=HOT 로 저장된 떡볶이는 R02 가 -12 가 아니라
-- -16 으로 재평가된다. 옛 행을 되메울 방법은 없다(이름만 남아 있고 그때의 매칭 결과는
-- 기록되지 않았다). 새 기록부터 맞는다.
-- ============================================================

alter table food_analysis   add column standard_food_name varchar(100);
alter table food_ingredient add column from_standard boolean not null default false;
