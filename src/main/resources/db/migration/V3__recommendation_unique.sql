-- 추천은 피부 분석 1건당 한 벌만 존재해야 한다.
--
-- RecommendationService.getOrCreate 가 "있나 보고 없으면 넣는다" 로 되어 있는데,
-- READ COMMITTED 에서 동시 요청 둘이 모두 "없다" 를 보고 둘 다 넣을 수 있다.
-- S08 을 두 번 누르거나 느린 첫 응답에 클라이언트가 재시도하면 행이 두 벌 생기고,
-- 이후 조회마다 같은 음식이 영구히 두 번씩 뜬다. 지우기 전까지 회복되지 않는다.
--
-- 애플리케이션 쪽 검사만으로는 막을 수 없다. 경합을 실제로 끊는 것은 이 제약이다.

-- 제약을 걸기 전에 이미 들어간 중복을 정리한다. 개발 중 만들어졌을 수 있고,
-- 남아 있으면 ADD CONSTRAINT 가 실패해 기동이 통째로 멈춘다(ddl-auto: validate).
DELETE FROM recommendation r
 USING recommendation keep
 WHERE r.skin_analysis_id = keep.skin_analysis_id
   AND r.type             = keep.type
   AND r.food_name        = keep.food_name
   AND r.id > keep.id;

ALTER TABLE recommendation
    ADD CONSTRAINT uq_recommendation_analysis_type_food
    UNIQUE (skin_analysis_id, type, food_name);
