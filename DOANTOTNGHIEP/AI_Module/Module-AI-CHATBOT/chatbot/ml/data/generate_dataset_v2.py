"""Sinh dataset synthetic v2 — MÔ PHỎNG VĂN PHONG THẬT (thay cho generate_dataset.py).

Vì sao có bản v2: bộ v1 (`generate_dataset.py`) sinh câu viết chuẩn, tròn trịa
("Cho mình hẹn xem phòng số 1 vào 8h tối mai nhé ạ") trong khi câu người dùng
thật gõ đầy viết tắt và giá dạng khoảng ("E tìm phòng ở đoạn Thái thịnh,
Tc 3tr5-4tr"). Model train trên v1 rồi đi thi trên câu thật bị lệch phân phối:
train_loss ~0.002 nhưng eval_loss đứng im ~1.28.

Toàn bộ từ vựng dưới đây rút từ **115 câu train THẬT** (`intent_train_real.jsonl`)
và `ner_data_md_labeled.jsonl`. TUYỆT ĐỐI KHÔNG rút từ `intent_test_gold.jsonl` —
bịa template theo bộ test sẽ làm số đo đẹp giả (rò rỉ test set).

Xuất (cùng định dạng v1, offset KÝ TỰ):
  intent_train_v2.jsonl: {"text": str, "intent": str}
  ner_train_v2.jsonl:    {"text": str, "entities": [{"start", "end", "label"}]}

## Nhiễu — chỉ dùng phép biến đổi 1 ký tự -> 1 ký tự

Offset entity tính lúc ghép chuỗi (`render`). Nên nhiễu áp SAU đó bắt buộc phải
giữ nguyên độ dài, nếu không offset lệch. Các phép dùng ở đây (bỏ dấu toàn câu,
bỏ dấu lỗ chỗ theo từ, viết hoa/thường cả câu) đều 1-đối-1.
Ngược lại, viết tắt ("tài chính" -> "tc") LÀM ĐỔI độ dài nên phải chọn ở mức
MẢNH (piece), trước khi render — xem các list *_VARIANTS bên dưới.
`main()` có assert đối chiếu lại từng span sau khi áp nhiễu.
"""

import argparse
import json
import random
import re
import unicodedata
from pathlib import Path

OUT_DIR = Path(__file__).parent

# ---------------------------------------------------------------------------
# Từ vựng — rút từ data THẬT (intent_train_real.jsonl, ner_data_md_labeled.jsonl)
# ---------------------------------------------------------------------------

# Giá: người thật viết rất nhiều dạng. Đây là chỗ khác v1 nhiều nhất — v1 chỉ có
# một mốc đơn ("2 triệu", "2tr5"), thật thì quá nửa là KHOẢNG hoặc có dấu "<".
PRICE_SINGLE = [
    "1tr5", "1tr8", "2tr", "2tr2", "2tr5", "2tr7", "2tr8", "3tr", "3tr1", "3tr2",
    "3tr5", "4tr", "4tr5", "5tr", "5tr5", "6tr", "7tr", "8tr",
    "2 triệu", "3 triệu", "4 triệu", "2 triệu rưỡi", "3 triệu rưỡi",
    "3,5tr", "2,5tr", "4,5tr", "3.5tr", "2.5tr",
    "500k", "800k", "1tr2", "2tr3",
    "4m", "5m5", "3m2", "4m5",
    "3.2M", "4.2M", "3.5M",
]
PRICE_RANGE_TPL = [
    "{a}-{b}", "{a} - {b}", "{a}-{b}/tháng", "{a} đến {b}", "{a}~{b}",
]
# Cặp (thấp, cao) hợp lý để ghép khoảng giá
PRICE_PAIRS = [
    ("1tr5", "2tr"), ("2tr", "2tr5"), ("2tr5", "3tr"), ("2tr7", "3tr"),
    ("3tr", "3tr5"), ("3tr5", "4tr"), ("4tr", "4tr5"), ("4tr5", "5tr"),
    ("5tr", "6tr"), ("5", "6tr"), ("6", "8tr"), ("2", "3tr"), ("3", "4tr"),
    ("3.2M", "3.5M"), ("4M", "4.2M"),
]

# Từ khóa ngân sách — thật viết tắt rất nhiều kiểu
BUDGET_WORDS = [
    "tc ", "Tc ", "TC ", "T/c ", "tài chính ", "Tài chính ", "tchinh ",
    "tài chính: ", "budget ", "Budget: ", "ngân sách ", "giá ", "giá phòng ",
    "tầm ", "khoảng ", "gia ", "",
]
# Bộ so sánh đứng trước giá -> nhãn PRICE_MAX
MAX_PREFIX = [
    "dưới ", "<", "< ", "tối đa ", "khoảng ", "tầm ", "max ", "không quá ", "",
]
# Bộ so sánh đứng SAU giá (đặc sản tiếng Việt: "3tr đổ lại")
MAX_SUFFIX = [" đổ lại", " đổ xuống", " trở xuống", " đổ về", ""]
MIN_PREFIX = ["từ ", "trên ", "ít nhất "]

# Địa điểm: v1 chỉ có 12 quận. Thật thì phần lớn là tên PHỐ/PHƯỜNG.
DISTRICTS = [
    "Thanh Xuân", "Cầu Giấy", "Hà Đông", "Đống Đa", "Hai Bà Trưng", "Ba Đình",
    "Hoàng Mai", "Nam Từ Liêm", "Bắc Từ Liêm", "Long Biên", "Tây Hồ", "Hoàn Kiếm",
]
STREETS = [
    "Ngã Tư Sở", "Khương Trung", "Khương Đình", "Kim Giang", "Nguyễn Trãi",
    "Khuất Duy Tiến", "Triều Khúc", "Thái Hà", "Trung Kính", "Hoàng Ngân",
    "Trần Duy Hưng", "Nguyễn Ngọc Vũ", "Láng Hạ", "Giải Phóng", "Trương Định",
    "Khương Thượng", "Quan Nhân", "Vũ Tông Phan", "Cầu Diễn", "Mỹ Đình",
    "Yên Hòa", "Nguyễn Chí Thanh", "Thái Thịnh", "Xuân Thủy", "Phạm Văn Đồng",
    "Định Công", "Kim Mã", "Tôn Thất Tùng", "Chùa Bộc", "Nguyễn Xiển",
    "Lê Văn Lương", "Nguyễn Khang", "Dương Quảng Hàm", "Hồ Tùng Mậu",
]
LOCATIONS = DISTRICTS + STREETS
# Tiền tố khu vực — KHÔNG thuộc span entity
LOC_PREFIX = [
    "ở ", "khu vực ", "kv ", "KV ", "kvuc ", "khvuc ", "quanh ", "quanh khu ",
    "khu ", "gần ", "mạn ", "quận ", "ở khu ", "ở quanh ", "đoạn ", "",
]

# POI: thật dùng viết tắt trường rất nhiều (ptit, ussh, hvnh, FTU...)
POIS = [
    "Đại học Bách Khoa", "đh Bách Khoa", "BKHN", "Đại học Thủy Lợi",
    "đh thủy lợi", "DH thuỷ lợi", "Đại học Thương Mại", "đh thương mại",
    "Đại học Giao thông Vận tải", "đh gtvt", "ĐH GTVT",
    "Đại học Kinh tế Quốc dân", "NEU", "Học viện Ngân hàng", "hvnh",
    "Học viện Bưu chính Viễn thông", "ptit", "PTIT",
    "Đại học Y Hà Nội", "đh y hà nội", "Đại học Ngoại thương", "FTU",
    "Đại học Quốc gia", "ussh", "DAV", "Cao đẳng Y tế Hà Nội",
    "Bệnh viện Bạch Mai", "Bv Bạch Mai", "Times City", "bến xe Mỹ Đình",
    "Đại học Thăng Long", "đh Thăng Long", "Đại học Công nghiệp",
]
POI_PREFIX = ["gần ", "cạnh ", "sát ", "quanh ", "gần khu ", "di chuyển đến ", ""]

# Tiện ích: v1 chỉ có 4 nhóm. Data thật có rất nhiều nhóm khác + viết tắt.
# CẢNH BÁO: các nhóm ngoài 4 nhóm gốc hiện CHƯA có trong UtilityNormalizer phía
# Java (SYNONYMS) -> NER nhận ra span nhưng backend chưa map được giá trị.
# Xem TODO.md mục 6. Cần bổ sung normalizer để khớp end-to-end.
UTILITIES = [
    "điều hòa", "điều hoà", "máy lạnh", "đh", "dh",
    "nóng lạnh", "nl", "bình nóng lạnh",
    "tủ lạnh", "tl",
    "máy giặt", "máy giặt riêng", "máy giặt chung", "mg",
    "vệ sinh khép kín", "vskk", "VSKK", "khép kín", "wc riêng",
    "ban công", "cửa sổ", "cửa sổ thoáng", "gác xép", "thang máy", "máy sấy",
    "chỗ để xe", "chỗ đỗ xe", "bãi xe", "chỗ gửi xe", "chỗ để xe điện",
    "nhận xe điện", "sạc xe điện", "để xe điện",
    "wifi", "mạng", "internet",
    "bếp", "bàn bếp", "chỗ nấu ăn", "khu bếp riêng",
    "giường", "tủ quần áo", "nội thất", "đồ cơ bản", "đầy đủ nội thất",
    "không chung chủ", "giờ giấc tự do", "an ninh tốt", "nuôi pet", "nuôi mèo",
]
UTIL_PREFIX = ["có ", "yc ", "y/c ", "yêu cầu ", "cần ", "ưu tiên ", "", "+ "]

ROOM_TYPES = [
    "phòng trọ", "chung cư mini", "ccmn", "nhà nguyên căn", "phòng khép kín",
    "studio", "trọ", "phòng", "1n1k", "2n1k", "phòng đơn", "nhà trọ",
]
OCCUPANCY = [
    "cho 1 người", "ở 1 mình", "cho 2 người", "cho 2 bạn", "2ng", "3 nữ",
    "cho sinh viên", "ở ghép", "2 người ở", "cho 2 vợ chồng", "1 mình",
]

# Thời điểm vào ở — "quay đầu" là đặc sản của thị trường trọ (phòng sắp trống)
DATETIMES = [
    "quay đầu", "quay đầu 1/8", "quay đầu qua tháng", "đầu tháng 8", "đầu t8",
    "m1/8", "sang tháng 7", "qua tháng", "vào ở luôn", "ở luôn", "cuối tháng",
    "giữa tháng 8", "1/9", "đầu tháng sau", "tuần sau", "cuối tuần này",
    "chiều mai", "sáng mai", "9h sáng mai", "3h chiều mai", "tối nay lúc 7h",
    "10h sáng thứ 5", "chiều thứ 4", "2h chiều thứ 6", "sáng thứ 2", "8h tối mai",
]
AREAS = ["18m2", "20m2", "20 m²", "22m²", "25m2", "30 m²", "15m2", "28m2"]
AREA_PREFIX = ["rộng ", "diện tích ", "rộng khoảng ", "tầm ", "dt "]
RADII = ["bán kính 3km", "bán kính 2km", "<5km", "trong vòng 2km", "bán kính 5km",
         "trong bán kính 1km"]

ROOM_REFS = [
    "phòng số 1", "phòng số 2", "phòng số 3", "phòng đầu tiên", "phòng thứ hai",
    "cái đầu tiên", "phòng 1", "phòng 2", "phòng 3", "phòng này", "căn này",
    "căn A", "căn B", "phòng đầu", "cái này", "phòng trên", "căn đầu tiên",
]

# Đuôi câu — data thật gần như câu nào cũng có
TAILS = [
    " ạ", " ah", " nhé", " nha", " với", " ạ.", "", " ", " nhé ạ", " với ạ",
    " mn giúp e với", " ai có ib em", " ib zalo em", " cmt giúp e ạ",
    " em cảm ơn", " mng giúp em với ạ", "!", ".", " !!", " ???",
]
HEADS = [
    "", "Em ", "em ", "E ", "Mình ", "mình ", "M ", "Cần ", "cần ", "Tìm ",
    "tìm ", "Em cần ", "em cần ", "Mình cần ", "Em muốn ", "em muốn ",
    "Mình muốn ", "Cho em hỏi ", "Bác nào có ", "Ai có ",
]
DECOR = ["", "", "", "", "🏠 ", "✅ ", "📌 ", "#timtro "]

INTENTS = [
    "search_room", "refine_search", "room_detail", "compare_rooms",
    "book_appointment", "out_of_scope",
]

# calculate_cost / policy_inquiry đã gộp vào room_detail (0 và 1 câu thật — không
# đủ để học riêng; bản chất đều là hỏi thông tin một phòng). Template vẫn giữ để
# model gặp đủ kiểu câu hỏi chi phí/quy định, chỉ đổi nhãn đầu ra. Gộp ở bước
# gán nhãn (không bỏ template) nên RNG không đổi -> văn bản sinh ra y hệt cũ.
MERGED_INTENTS = {"calculate_cost": "room_detail", "policy_inquiry": "room_detail"}


# ---------------------------------------------------------------------------
# Nhiễu — mọi phép ở đây PHẢI giữ nguyên độ dài chuỗi
# ---------------------------------------------------------------------------

def strip_diacritics(s: str) -> str:
    """Bỏ dấu tiếng Việt. 1 ký tự -> 1 ký tự nên KHÔNG làm lệch offset."""
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.replace("đ", "d").replace("Đ", "D")
    return unicodedata.normalize("NFC", s)


def partial_strip(s: str, p: float = 0.45) -> str:
    """Bỏ dấu LỖ CHỖ theo từng từ — giống người gõ vội hơn là bỏ dấu cả câu.

    Thật: "Em tìm phòng ở đoạn Thái thịnh" (mất dấu đúng 1 từ).
    v1 chỉ có kiểu bỏ dấu TOÀN câu, không thực tế.
    """
    parts = re.split(r"(\s+)", s)
    return "".join(strip_diacritics(t) if t.strip() and random.random() < p else t
                   for t in parts)


def apply_noise(text: str) -> str:
    original_len = len(text)
    r = random.random()
    if r < 0.26:
        text = partial_strip(text)
    elif r < 0.34:
        text = strip_diacritics(text)
    r2 = random.random()
    if r2 < 0.04:
        text = text.upper()
    elif r2 < 0.10:
        text = text.lower()
    # Bảo hiểm: nếu một phép nào đó đổi độ dài thì offset sẽ sai -> bỏ nhiễu.
    return text if len(text) == original_len else text[:original_len]


# ---------------------------------------------------------------------------
# Ghép mảnh -> (text, entities). Giống v1: biết chính xác offset vì tự ghép.
# ---------------------------------------------------------------------------

def render(parts):
    out, entities, pos = [], [], 0
    for text, label in parts:
        out.append(text)
        if label is not None:
            entities.append({"start": pos, "end": pos + len(text), "label": label})
        pos += len(text)
    return "".join(out), entities


def pick(seq):
    return random.choice(seq)


# --- các mảnh dùng lại giữa nhiều template -----------------------------------

def piece_price_max():
    """Trả list parts cho một cụm giá PRICE_MAX (đơn hoặc khoảng)."""
    budget = pick(BUDGET_WORDS)
    if random.random() < 0.38:  # khoảng giá — thật dùng rất nhiều
        lo, hi = pick(PRICE_PAIRS)
        phrase = pick(PRICE_RANGE_TPL).format(a=lo, b=hi)
        return [(budget, None), (phrase, "PRICE_MAX")]
    prefix, suffix = pick(MAX_PREFIX), pick(MAX_SUFFIX)
    return [(budget, None), (prefix, None), (pick(PRICE_SINGLE), "PRICE_MAX"),
            (suffix, None)]


def piece_price_min():
    return [(pick(MIN_PREFIX), None), (pick(PRICE_SINGLE), "PRICE_MIN")]


def piece_location():
    return [(pick(LOC_PREFIX), None), (pick(LOCATIONS), "LOCATION")]


def piece_poi():
    return [(pick(POI_PREFIX), None), (pick(POIS), "POI")]


def piece_utility(n=1):
    parts, seen = [], set()
    for i in range(n):
        u = pick(UTILITIES)
        if u in seen:
            continue
        seen.add(u)
        parts.append((pick(UTIL_PREFIX) if i == 0 else pick([", ", " ", ", có ", " + "]), None))
        parts.append((u, "UTILITY"))
    return parts


def piece_datetime():
    return [(pick(["", "", "vào ", "chuyển vào ", "hẹn "]), None),
            (pick(DATETIMES), "DATETIME")]


def piece_area():
    return [(pick(AREA_PREFIX), None), (pick(AREAS), "AREA_MIN")]


def piece_radius():
    return [(pick(["", "trong ", ""]), None), (pick(RADII), "RADIUS")]


def piece_room_type():
    return [(pick(ROOM_TYPES), "ROOM_TYPE")]


def piece_room_ref():
    return [(pick(ROOM_REFS), "ROOM_REF")]


# ---------------------------------------------------------------------------
# Template theo intent
# ---------------------------------------------------------------------------

def tpl_search_room():
    """Bài tìm phòng — thật có cả câu 1 dòng lẫn post dài nhiều mệnh đề."""
    head = pick(HEADS)
    # Tranh lap dong tu: HEADS co the da chua "Cần "/"Tìm " roi.
    verbs = ["tìm ", "cần tìm ", "kiếm ", "tìm thuê ", "cần ", ""]
    if any(w in head.lower() for w in ("cần", "tìm", "muốn")):
        verbs = ["tìm ", "kiếm ", "thuê ", ""]
    parts = [(pick(DECOR), None), (head, None), (pick(verbs), None)]
    parts += piece_room_type()

    clauses = []
    if random.random() < 0.55:
        clauses.append([(" ", None), (pick(OCCUPANCY), None)])
    if random.random() < 0.85:
        clauses.append([(" ", None)] + piece_location())
    has_max = random.random() < 0.90
    if has_max:
        clauses.append([(pick([" ", ", ", " "]), None)] + piece_price_max())
    if random.random() < 0.35:
        clauses.append([(", ", None)] + piece_poi())
    if random.random() < 0.65:
        clauses.append([(", ", None)] + piece_utility(random.randint(1, 4)))
    if random.random() < 0.30:
        clauses.append([(", ", None)] + piece_datetime())
    if random.random() < 0.18:
        clauses.append([(", ", None)] + piece_area())
    if random.random() < 0.12:
        clauses.append([(", ", None)] + piece_radius())
    # PRICE_MIN chi xuat hien khi KHONG co PRICE_MAX — neu khong se sinh cau vo ly
    # kieu "tu 5tr5 ... duoi 2tr3". Data that gan nhu chi neu tran gia.
    if not has_max and random.random() < 0.45:
        clauses.append([(", ", None)] + piece_price_min())

    random.shuffle(clauses)
    for c in clauses:
        parts += c
    parts.append((pick(TAILS), None))
    return parts, "search_room"


def tpl_refine_search():
    v = random.randint(0, 6)
    head = pick(["", "em ", "mình ", "e ", "cho em "])
    if v == 0:
        return [(head, None), (pick(["tăng được lên ", "nâng lên ", "lên "]), None)] \
            + [(pick(PRICE_SINGLE), "PRICE_MAX")] + [(pick(TAILS), None)], "refine_search"
    if v == 1:
        return [(head, None), (pick(["đổi sang ", "chuyển sang tìm ", "tìm lại "]), None)] \
            + piece_location() + [(pick(TAILS), None)], "refine_search"
    if v == 2:
        return [(head, None), ("rẻ hơn nữa đi", None), (pick(TAILS), None)], "refine_search"
    if v == 3:
        return [(head, None), (pick(["giảm xuống ", "hạ xuống ", "bớt xuống "]), None)] \
            + piece_price_max() + [(pick(TAILS), None)], "refine_search"
    if v == 4:
        return [(head, None), (pick(["thêm ", "cần thêm ", "muốn có thêm "]), None)] \
            + piece_utility(random.randint(1, 2)) + [(pick(TAILS), None)], "refine_search"
    if v == 5:
        return [(head, None), ("chỉ tìm quanh ", None)] + [(pick(LOCATIONS), "LOCATION")] \
            + [(", ", None)] + piece_radius() + [(pick(TAILS), None)], "refine_search"
    return [(head, None), (pick(["bỏ điều kiện ", "không cần ", "thôi không cần "]), None)] \
        + [(pick(UTILITIES), "UTILITY")] + [(pick(TAILS), None)], "refine_search"


def tpl_room_detail():
    v = random.randint(0, 5)
    head = pick(["", "cho em xem ", "em xem ", "cho mình hỏi ", "e hỏi "])
    if v == 0:
        return [(head, None)] + piece_room_ref() \
            + [(pick([" thế nào", " ra sao", " chi tiết", " kỹ hơn"]), None),
               (pick(TAILS), None)], "room_detail"
    if v == 1:
        return [(head, None)] + piece_room_ref() + [(" có ", None)] \
            + [(pick(UTILITIES), "UTILITY")] + [(" không", None), (pick(TAILS), None)], "room_detail"
    if v == 2:
        return [(head, None), ("phòng khoảng bao nhiêu mét", None), (pick(TAILS), None)], "room_detail"
    if v == 3:
        return [(head, None)] + piece_room_ref() + [(" còn trống không", None),
                                                    (pick(TAILS), None)], "room_detail"
    if v == 4:
        return [(head, None), ("cho xin thêm ảnh ", None)] + piece_room_ref() \
            + [(pick(TAILS), None)], "room_detail"
    return [(head, None), ("địa chỉ cụ thể của ", None)] + piece_room_ref() \
        + [(" ở đâu", None), (pick(TAILS), None)], "room_detail"


def tpl_compare_rooms():
    a, b = random.sample(ROOM_REFS, 2)
    v = random.randint(0, 3)
    if v == 0:
        return [("so sánh ", None), (a, "ROOM_REF"), (" với ", None), (b, "ROOM_REF"),
                (pick([" cái nào hơn", " bên nào tốt hơn", " nên chọn cái nào"]), None),
                (pick(TAILS), None)], "compare_rooms"
    if v == 1:
        return [("giữa ", None), (a, "ROOM_REF"), (" và ", None), (b, "ROOM_REF"),
                (" thì nên lấy cái nào", None), (pick(TAILS), None)], "compare_rooms"
    if v == 2:
        return [(a, "ROOM_REF"), (" với ", None), (b, "ROOM_REF"),
                (" khác nhau chỗ nào", None), (pick(TAILS), None)], "compare_rooms"
    return [("theo mn thêm ", None), (pick(PRICE_SINGLE), "PRICE_MAX"), (" lấy ", None),
            (a, "ROOM_REF"), (" có đáng không", None), (pick(TAILS), None)], "compare_rooms"


def tpl_book_appointment():
    v = random.randint(0, 3)
    if v == 0:
        return [(pick(["cho em hẹn xem ", "em muốn xem ", "cho mình hẹn xem "]), None)] \
            + piece_room_ref() + [(" ", None)] + piece_datetime() \
            + [(pick(TAILS), None)], "book_appointment"
    if v == 1:
        return [("em đặt lịch xem phòng ", None)] + piece_datetime() \
            + [(" được không", None), (pick(TAILS), None)], "book_appointment"
    if v == 2:
        return [("hẹn ", None)] + piece_datetime() + [(" em qua xem ", None)] \
            + piece_room_ref() + [(pick(TAILS), None)], "book_appointment"
    return [("mai em ghé xem ", None)] + piece_room_ref() + [(" lúc ", None)] \
        + [(pick(DATETIMES), "DATETIME")] + [(pick(TAILS), None)], "book_appointment"


def tpl_calculate_cost():
    v = random.randint(0, 4)
    if v == 0:
        return [(pick(["tính giúp em ", "cho em hỏi ", "tính hộ mình "]), None),
                ("tổng chi phí hàng tháng của ", None)] + piece_room_ref() \
            + [(pick(TAILS), None)], "calculate_cost"
    if v == 1:
        return [("ở ", None)] + piece_room_ref() \
            + [(" thì một tháng hết bao nhiêu tiền", None), (pick(TAILS), None)], "calculate_cost"
    if v == 2:
        return [("tiền điện nước của ", None)] + piece_room_ref() \
            + [(" tính thế nào", None), (pick(TAILS), None)], "calculate_cost"
    if v == 3:
        return [("cộng cả phí dịch vụ thì ", None)] + piece_room_ref() \
            + [(" tầm bao nhiêu 1 tháng", None), (pick(TAILS), None)], "calculate_cost"
    return [("em ở 2 người thì ", None)] + piece_room_ref() \
        + [(" tổng hết bao nhiêu", None), (pick(TAILS), None)], "calculate_cost"


def tpl_policy_inquiry():
    q = pick([
        "đặt cọc mấy tháng", "hợp đồng ký tối thiểu bao lâu", "có phải cọc trước không",
        "quy trình ký hợp đồng thế nào", "muốn gia hạn hợp đồng thì làm sao",
        "trả phòng trước hạn có mất cọc không", "tiền cọc có được trả lại không",
        "có cần giấy tờ gì để ký hợp đồng không", "chính sách hủy phòng thế nào",
        "nuôi thú cưng có được không", "có được đăng ký tạm trú không",
    ])
    return [(pick(["cho em hỏi ", "cho hỏi ", "em hỏi chút ", ""]), None), (q, None),
            (pick(TAILS), None)], "policy_inquiry"


def tpl_out_of_scope():
    q = pick([
        "hôm nay trời đẹp nhỉ", "bạn tên gì thế", "kể chuyện cười đi",
        "mình vẫn còn hợp đồng nhà tí nữa", "giá vàng hôm nay bao nhiêu",
        "ăn trưa chưa", "cho xin số điện thoại chủ nhà cái", "haha đúng rồi",
        "thời tiết mai thế nào", "bạn có người yêu chưa", "chán quá đi mất",
        "ok thanks", "để mình suy nghĩ đã", "xuất hiện muộn tí ngon rồi",
    ])
    return [(q, None), (pick(TAILS), None)], "out_of_scope"


BUILDERS = [
    (tpl_search_room, 0.42),
    (tpl_refine_search, 0.13),
    (tpl_room_detail, 0.11),
    (tpl_compare_rooms, 0.08),
    (tpl_book_appointment, 0.09),
    (tpl_calculate_cost, 0.07),
    (tpl_policy_inquiry, 0.06),
    (tpl_out_of_scope, 0.04),
]


def generate_one():
    builders, weights = zip(*BUILDERS)
    builder = random.choices(builders, weights=weights, k=1)[0]
    parts, intent = builder()
    intent = MERGED_INTENTS.get(intent, intent)
    text, entities = render(parts)
    text = apply_noise(text)
    # Bỏ khoảng trắng thừa ở 2 đầu KHÔNG được: sẽ lệch offset. Chỉ chuẩn hóa
    # bằng cách bỏ qua câu rác.
    return text, entities, intent


def verify(text, entities):
    """Mọi span phải nằm trong chuỗi và không rỗng sau khi áp nhiễu."""
    for e in entities:
        if not (0 <= e["start"] < e["end"] <= len(text)):
            return False
        if not text[e["start"]:e["end"]].strip():
            return False
    return True


def write_jsonl(path, rows):
    with path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def main(n_intent, n_ner, seed, suffix):
    random.seed(seed)
    intent_rows, ner_rows = [], []
    seen = set()
    guard = 0
    while len(intent_rows) < n_intent or len(ner_rows) < n_ner:
        guard += 1
        if guard > (n_intent + n_ner) * 60:
            raise RuntimeError("Khong sinh du cau duy nhat — noi rong tu vung/template.")
        text, entities, intent = generate_one()
        if len(text.strip()) < 6 or text in seen:
            continue
        if not verify(text, entities):
            continue
        seen.add(text)
        if len(intent_rows) < n_intent:
            intent_rows.append({"text": text, "intent": intent})
        if len(ner_rows) < n_ner and entities:
            ner_rows.append({"text": text, "entities": entities})

    ip = OUT_DIR / f"intent_train{suffix}.jsonl"
    np_ = OUT_DIR / f"ner_train{suffix}.jsonl"
    write_jsonl(ip, intent_rows)
    write_jsonl(np_, ner_rows)

    from collections import Counter
    ic = Counter(r["intent"] for r in intent_rows)
    ec = Counter(e["label"] for r in ner_rows for e in r["entities"])
    print(f"{ip.name}: {len(intent_rows)} cau")
    for k, v in ic.most_common():
        print(f"   {k:18s} {v}")
    print(f"{np_.name}: {len(ner_rows)} cau | {sum(ec.values())} entity")
    for k, v in ec.most_common():
        print(f"   {k:12s} {v}")


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--n-intent", type=int, default=2500)
    ap.add_argument("--n-ner", type=int, default=2075)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--suffix", default="_v2",
                    help="hau to file xuat (mac dinh _v2 de khong de len bo v1)")
    a = ap.parse_args()
    main(a.n_intent, a.n_ner, a.seed, a.suffix)
