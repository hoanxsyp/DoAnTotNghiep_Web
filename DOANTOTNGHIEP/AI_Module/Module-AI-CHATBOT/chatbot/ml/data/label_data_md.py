"""Đánh nhãn bán tự động cho data.md (dữ liệu THẬT — bài đăng tìm phòng).

- Mỗi bài đăng -> 1 câu, intent = "search_room".
- NER: gazetteer (LOCATION/POI/UTILITY) + regex (PRICE_MAX/AREA_MIN/DATETIME).
  Offset ký tự do finditer tính nên luôn khớp; có assert text[s:e]==span.
  Chồng lấn: ưu tiên span DÀI hơn, rồi theo priority (AREA<PRICE<DATETIME<POI<LOCATION<UTILITY).

Xuất:
  intent_data_md_labeled.jsonl : {"text", "intent"}
  ner_data_md_labeled.jsonl    : {"text", "entities":[{start,end,label}]}
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE.parent / "data.md"   # ml/data.md

# ------------------------------------------------------------------ gazetteers
# Longest-first không cần tự sắp — greedy đã ưu tiên span dài hơn.
LOCATIONS = [
    "Nguyễn Chí Thanh", "Trần Duy Hưng", "Khuất Duy Tiến", "Nguyễn Trãi",
    "Vũ Tông Phan", "Nguyễn Thị Định", "Nguyễn Ngọc Vũ", "Hoàng Ngân",
    "Trung Kính", "Thái Hà", "Láng Hạ", "Giải Phóng", "Cầu Diễn", "Mỹ Đình",
    "Nam Từ Liêm", "Bắc Từ Liêm", "Cầu Giấy", "Trương Định", "Hai Bà Trưng",
    "Khương Đình", "Khương Trung", "Khương Thượng", "Kim Giang", "Quan Nhân",
    "Nguyễn Xiển", "Ngã Tư Sở", "Ngã tư sở", "Tây Sơn", "Đống Đa", "Đống đa",
    "Thanh Xuân", "Ba Đình", "Yên Hoà", "Yên Hòa", "Triều Khúc", "Tây Hồ",
    "Hoàng Mai", "Long Biên", "Hoàn Kiếm", "Hà Đông", "Láng",
    # biến thể không dấu / viết tắt gặp trong data thật
    "thanh xuan", "Khuong Trung", "Thái thịnh", "ng thị định",
]

POIS = [
    "đại học công nghệ giao thông vận tải", "đh giao thông vận tải",
    "đại học giao thông vận tải", "cao đẳng y tế Hà Nội",
    "Trường Cao đẳng Bách Khoa", "cao đẳng Bách Khoa", "đại học y hà nội",
    "đh Thăng Long", "đại học Thăng Long", "đh thương mại", "đại học thương mại",
    "Bv Bạch mai", "bệnh viện Bạch Mai", "Bạch mai",
    "Dh thuỷ lợi", "DH thuỷ lợi", "đh thủy lợi", "đại học thuỷ lợi",
    "thuỷ lợi", "thủy lợi",
    "PTIT", "ptit", "ussh", "hvnh", "FTU", "DAV",
]

UTILITIES = [
    "vệ sinh khép kín", "vskk", "vskk", "khép kín",
    "điều hoà", "điều hòa", "máy lạnh",
    "nóng lạnh", "máy giặt", "máy sấy", "tủ lạnh", "tủ quần áo",
    "thang máy", "ban công", "cửa sổ", "gác xép", "bàn bếp", "bàn làm việc",
    "chỗ để xe", "chỗ đỗ xe", "chỗ gửi xe", "chỗ nấu ăn", "bãi xe",
    "xe điện", "wifi", "internet", "nội thất", "pccc", "thoát hiểm",
    # viết tắt — chỉ khớp khi đứng riêng (\b), POI/địa danh được match trước
    "nl", "tl", "dh", "đh",
]

# ------------------------------------------------------------------ regex spans
RE_AREA = re.compile(r"\d+\s?(?:m²|m2|mét vuông|mét)", re.IGNORECASE)
# unit + \d? tuỳ chọn, rồi negative-lookahead chặn chữ cái theo sau
# -> tránh khớp nhầm "1 mình" (=1 m), "3 mét"... nhưng vẫn nhận "4m", "5m5".
_MONEY = r"\d+(?:[.,]\d+)?\s?(?:triệu|tr\d?|củ|[mM]\d?)(?![A-Za-zÀ-ỹ])"
# Quy ước span giá khớp bộ synthetic v2 (ner_train_v2.jsonl) mà model học:
#   - khoảng "2tr5-3tr", "6-8tr", "4tr đến 4tr5" -> MỘT span PRICE_MAX cả khoảng
#     (normalizer phía Java tách min/max), số đầu được phép thiếu đơn vị;
#   - đuôi "/tháng" dính liền giá nằm TRONG span ("4tr-4tr5/tháng").
RE_MONEY = re.compile(
    r"(?:\d+(?:[.,]\d+)?\s?(?:triệu|tr\d?|củ|[mM]\d?)?\s*(?:[-–—~]|đến)\s*)?"
    + _MONEY + r"(?:\s*/\s*(?:1\s*)?th[áa]ng)?", re.IGNORECASE)
RE_DATE = [
    re.compile(r"m?\d{1,2}\s*/\s*\d{1,2}"),
    re.compile(r"đầu t\d{1,2}", re.IGNORECASE),
    re.compile(r"đầu tháng \d{1,2}", re.IGNORECASE),
    re.compile(r"sang tháng \d{1,2}", re.IGNORECASE),
    re.compile(r"qua tháng", re.IGNORECASE),
    # Như v2: "quay đầu", "ở luôn" luôn là DATETIME; ghép với mốc theo sau
    # ("quay đầu 1/8", "quay đầu qua tháng") thành 1 span.
    re.compile(r"quay đầu(?:\s+(?:m?\d{1,2}\s*/\s*\d{1,2}|qua tháng))?", re.IGNORECASE),
    re.compile(r"(?:vào\s+)?ở luôn", re.IGNORECASE),
    re.compile(r"(?:đầu|cuối|giữa) tháng (?:sau|này|\d{1,2})", re.IGNORECASE),
    re.compile(r"cuối tháng|tuần sau|cuối tuần(?: này)?", re.IGNORECASE),
]

# ROOM_TYPE theo quy ước v2: loại phòng không mơ hồ gán ở mọi vị trí; cụm bắt đầu
# bằng "phòng" chỉ gán khi đứng sau động từ tìm/thuê ("tìm phòng" -> ROOM_TYPE,
# còn "xem phòng"/"giá phòng" thì không) — đúng như phân bố trong ner_train_v2.
ROOM_TYPES = [
    "chung cư mini", "ccmn", "studio", "2n1k", "1n1k", "nhà nguyên căn", "nhà trọ", "trọ",
]
ROOM_TYPES_PHONG = ["phòng khép kín", "phòng trọ", "phòng đơn", "phòng"]
SEARCH_VERBS = {"tìm", "thuê", "kiếm", "cần", "muốn", "có", "tim", "thue", "kiem", "can"}

PRIORITY = {"AREA_MIN": 0, "PRICE_MAX": 1, "DATETIME": 2,
            "POI": 3, "LOCATION": 4, "ROOM_TYPE": 5, "UTILITY": 6}


def gaz_spans(text, phrases, label):
    out = []
    for p in phrases:
        for m in re.finditer(r"(?<!\w)" + re.escape(p) + r"(?!\w)", text, re.IGNORECASE):
            out.append((m.start(), m.end(), label))
    return out


def price_spans(text):
    # Khoảng "A-B" đã nằm gọn trong 1 match của RE_MONEY (xem quy ước ở trên).
    return [(m.start(), m.end(), "PRICE_MAX") for m in RE_MONEY.finditer(text)]


def date_spans(text):
    out = []
    for r in RE_DATE:
        for m in r.finditer(text):
            out.append((m.start(), m.end(), "DATETIME"))
    return out


def room_type_spans(text):
    out = gaz_spans(text, ROOM_TYPES, "ROOM_TYPE")
    for s, e, lab in gaz_spans(text, ROOM_TYPES_PHONG, "ROOM_TYPE"):
        prev = text[:s].split()[-1:]
        if prev and prev[0].lower() in SEARCH_VERBS:
            out.append((s, e, lab))
    return out


def label_post(text):
    cands = []
    cands += [(m.start(), m.end(), "AREA_MIN") for m in RE_AREA.finditer(text)]
    cands += price_spans(text)
    cands += date_spans(text)
    cands += gaz_spans(text, POIS, "POI")
    cands += gaz_spans(text, LOCATIONS, "LOCATION")
    cands += room_type_spans(text)
    cands += gaz_spans(text, UTILITIES, "UTILITY")

    # greedy: leftmost, dài nhất, rồi priority; bỏ span chồng lấn
    cands.sort(key=lambda c: (c[0], -(c[1] - c[0]), PRIORITY[c[2]]))
    accepted = []
    for s, e, lab in cands:
        if all(e <= a[0] or s >= a[1] for a in accepted):
            accepted.append((s, e, lab))
    accepted.sort()
    ents = []
    for s, e, lab in accepted:
        assert text[s:e] != "" and text[s:e]
        ents.append({"start": s, "end": e, "label": lab})
    return ents


def load_posts():
    raw = SRC.read_text(encoding="utf-8").splitlines()
    posts, cur = [], []
    for line in raw:
        if line.strip():
            cur.append(line.strip())
        elif cur:
            posts.append(" ".join(cur))
            cur = []
    if cur:
        posts.append(" ".join(cur))
    return posts


def main():
    posts = load_posts()
    intent_rows, ner_rows = [], []
    for p in posts:
        intent_rows.append({"text": p, "intent": "search_room"})
        ents = label_post(p)
        for en in ents:                                  # sanity offset
            assert p[en["start"]:en["end"]], (p, en)
        ner_rows.append({"text": p, "entities": ents})

    (HERE / "intent_data_md_labeled.jsonl").write_text(
        "\n".join(json.dumps(r, ensure_ascii=False) for r in intent_rows) + "\n",
        encoding="utf-8")
    (HERE / "ner_data_md_labeled.jsonl").write_text(
        "\n".join(json.dumps(r, ensure_ascii=False) for r in ner_rows) + "\n",
        encoding="utf-8")

    from collections import Counter
    c = Counter(e["label"] for r in ner_rows for e in r["entities"])
    print(f"posts: {len(posts)}")
    print("entities:", dict(c))
    empty = sum(1 for r in ner_rows if not r["entities"])
    print("posts khong co entity:", empty)


if __name__ == "__main__":
    main()
