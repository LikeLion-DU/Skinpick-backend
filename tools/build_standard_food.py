"""공공데이터 '전국통합식품영양성분정보(음식)표준데이터' → 앱이 쓰는 표준 영양 JSON.

원본은 그대로 못 쓴다. 세 가지를 여기서 해결한다.

  1. 기준량이 100g/100ml 이다. `1인(회)분량 참고량` 컬럼은 전 행이 비어 있어서
     `식품중량` 으로 1인분을 환산한다. 이걸 안 하면 룰 엔진이 나트륨을 1500mg 과
     비교할 때 거의 모든 음식이 "안전" 으로 나온다.
     중량이 100 이하인 행은 1인분 정보가 아니라 기준량을 그대로 적은 것이므로 뺀다.
  2. 같은 음식이 여러 행이고 생성 방법이 섞여 있다. 실제로 대조해 보면 차이가 크다.

         라면     분석·수집 2112mg   산출 462mg   (실제 ~1800mg)
         된장찌개  분석·수집 1291mg   산출 842mg   (실제 ~1300mg)

     '산출' 은 레시피 기반 계산값이라 국물까지 부피로 잡아 농도가 희석된다.
     그래서 **분석·수집(실측)을 먼저 쓰고, 없을 때만 산출로 떨어진다.**
     같은 등급 안에서는 평균 대신 중앙값을 쓴다 — 이상치에 끌려가지 않는다.
  3. 조리법·매운맛·재료 태그가 원본에 없다. 룰 엔진의 절반이 이 셋을 보므로
     식품명에서 규칙으로 뽑는다 — 원본 이름이 `김치찌개_돼지고기_두부` 처럼
     재료를 품고 있어서 가능하다.

출력은 2단계 조회를 위해 두 벌이다.
  exact : `김치찌개_참치` 처럼 재료까지 맞는 이름
  base  : `김치찌개` 기본명(같은 기본명 행들의 중앙값)

실행:
  python tools/build_standard_food.py <csv경로> <출력 json경로>
"""
import csv
import io
import json
import re
import statistics
import sys
from collections import defaultdict

# 요리 계열만 남긴다. 원본 19,495건 중 74%가 빵·과자·음료(프랜차이즈 제품)라
# 사진으로 찍어 상극 분석을 할 대상이 아니다.
DISH_CATEGORIES = {
    '국 및 탕류', '생채·무침류', '볶음류', '튀김류', '밥류', '면 및 만두류',
    '찌개 및 전골류', '구이류', '나물·숙채류', '조림류', '찜류',
    '전·적 및 부침류', '김치류',
}

# 위에 있을수록 우선한다 — 첫 일치에서 멈춘다.
#
# 룰 엔진이 실제로 보는 건 둘뿐이다. FRIED(R07 피지)와 BOILED(국물 절반 남기기).
# 나머지는 화면 표기용이라 대충 맞으면 된다. 그래서 애매한 것은 FRIED 로
# 올리지 않는다 — 잘못 올리면 지성 피부에 없던 감점이 생긴다.
#
# `$` 로 끝나는 낱말은 **기본명의 끝에서만** 본다. 부분일치로 두면 낱자 하나가
# 다른 음식을 통째로 끌고 온다 — '전' 을 그냥 두면 전골·전복탕·전어구이가 전부
# FRIED 가 되어, AI 가 맞게 본 BOILED 를 덮어쓰고 없던 R07 감점이 생긴다.
COOKING_RULES = [
    ('FRIED', ['튀김', '까스', '가스', '강정', '탕수', '프라이', '후라이', '전$']),
    ('BOILED', ['국', '탕', '찌개', '전골', '죽', '면', '스프', '수프', '조림', '라면']),
    ('STEAMED', ['찜', '수육', '만두']),
    ('GRILLED', ['구이', '볶', '부침', '적', '갈비', '스테이크']),
    ('RAW', ['생채', '무침', '샐러드', '회', '쌈']),
]

# 매운맛. 룰 R02(홍조)의 조건이다.
# 주의 — 서버는 `spicy || CAPSAICIN 태그` 로 판정하므로 아래 목록과
# TAG_RULES 의 CAPSAICIN 이 사실상 합쳐진다. 한쪽만 넣어도 발동한다.
SPICY_WORDS = ['매운', '매콤', '불닭', '고추', '청양', '떡볶이', '제육',
               '아귀찜', '짬뽕', '육개장', '김치찌개', '김치볶음', '닭갈비',
               '쭈꾸미', '주꾸미', '낙지볶음', '비빔']

# '고추' 가 들어가도 맵지 않은 것들. 매운맛·CAPSAICIN 판정 전에 이름에서 지운다.
# 풋고추는 고명이지 매운맛이 아닌데, 부분일치라 풋고추찜·김밥_풋고추까지 매운 음식이
# 된다 — 홍조가 높은 사용자에게 없던 R02 감점이 확정으로 붙는다.
MILD_WORDS = ['풋고추', '고추냉이']

# 공공데이터에 없거나, 원본 항목이 시연에서 말하는 음식과 다른 시연 음식.
# 시연 3종(김치찌개·연어구이·라면) 중 둘이 여기 걸린다 —
#   연어구이: 원본에 없다(음식 DB 에는 연어롤뿐이다).
#   돼지고기 김치찌개: 원본의 `김치찌개` 는 고기 없는 기본형이라 단백질 15.1g 이다.
#     시연 음식은 돼지고기가 들어간 쪽이고, 그 차이가 R05(단백질 20g 이상 가점)를
#     가른다. 기본형에 얹혀 가면 무대에서 말할 60점이 54점이 된다.
# 조리법·매운맛·태그는 같은 이름 규칙으로 뽑는다.
MANUAL_FOODS = [
    {'name': '연어구이', 'caloriesKcal': 610, 'proteinG': 32.0, 'fatG': 28.0,
     'carbG': 45.0, 'sodiumMg': 1600, 'sugarG': 4.0},
    {'name': '돼지고기 김치찌개', 'caloriesKcal': 520, 'proteinG': 28.5, 'fatG': 24.0,
     'carbG': 32.0, 'sodiumMg': 1850, 'sugarG': 6.2},
]

# 재료 태그. 이름에 등장하면 붙인다.
TAG_RULES = [
    ('PROBIOTIC', ['김치', '된장', '고추장', '청국장', '요거트', '요구르트', '낫토']),
    ('CAPSAICIN', ['고추', '청양', '매운', '매콤', '불닭', '떡볶이', '짬뽕', '육개장']),
    ('OMEGA3', ['연어', '고등어', '삼치', '꽁치', '참치', '정어리', '방어', '멸치', '들기름']),
    ('VITAMIN_C', ['파프리카', '브로콜리', '키위', '피망', '딸기', '귤', '오렌지', '레몬']),
    ('VITAMIN_A', ['당근', '시금치', '단호박', '부추', '깻잎']),
    ('ANTIOXIDANT', ['토마토', '녹차', '블루베리', '베리', '가지', '양파']),
    ('DAIRY', ['우유', '치즈', '크림', '버터', '요거트', '요구르트']),
    ('GLUTEN', ['밀가루', '빵', '面', '국수', '라면', '우동', '파스타', '스파게티', '만두', '수제비']),
    ('CAFFEINE', ['커피', '홍차', '녹차', '콜라']),
    ('ALCOHOL', ['소주', '맥주', '막걸리', '와인', '청주']),
    ('HIGH_GI', ['흰쌀', '백미', '떡', '설탕', '시럽', '감자', '옥수수']),
]

NUTRIENT_COLUMNS = {
    'caloriesKcal': '에너지(kcal)',
    'proteinG': '단백질(g)',
    'fatG': '지방(g)',
    'carbG': '탄수화물(g)',
    'sodiumMg': '나트륨(mg)',
    'sugarG': '당류(g)',
}


def serving_grams(row):
    """`식품중량` 은 '300g' · '240ml' · '201.7' 처럼 단위가 뒤죽박죽이다.
    숫자만 뽑는다 — 100g 과 100ml 을 구분해 봐야 액체 밀도를 모르므로 의미가 없고,
    찌개 국물은 실제로 물에 가깝다.

    100 이하는 버린다. 그 행들은 1인분을 적은 게 아니라 기준량(100g)을 그대로
    옮겨 둔 것이라, 환산해도 1인분이 되지 않는다."""
    match = re.match(r'^\s*([\d.]+)', row.get('식품중량') or '')
    if not match:
        return None
    grams = float(match.group(1))
    # 4kg 짜리 급식 단위는 1인분이 아니다.
    return grams if 100 < grams <= 2000 else None


def is_measured(row):
    """실측('분석'·'수집')인가. '산출'은 레시피 계산이라 농도가 낮게 나온다."""
    return (row.get('데이터생성방법명') or '').strip() != '산출'


def to_serving(row):
    """100g 기준 값을 1인분으로 환산한다. 값이 없는 항목은 뺀다(0 으로 채우면
    '지방 0g' 이라는 거짓이 된다)."""
    grams = serving_grams(row)
    if grams is None:
        return None

    ratio = grams / 100.0
    result = {}
    for key, column in NUTRIENT_COLUMNS.items():
        raw = (row.get(column) or '').strip()
        if not raw:
            continue
        try:
            result[key] = float(raw) * ratio
        except ValueError:
            continue
    # 에너지와 나트륨이 없으면 룰 엔진에 쓸모가 없다.
    if 'caloriesKcal' not in result or 'sodiumMg' not in result:
        return None
    return result


def clean_name(raw):
    """`달걀부침(달걀후라이)_햄` → `달걀부침(달걀후라이)_햄` 은 그대로 두고
    앞뒤 공백만 정리한다. 괄호 안 설명은 매칭에 도움이 되므로 남긴다."""
    return re.sub(r'\s+', ' ', (raw or '')).strip()


def base_name(name):
    """밑줄 앞이 기본 음식명이다 — `김치찌개_돼지고기_두부` → `김치찌개`."""
    return name.split('_')[0].strip()


def cooking_method(name):
    # 끝 낱말 판정은 기본명에서 본다 — `해물파전_오징어` 의 정체는 파전이다.
    base = base_name(name)
    for method, words in COOKING_RULES:
        for word in words:
            if word.endswith('$'):
                if base.endswith(word[:-1]):
                    return method
            elif word in name:
                return method
    return 'ETC'


def spice_name(name):
    """매운맛 판정용 이름. 안 매운 '고추'(풋고추 등)를 지운 뒤에 본다."""
    for word in MILD_WORDS:
        name = name.replace(word, '')
    return name


def is_spicy(name):
    return any(word in spice_name(name) for word in SPICY_WORDS)


def tags(name):
    # CAPSAICIN 도 '고추' 부분일치라 매운맛과 같은 이름으로 봐야 한다. 한쪽만 고치면
    # 서버의 `spicy || CAPSAICIN` 판정에서 풋고추가 여전히 매운 음식이 된다.
    spiced = spice_name(name)
    found = [tag for tag, words in TAG_RULES
             if any(word in (spiced if tag == 'CAPSAICIN' else name) for word in words)]
    return found or ['ETC']


def median_of(entries, key):
    values = [entry[key] for entry in entries if key in entry]
    return round(statistics.median(values), 1) if values else None


def aggregate(name, measured, computed):
    """같은 이름의 여러 행을 한 건으로 접는다.

    실측이 하나라도 있으면 실측만 쓴다. 둘을 섞어 중앙값을 내면 라면이
    2112mg 과 462mg 사이 어딘가로 떨어져 어느 쪽도 아닌 값이 된다."""
    entries = measured or computed
    record = {
        'name': name,
        'sampleCount': len(entries),
        'measured': bool(measured),
    }
    for key in NUTRIENT_COLUMNS:
        value = median_of(entries, key)
        if value is not None:
            # 칼로리·나트륨은 정수로 쓴다. 소수점은 화면에서 의미가 없다.
            record[key] = int(round(value)) if key in ('caloriesKcal', 'sodiumMg') else value
    record['cookingMethod'] = cooking_method(name)
    record['spicy'] = is_spicy(name)
    record['tags'] = tags(name)
    return record


def main(csv_path, json_path):
    with io.open(csv_path, encoding='cp949', newline='') as handle:
        rows = list(csv.DictReader(handle))

    dishes = [row for row in rows if row.get('식품대분류명') in DISH_CATEGORIES]

    # 이름 → (실측 목록, 산출 목록)
    exact_groups = defaultdict(lambda: ([], []))
    base_groups = defaultdict(lambda: ([], []))

    for row in dishes:
        nutrition = to_serving(row)
        if nutrition is None:
            continue
        name = clean_name(row.get('식품명'))
        if not name:
            continue
        slot = 0 if is_measured(row) else 1
        exact_groups[name][slot].append(nutrition)
        base_groups[base_name(name)][slot].append(nutrition)

    exact = [aggregate(name, *buckets) for name, buckets in sorted(exact_groups.items())]
    base = [aggregate(name, *buckets) for name, buckets in sorted(base_groups.items())]

    # 수동 보충은 공공데이터가 이긴다 — 언젠가 원본에 연어구이가 생기면 그쪽을 쓴다.
    covered = {record['name'] for record in exact}
    for manual in MANUAL_FOODS:
        if manual['name'] in covered:
            continue
        record = dict(manual)
        record['sampleCount'] = 1
        record['measured'] = False
        record['cookingMethod'] = cooking_method(record['name'])
        record['spicy'] = is_spicy(record['name'])
        record['tags'] = tags(record['name'])
        exact.append(record)

    # 기본명이 exact 에도 똑같이 있으면 중복이다. exact 쪽이 더 구체적이므로 남기고
    # base 에서는 뺀다 — 조회는 exact 를 먼저 보기 때문에 결과가 같다.
    exact_names = {record['name'] for record in exact}
    base = [record for record in base if record['name'] not in exact_names]

    payload = {
        'source': '전국통합식품영양성분정보(음식)표준데이터 · 식품의약품안전처',
        'basis': '1인분(식품중량 기준 환산) · 실측 우선 · 같은 이름은 중앙값',
        'exact': exact,
        'base': base,
    }

    with io.open(json_path, 'w', encoding='utf-8') as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(',', ':'))

    measured = sum(1 for r in exact + base if r['measured'])
    print(f'원본 {len(rows)}건 → 요리 {len(dishes)}건')
    print(f'exact {len(exact)}종 · base {len(base)}종')
    print(f'실측 기반 {measured}종 / 산출 기반 {len(exact) + len(base) - measured}종')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
