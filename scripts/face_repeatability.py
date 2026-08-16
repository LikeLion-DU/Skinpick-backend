#!/usr/bin/env python3
"""실제 얼굴 사진으로 피부 분석 모델의 반복성을 재는 게이트. (PRD §17.2)

같은 사진을 다시 분석했을 때 결과가 크게 달라지지 않는다 — 이 제품이 하는 주장이다.
gpt-5 계열은 temperature 를 고정할 수 없어 재현성 레버가 없으므로, 모델을 바꾸거나
프롬프트를 손댄 뒤에는 이 스크립트로 확인하고 넘어간다.

    python3 scripts/face_repeatability.py <사진_루트> [반복횟수]

사진 루트 구조 (사람마다 폴더 하나, 파일명 고정)
    faces/
      personA/front.jpg  left.jpg  right.jpg
      personB/...
      personC/...

판정 기준 — 하나라도 벗어나면 OPENAI_MODEL=gpt-4o 로 되돌린다
    Skin Score 폭 ≤ 5 · 피부 나이 폭 ≤ 3 · 피부 타입 일치율 100%
    (총점 5점은 SkinLevel 등급 구간 폭 20 안이라 화면 문구와 뱃지 색이 안 바뀐다)

★ 계정 일일 요청 한도를 먼저 확인한다. Free 티어는 모델당 하루 50회이고
  이 스크립트는 사람수 × 반복 만큼 쓴다. 3세트 × 5회 = 15회다.
"""
import base64, json, os, re, statistics, sys, textwrap, time, urllib.error, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROMPT = f"{ROOT}/src/main/java/com/skinplate/api/infra/openai/prompt/SkinAnalysisPrompt.java"
PHOTO_TYPE = f"{ROOT}/src/main/java/com/skinplate/api/infra/openai/dto/FacePhotoType.java"

FACES = sys.argv[1] if len(sys.argv) > 1 else f"{ROOT}/faces"
RUNS = int(sys.argv[2]) if len(sys.argv) > 2 else 5
# 배포에 나가는 모델만 잰다. 판정 기준이 "gpt-4o 보다 나은가"가 아니라 절대값이라
# 비교군이 필요 없고, 하루 50회짜리 계정에서 요청이 두 배가 된다.
# 굳이 비교하려면  MODELS=gpt-5.6-luna,gpt-4o  로 준다.
MODELS = os.environ.get("MODELS", "gpt-5.6-luna").split(",")

METRICS = ["hydration", "oil", "redness", "trouble", "barrier"]
AGE_AXES = ["skinTexture", "elasticity", "wrinkles", "skinTone",
            "pores", "pigmentation", "redness", "blemishMarks"]
# gpt-4o 는 이 조직에서 RPM 3 이다. 벌리지 않으면 429 로 표본이 깨진다.
GAP_SECONDS = {"gpt-4o": 21}

PASS_SCORE_RANGE = 5
PASS_AGE_RANGE = 3


def api_key():
    for line in open(f"{ROOT}/.env"):
        if line.startswith("OPENAI_API_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit(".env 에 OPENAI_API_KEY 가 없다")


def block(name):
    """SkinAnalysisPrompt.java 의 텍스트 블록을 읽는다 — 사본을 두면 곧 어긋난다.

    dedent 가 핵심이다. javac 는 텍스트 블록의 공통 들여쓰기를 벗겨서 보내는데,
    소스를 그대로 읽으면 줄마다 12칸이 남아 프로덕션과 다른 프롬프트를 재게 된다.
    """
    raw = re.search(rf'{name} = """\n(.*?)""";', open(PROMPT).read(), re.S).group(1)
    return textwrap.dedent(raw)


def photo_labels():
    """FacePhotoType 의 라벨을 그대로 읽는다. 손으로 옮겨 적으면 라벨을 고친 날
    하네스만 옛 프롬프트를 재고, 그 측정이 모델 채택의 유일한 근거가 된다."""
    src = open(PHOTO_TYPE).read()
    found = dict(re.findall(r'(FRONT|LEFT|RIGHT)\("([^"]+)"', src))
    if len(found) != 3:
        sys.exit("FacePhotoType 에서 라벨 3개를 못 읽었다")
    return {"front": found["FRONT"], "left": found["LEFT"], "right": found["RIGHT"]}


def schema():
    """SCHEMA_JSON 은 나이 축 8개를 %1$s 로 끼워 넣으므로 같은 치환을 여기서도 한다."""
    src = open(PROMPT).read()
    axis = re.search(r'AXIS = """\n(.*?)""";', src, re.S).group(1)
    template = re.search(r'SCHEMA_JSON = """\n(.*?)"""\s*\.formatted', src, re.S).group(1)
    return json.loads(template.replace("%1$s", axis))


def skin_score(d):
    """SkinScoreCalculator 와 같은 산식."""
    return round((d["hydration"] + d["barrier"]
                  + (100 - d["oil"]) + (100 - d["redness"]) + (100 - d["trouble"])) / 5)


def content_for(folder, user_prompt, labels):
    parts = [{"type": "text", "text": user_prompt}]
    for slot in ["front", "left", "right"]:
        path = next((f"{folder}/{slot}{ext}" for ext in (".jpg", ".jpeg", ".png")
                     if os.path.exists(f"{folder}/{slot}{ext}")), None)
        if path is None:
            sys.exit(f"{folder}/{slot}.jpg 가 없다")
        media = "image/png" if path.endswith(".png") else "image/jpeg"
        parts.append({"type": "text", "text": f"[{labels[slot]}]"})
        parts.append({"type": "image_url", "image_url": {
            "url": f"data:{media};base64," + base64.b64encode(open(path, "rb").read()).decode(),
            "detail": "high"}})
    return parts


def call(key, model, system, content, sch):
    """OpenAiVisionClient.call() 과 같은 본문. 모델별 규약 분기도 같다."""
    body = {"model": model,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": content}],
            "response_format": {"type": "json_schema", "json_schema": {
                "name": "skin_analysis", "strict": True, "schema": sch}}}
    if "gpt-5" in model:
        body["max_completion_tokens"] = 1400
        body["reasoning_effort"] = "low"
    else:
        body["max_tokens"] = 1400
        body["temperature"] = 0.2

    request = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions", data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})

    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = json.load(response)
        return {"ok": True, "latency": time.time() - started, "usage": payload["usage"],
                "data": json.loads(payload["choices"][0]["message"]["content"])}
    except urllib.error.HTTPError as e:
        message = json.loads(e.read().decode()).get("error", {}).get("message", "")
        return {"ok": False, "latency": time.time() - started, "code": e.code,
                "rate_limited": e.code == 429, "error": message[:160]}
    except Exception as e:
        # 연결이 한 번 끊겼다고 run 전체를 버리면, 이미 쓴 요청이 같이 날아간다.
        # 하루 50회짜리 계정에서 그건 그날 예산의 일부다. 실패로 세고 계속한다.
        return {"ok": False, "latency": time.time() - started, "code": type(e).__name__,
                "rate_limited": False, "error": str(e)[:160]}


def spread(values):
    return max(values) - min(values) if values else 0


def main():
    key, system, user_prompt, sch = api_key(), block("SYSTEM"), block("USER"), schema()
    labels = photo_labels()

    people = sorted(d for d in os.listdir(FACES) if os.path.isdir(f"{FACES}/{d}"))
    if not people:
        sys.exit(f"{FACES} 안에 사람별 폴더가 없다. front/left/right 3장씩 넣어라.")
    print(f"얼굴 {len(people)}세트 · 모델당 세트당 {RUNS}회 "
          f"→ 총 {len(people) * RUNS * len(MODELS)} 요청\n")

    summary = {}
    for model in MODELS:
        per_person, limited, failed = [], 0, 0

        for person in people:
            content = content_for(f"{FACES}/{person}", user_prompt, labels)
            data = []
            for i in range(RUNS):
                result = call(key, model, system, content, sch)
                if result["ok"]:
                    d = result["data"]
                    data.append((d, result))
                    print(f"  {model:14} {person:9} {i + 1}/{RUNS}  "
                          f"{[d[k] for k in METRICS]} score={skin_score(d)} "
                          f"age={d['skinAgeAnalysis']['estimatedSkinAge']} "
                          f"type={d['skinType']['primary']} {result['latency']:.1f}s")
                else:
                    failed += 1
                    limited += result["rate_limited"]
                    print(f"  {model:14} {person:9} {i + 1}/{RUNS}  "
                          f"✗ {result['code']} {result['error'][:80]}")
                time.sleep(GAP_SECONDS.get(model, 2))

            if not data:
                continue
            results = [d for d, _ in data]
            scores = [skin_score(d) for d in results]
            ages = [d["skinAgeAnalysis"]["estimatedSkinAge"] for d in results]
            types = [d["skinType"]["primary"] for d in results]
            per_person.append({
                "metric_range": {k: spread([d[k] for d in results]) for k in METRICS},
                "score_range": spread(scores),
                "age_range": spread(ages),
                "axis_range": statistics.mean(
                    spread([d["skinAgeAnalysis"][a]["score"] for d in results]) for a in AGE_AXES),
                "type_agree": max(types.count(t) for t in set(types)) / len(types),
                "latency": statistics.mean(r["latency"] for _, r in data),
                "cost_tokens": statistics.mean(
                    r["usage"]["prompt_tokens"] + r["usage"]["completion_tokens"] for _, r in data),
            })

        if per_person:
            summary[model] = {
                # 평균은 표시용이다. 판정은 아래 worst_* 로 한다 — 세 명 중 한 명이
                # 10점 흔들려도 평균 4.7 이면 통과로 찍히는데, 그게 이 게이트가
                # 잡으라고 있는 상황이다.
                key_: statistics.mean(p[key_] for p in per_person)
                for key_ in ["score_range", "age_range", "axis_range",
                             "type_agree", "latency", "cost_tokens"]
            } | {
                "metric_range": {k: statistics.mean(p["metric_range"][k] for p in per_person)
                                 for k in METRICS},
                "worst_score_range": max(p["score_range"] for p in per_person),
                "worst_age_range": max(p["age_range"] for p in per_person),
                "worst_type_agree": min(p["type_agree"] for p in per_person),
                "rate_limited": limited, "failed": failed,
            }

    if not summary:
        sys.exit("\n성공한 호출이 없다. 일일 요청 한도부터 확인한다.")

    models = list(summary)
    rows = [("실제 얼굴 평균 latency", lambda s: f"{s['latency']:.1f}초"),
            ("Skin Score 평균 range", lambda s: f"{s['score_range']:.1f}"),
            ("피부 나이 평균 range", lambda s: f"{s['age_range']:.1f}")]
    rows += [(f"{k} range", lambda s, k=k: f"{s['metric_range'][k]:.1f}") for k in METRICS]
    rows += [("피부 타입 일치율", lambda s: f"{s['type_agree'] * 100:.0f}%"),
             ("피부 나이 축 평균 변동", lambda s: f"{s['axis_range']:.1f}"),
             ("평균 토큰/건", lambda s: f"{s['cost_tokens']:.0f}"),
             ("429 발생", lambda s: f"{s['rate_limited']}건"),
             ("실패", lambda s: f"{s['failed']}건")]

    print("\n\n" + f"{'항목':<26}" + "".join(f"{m:>18}" for m in models))
    print("-" * (26 + 18 * len(models)))
    for name, render in rows:
        print(f"{name:<26}" + "".join(f"{render(summary[m]):>18}" for m in models))

    print("\n판정 — 얼굴 세트 중 가장 나쁜 값으로 본다 (하나라도 벗어나면 미달)")
    for model, s in summary.items():
        checks = [(f"Skin Score 최대 폭 {s['worst_score_range']:.0f} ≤ {PASS_SCORE_RANGE}",
                   s["worst_score_range"] <= PASS_SCORE_RANGE),
                  (f"피부 나이 최대 폭 {s['worst_age_range']:.0f} ≤ {PASS_AGE_RANGE}",
                   s["worst_age_range"] <= PASS_AGE_RANGE),
                  (f"타입 최저 일치율 {s['worst_type_agree'] * 100:.0f}% = 100%",
                   s["worst_type_agree"] == 1.0),
                  (f"실패 {s['failed']}건 = 0", s["failed"] == 0)]
        ok = all(passed for _, passed in checks)
        print(f"\n{model}: {'✓ 통과' if ok else '✗ 미달 — OPENAI_MODEL 을 되돌린다'}")
        for text, passed in checks:
            print(f"    {'✓' if passed else '✗'} {text}")


if __name__ == "__main__":
    main()
