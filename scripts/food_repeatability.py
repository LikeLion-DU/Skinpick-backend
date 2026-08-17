#!/usr/bin/env python3
"""실제 음식 사진으로 음식 인식의 반복성을 재는 게이트. (face_repeatability.py 의 음식판)

같은 사진에서 점수가 흔들리는 경로는 셋뿐이다 — 이 스크립트는 그 셋만 잰다.
  1. foodName 이 흔들려 표준 테이블 적중이 갈린다 → 영양값·조리법이 통째로 바뀐다
  2. spiciness 가 MILD/MEDIUM/HOT 사이를 오간다 → R02 감점이 ±30% 흔들린다
  3. oiliness HIGH 여부·spicy/CAPSAICIN 여부가 갈린다 → R07·R02 발동 자체가 갈린다
칼로리·나트륨 추정치는 표준 테이블이 덮어쓰므로 (적중하는 한) 흔들려도 무해하다 —
그래서 게이트가 아니라 참고로만 찍는다. portionSize 도 점수 밖이라 참고다.

    python3 scripts/food_repeatability.py <사진_루트> [반복횟수]

사진 루트 구조 (음식 하나 = 파일 하나)
    foods/
      kimchi_stew.jpg  ramen.jpg  salad.png  ...

판정 기준 — 하나라도 벗어나면 표준 테이블에 그 음식의 특성을 심거나 모델을 되돌린다
    표준 테이블 적중 이름이 회차마다 같을 것 · spiciness 강도 계수가 갈리지 않을 것
    · R02(매움)·R07(튀김/기름) 발동 여부가 갈리지 않을 것

★ 계정 일일 요청 한도를 먼저 확인한다. Free 티어는 모델당 하루 50회이고
  이 스크립트는 음식수 × 반복 만큼 쓴다. 5장 × 5회 = 25회다.
"""
import base64, json, os, re, statistics, sys, textwrap, time, urllib.error, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROMPT = f"{ROOT}/src/main/java/com/skinplate/api/infra/openai/prompt/FoodAnalysisPrompt.java"
CLIENT = f"{ROOT}/src/main/java/com/skinplate/api/infra/openai/OpenAiVisionClient.java"
CONSTANTS = f"{ROOT}/src/main/java/com/skinplate/api/domain/plate/engine/RuleConstants.java"

FOODS = sys.argv[1] if len(sys.argv) > 1 else f"{ROOT}/foods"
RUNS = int(sys.argv[2]) if len(sys.argv) > 2 else 5


def api_key():
    for line in open(f"{ROOT}/.env"):
        if line.startswith("OPENAI_API_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit(".env 에 OPENAI_API_KEY 가 없다")


def deployed_model():
    src = open(f"{ROOT}/src/main/resources/application.yml").read()
    return re.search(r"model:\s*\$\{OPENAI_MODEL:([^}]+)\}", src).group(1).strip()


def block(name):
    """FoodAnalysisPrompt.java 의 텍스트 블록. 사본을 두면 곧 어긋난다 (face 판과 동일)."""
    raw = re.search(rf'{name} = "{{3}}\n(.*?)"{{3}};', open(PROMPT).read(), re.S)
    if raw is None:
        # USER 는 텍스트 블록이 아니라 한 줄 문자열이다.
        return re.search(rf'{name} = "([^"]+)"', open(PROMPT).read()).group(1)
    return textwrap.dedent(raw.group(1))


def schema():
    return json.loads(block("SCHEMA_JSON"))


def food_detail():
    """OpenAiVisionClient.FOOD_DETAIL — 음식은 low 다. high 로 재면 다른 것을 잰 것이다."""
    return re.search(r'FOOD_DETAIL\s*=\s*"([^"]+)"', open(CLIENT).read()).group(1)


def food_max_tokens():
    return int(re.search(r"MAX_TOKENS\s*=\s*(\d+)", open(CLIENT).read()).group(1))


def spiciness_factors():
    """RuleConstants 의 강도 계수. MEDIUM·UNKNOWN·NONE 은 1.0 (SpicyRednessRule 과 동일)."""
    src = open(CONSTANTS).read()
    mild = float(re.search(r"SPICINESS_MILD_FACTOR\s*=\s*([\d.]+)", src).group(1))
    hot = float(re.search(r"SPICINESS_HOT_FACTOR\s*=\s*([\d.]+)", src).group(1))
    return {"MILD": mild, "HOT": hot, "MEDIUM": 1.0, "UNKNOWN": 1.0, "NONE": 1.0}


def standard_table():
    """StandardFoodTable 의 적재 순서 그대로 — base 먼저, exact 가 덮는다."""
    data = json.load(open(f"{ROOT}/src/main/resources/food/standard-food.json"))
    table = set()
    for row in data.get("base", []) + data.get("exact", []):
        name = row.get("name", "").strip()
        if name:
            table.add(name)
    return table


def standard_hit(table, food_name):
    """StandardFoodTable.find 와 같은 2단계 조회 — 정확한 이름 → 뒤 낱말의 긴 접미사."""
    if not food_name or not food_name.strip():
        return None
    trimmed = food_name.strip()
    if trimmed in table:
        return trimmed
    for word in reversed(trimmed.split()):
        for start in range(len(word)):
            if word[start:] in table:
                return word[start:]
    return None


def call(key, model, system, content, sch, max_tokens):
    """OpenAiVisionClient.call() 과 같은 본문. 모델별 규약 분기도 같다."""
    body = {"model": model,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": content}],
            "response_format": {"type": "json_schema", "json_schema": {
                "name": "food_analysis", "strict": True, "schema": sch}}}
    if "gpt-5" in model:
        body["max_completion_tokens"] = max_tokens
        body["reasoning_effort"] = "low"
    else:
        body["max_tokens"] = max_tokens
        body["temperature"] = 0.2

    request = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions", data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})

    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.load(response)
        choice = payload["choices"][0]
        if choice.get("finish_reason") == "length":
            return {"ok": False, "latency": time.time() - started,
                    "error": "출력 상한에서 잘렸다 — MAX_TOKENS 를 올려야 한다"}
        return {"ok": True, "latency": time.time() - started,
                "data": json.loads(choice["message"]["content"])}
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            message = json.loads(raw).get("error", {}).get("message", "")
        except ValueError:
            message = raw
        return {"ok": False, "latency": time.time() - started,
                "error": f"{e.code} {message[:120]}"}
    except Exception as e:
        return {"ok": False, "latency": time.time() - started, "error": str(e)[:120]}


def content_for(path, user_prompt):
    media = "image/png" if path.endswith(".png") else "image/jpeg"
    return [{"type": "text", "text": user_prompt},
            {"type": "image_url", "image_url": {
                "url": f"data:{media};base64," + base64.b64encode(open(path, "rb").read()).decode(),
                "detail": food_detail()}}]


def main():
    key, system, user_prompt, sch = api_key(), block("SYSTEM"), block("USER"), schema()
    model, max_tokens = deployed_model(), food_max_tokens()
    factors, table = spiciness_factors(), standard_table()

    photos = sorted(f for f in (os.listdir(FOODS) if os.path.isdir(FOODS) else [])
                    if f.lower().endswith((".jpg", ".jpeg", ".png")))
    if not photos:
        sys.exit(f"{FOODS} 안에 음식 사진(jpg/png)이 없다.")
    print(f"음식 {len(photos)}장 × {RUNS}회 → 총 {len(photos) * RUNS} 요청  ({model})\n")

    failures = []
    for photo in photos:
        content = content_for(f"{FOODS}/{photo}", user_prompt)
        rounds = []
        for i in range(RUNS):
            result = call(key, model, system, content, sch, max_tokens)
            if result["ok"]:
                d = result["data"]
                rounds.append(d)
                print(f"  {photo:24} {i + 1}/{RUNS}  {d.get('foodName', '?'):20} "
                      f"매움={d.get('spiciness', '?'):7} 기름={d.get('oiliness', '?'):7} "
                      f"양={d.get('portionSize', '?'):7} {result['latency']:.1f}s")
            else:
                print(f"  {photo:24} {i + 1}/{RUNS}  ✗ {result['error']}")
            time.sleep(2)
        if len(rounds) < 2:
            print(f"  {photo}: 표본 부족 — 판정 불가\n")
            continue

        hits = {standard_hit(table, d.get("foodName")) for d in rounds}
        # 강도는 enum 이 아니라 계수로 비교한다 — MEDIUM 과 UNKNOWN 은 같은 1.0 이라
        # 그 사이의 흔들림은 점수를 안 움직인다. 갈렸다고 잡으면 거짓 경보다.
        spicy_factors = {factors.get(d.get("spiciness", "UNKNOWN"), 1.0) for d in rounds}
        r02_triggers = {bool(d.get("spicy"))
                        or any(i.get("tag") == "CAPSAICIN" for i in d.get("ingredients", []))
                        for d in rounds}
        r07_triggers = {d.get("cookingMethod") == "FRIED" or d.get("oiliness") == "HIGH"
                        for d in rounds}

        problems = []
        if len(hits) > 1:
            problems.append(f"표준 적중 갈림 {sorted(str(h) for h in hits)} — 영양값이 사진과 무관하게 바뀐다")
        if len(spicy_factors) > 1:
            problems.append(f"매운맛 계수 갈림 {sorted(spicy_factors)} — R02 감점이 흔들린다")
        if len(r02_triggers) > 1:
            problems.append("R02 발동 여부 갈림 (spicy/CAPSAICIN)")
        if len(r07_triggers) > 1:
            problems.append("R07 발동 여부 갈림 (FRIED/기름 HIGH)")

        names = sorted({d.get("foodName", "?") for d in rounds})
        portions = sorted({d.get("portionSize", "?") for d in rounds})
        status = "PASS" if not problems else "FAIL"
        print(f"  → {status}  이름 {names} · 적중 {sorted(str(h) for h in hits)} · 양 {portions} (참고)")
        for problem in problems:
            print(f"     ✗ {problem}")
            failures.append(f"{photo}: {problem}")
        print()

    if failures:
        print(f"게이트 실패 {len(failures)}건 — 해당 음식의 특성을 표준 테이블에 심거나 "
              f"(tools/build_standard_food.py) OPENAI_MODEL=gpt-4o 롤백을 검토한다")
        sys.exit(1)
    print("게이트 통과 — 점수에 닿는 세 경로가 전부 안정적이다")


if __name__ == "__main__":
    main()
