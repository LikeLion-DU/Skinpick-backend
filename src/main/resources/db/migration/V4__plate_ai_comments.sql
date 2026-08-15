-- 확정 시안이 요구하는 AI 문장 두 개.
--
--   ai_tip           결과 화면의 "AI 맞춤 TIP" — 이 기록 하나에 대한 다음 식사 제안
--   ai_daily_comment "오늘의 AI 코멘트" — 저장 시점 기준 그날 전체 기록에 대한 한 줄
--
-- 저장 시 1회 생성한다(A안). 조회 때마다 만들면 홈을 열 때마다 과금과 지연이
-- 생기고, 같은 날을 두 번 열면 다른 문장이 나온다.
--
-- daily comment 를 별도 테이블이 아니라 기록 행에 두는 이유: 그날 기록이 하나
-- 늘 때마다 문장이 달라져야 하는데, 최신 기록에 함께 저장하면 "그날의 최신
-- 문장 = 그날 마지막 기록의 문장"이라는 규칙 하나로 끝난다.
--
-- 둘 다 NULL 허용 — AI 가 실패해도 저장은 되어야 한다. 문장이 없으면 앱이
-- 카드를 그리지 않는다.
alter table skin_plate add column ai_tip varchar(300);
alter table skin_plate add column ai_daily_comment varchar(300);
