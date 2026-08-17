# API 응답 픽스처

**실제 서버 응답을 그대로 받아 적은 것이다.** 손으로 만들거나 다른 응답에서 파생하지 않았다.

채취 조건 — 로컬 `bootRun` · `AI_MOCK=true` · 2026-08-14 · commit `4e569cd`

> **2026-08-17 이후 응답에는 필드가 더 있다** — `skinBasis`·`skinMeasuredAt`(분석·기록), `food` 의 관찰 특성 5종(`foodGroup`~`processingLevel`), `feedbacks.good[]/caution[]` 의 `reason`. 전부 **추가**라 이 픽스처의 필드는 그대로 유효하다. 점수(60·72)도 그대로다 — Mock 특성이 강도 1.0 조합이다.

| 파일 | 엔드포인트 | 상태 |
|---|---|---|
| `plate_analyze.json` | `POST /api/v1/plates/analyze` | 200 |
| `plate_simulate.json` | `POST /api/v1/plates/simulate` | 200 |
| `plate_record.json` | `POST /api/v1/plates/records` | 201 |

## 읽을 때 주의

**`analysisToken` 값은 마스킹했다.** 실제 토큰은 **800자**였고, 여기 적힌 값은 자리표시자다. 로컬 시크릿으로 서명된 실물을 저장소에 남길 이유가 없다. 길이에 의존하는 테스트를 만들지 마라 — 재료 수에 따라 달라진다.

**`plate_analyze.json` 에 `plateId`·`createdAt` 이 없다.** 저장 전이라 그렇다. `food.foodAnalysisId` 도 없다 — 서버가 `non_null` 정책으로 키 자체를 뺀다.

**`plate_simulate.json` 에도 `plateId` 가 없다.** 저장되지 않은 분석을 대상으로 하므로 돌려줄 id 가 없다. 저장된 Plate 용 `POST /plates/{id}/simulate` 응답에는 있다 — 그쪽은 별개 계약이다.

**`plate_record.json` 은 `GET /plates/{id}` 와 같은 형태다.** 저장이 확정된 기록이라 `plateId`·`createdAt` 이 들어 있다 — `SkinPlateDto` 하나로 두 응답을 받으면 된다.

## 이 데이터로 확인된 것

- `analyze.plateScore` = **60** — 문서의 시연 예시값과 일치한다
- `simulate.beforeScore` = **60** — analyze 가 보여준 점수와 같다. 두 경로가 같은 변환(`toEntity`)을 타는지 확인하는 값이다
- `simulate.afterScore` = **72** — `REMOVE_BATTER` + `LESS_SPICY` 로 `R02` 가 사라진다

## 멱등성 (같은 채취 세션에서 확인)

같은 `analysisToken` 으로 저장을 **6회**(순차 2 + 동시 4) 요청해 **전부 같은 `plateId`** 를 받았고, DB 의 해당 행은 **1건**이었다. 앱은 저장 실패·타임아웃 시 **같은 토큰으로 재시도해도 안전하다.**
