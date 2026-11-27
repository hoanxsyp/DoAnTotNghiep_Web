"""Build the synthetic 500-message NLU benchmark with revision 2 labels."""

import json
import random
import re
import unicodedata
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "nlu_test_synthetic_500.jsonl"
PART = re.compile(r"\{(?:(?P<field>\w+)|(?P<text>[^{}|]+)\|(?P<label>[A-Z_]+))\}")
FIELDS = {
    "loc": ("LOCATION", "Kim Liên|Bồ Đề|Vạn Phúc|Phú Thượng|Quan Hoa|Tân Mai|Nhật Tân|Yên Sở|Lĩnh Nam|Xuân La"),
    "poi": ("POI", "bến xe Yên Nghĩa|ga Cát Linh|chợ Mơ|hồ Linh Đàm|công viên Nghĩa Đô|Đại học Thủy Lợi|cầu Vĩnh Tuy|Bệnh viện Bạch Mai"),
    "max": ("PRICE_MAX", "dưới 3tr8|không quá 5 triệu|tối đa 4tr6|trần 6 triệu|≤ 4,2 triệu|dưới 3 triệu rưỡi|không vượt 5tr3|max 4 củ"),
    "min": ("PRICE_MIN", "từ 2tr8|ít nhất 3 triệu|trên 3tr2|từ 4 triệu trở lên|không dưới 2tr6|tối thiểu 3 triệu rưỡi"),
    "area": ("AREA_MIN", "từ 22m²|ít nhất 19m2|trên 27 mét vuông|không dưới 24m2|tối thiểu 17 mét vuông|từ 31m2 trở lên"),
    "radius": ("RADIUS", "bán kính 800m|trong vòng 1,2 km|cách tối đa 650 m|cách dưới 900m|trong tầm 1km|bán kính 1,6 km"),
    "type": ("ROOM_TYPE", "căn một ngủ|phòng có gác|studio|chung cư mini|phòng khép kín|căn 2 ngủ 1 khách|phòng ở ghép|căn hộ dịch vụ"),
    "utility": ("UTILITY", "máy sấy|bếp riêng|mạng wifi|máy giặt riêng|bình nóng lạnh|ban công|chỗ gửi xe máy|thang máy|điều hòa|nuôi mèo"),
    "time": ("DATETIME", "sáu rưỡi tối thứ tư|trưa mai|sáng chủ nhật tuần tới|tầm 19h hôm nay|chiều ngày 26|khoảng 9h15 thứ bảy|sau 8 giờ tối mai|đầu tuần sau"),
    "ref": ("ROOM_REF", "căn số 4|phòng thứ 2|căn đầu danh sách|phòng cuối|căn số 6|căn thứ ba|phòng số 5|căn số 7"),
    "other": ("ROOM_REF", "phòng số 1|căn số 8|căn áp chót|phòng thứ tư|căn thứ năm|căn cuối danh sách|phòng thứ 6|căn số 9"),
    "voice": (None, "với ạ|nhé chị|nha bạn|đc ko|giúp em nhé|được không ta|nha|với"),
    "lead": (None, "bạn ơi|chị ơi|ad ơi|cho mình hỏi tí|ê cho hỏi|anh ơi|nhờ shop|cho em hỏi nha"),
}

# Each line is one scenario. Braces mark a variable field or a literal span.
SCENARIOS = {
    ("search_room",): """Mới chuyển công tác về {loc}; kiếm cho mình {type} {voice}?
Nhà em cần chỗ thuê {radius} tính từ {poi}, ưu tiên {utility}.
Em xin danh sách phòng bên {loc}, tiền thuê {max} là vừa.
Đang chưa có chỗ ở. Có {type} nào quanh {poi} {voice}?
Muốn thuê bên {loc}; em chỉ cần {utility} thôi.
{Cuối tháng|DATETIME} mình dọn ra riêng, tìm chỗ {area} ở {loc}.
{lead}, tìm {trọ|ROOM_TYPE} {min} và {max}, ở {loc} nhé.
Tìm giúp một nơi để ở, miễn là có {utility} và là {type}.
Em học gần {poi}, muốn thuê {radius}, tiền thuê {max}.
Tớ đang đi tìm nhà thuê. Có {type} nào rộng {area} không?
Gửi vài lựa chọn thuê {type} ở {loc} qua đây với.
Cần tìm {phòng|ROOM_TYPE} mới cạnh {poi}, điều kiện cứng là {utility}.
Em và bạn sắp ra ở riêng, ngân sách {max}, phòng rộng {area}.
Có tin nào cho thuê ở {loc} mà đáp ứng {area}, {utility} ko?
Ở quanh {poi} có chỗ thuê {min} không, xin vài tin.
{lead}, có {phòng|ROOM_TYPE} cho thuê {radius} tính từ {poi} rồi tới chỗ làm không?
Mình muốn dọn {time}. Tìm giúp chỗ ở bên {loc} nhé.
Ai chỉ cho em vài chỗ thuê có {utility}, ngân sách {max}?
Đang săn {type} để ở lâu dài, khu {loc} hay quanh đó đều được.
Nhờ tìm {type}, đi {radius} từ {poi} và cần {utility}.
Cần thuê ngay trong khu {loc}, giá {max}, vào ở {time}.
{lead}, cho xem các tin cho thuê mới đăng {voice}?""",
    ("refine_search",): """Những căn vừa gửi hơi xa, giới hạn lại {radius} từ {poi} giúp t.
Giữ các tiêu chí nãy giờ nhưng chỉ hiện căn có {utility}.
Bỏ khu cũ ra, lọc lại kết quả mình đang xem sang {loc} {voice}?
Mấy căn vừa rồi vượt tiền, thu hẹp xuống {max} thôi.
Đổi loại phòng trong bộ lọc thành {type}, phần còn lại giữ nguyên.
Trong dãy kết quả trên chỉ lấy chỗ rộng {area}.
Cái mức sàn lúc nãy hơi thấp, sửa thành {min} giúp mình.
Từ danh sách vừa có, gạt những chỗ lớn hơn dự tính bằng ngưỡng {max} nhé.
Vẫn tìm quanh {poi}, nhưng chỉnh bán kính còn {radius}.
Lọc tiếp trong đống vừa gửi, có {utility} thì giữ.
Cho mình xem lựa chọn khác, vẫn khu và giá cũ {voice}?
Giá để nguyên, đổi chỗ tìm sang {loc} rồi gửi lại kết quả.
Đừng gửi lại mấy tin vừa hiện, lọc nghiêm {area} trước nha.
Chỉnh bộ lọc hiện tại thêm {utility}, bỏ điều kiện về diện tích đi.
Vẫn cùng khu, hạ ngân sách còn {max}, chỉ xem {type}.
Mấy kết quả trên có cách nào lọc rẻ hơn thêm chút {voice}?""",
    ("room_detail",): """{ref} tính tiền điện theo số hay gộp luôn vậy?
{ref} có cho lắp {utility} riêng không bạn?
Cho hỏi {ref} còn trống để ở {time} không?
Nội quy khách đến qua đêm của {ref} thế nào?
Ở {ref}, hợp đồng tối thiểu mấy tháng?
{ref} cọc mấy tháng với có hoàn lại lúc dọn ra không?
{ref} ghi có {utility}; dùng chung với tầng dưới ạ?
{ref} phía {loc} có phí dịch vụ cố định mỗi tháng không?
Diện tích ghi trong tin của {ref} có tính cả nhà vệ sinh?
Ở {ref} điện nước tháng vừa rồi tầm bao nhiêu {voice}?
Phí giữ xe của {ref} tính một người hay tính theo đầu xe?
Khóa cổng của {ref} mở bằng chìa hay vân tay?
{ref} gần {poi} thật không, đi bộ có phải vòng đường lớn?
Đọc giúp chính sách báo trước khi trả {ref} nhé.
{ref} có phòng trống đúng loại {type} trong ảnh không?""",
    ("compare_rooms",): """Đặt {ref} cạnh {other} cho mình xem chênh nhau gì.
Giữa {ref} với {other}, chỗ nào tổng tiền hàng tháng thấp hơn?
Tóm tắt ưu nhược điểm của {ref} và {other} {voice}?
Hai lựa chọn {ref}, {other}, ở nơi nào tiện qua {poi} hơn?
Đối chiếu {ref} và {other} theo tiêu chí có {utility}.
{ref} khác {other} ở khoản cọc với điện nước như nào?
Em phân vân {ref} và {other}; so giúp diện tích thực dùng.
Nếu ưu tiên gần {poi}, {ref} hay {other} hợp hơn?
Lập bảng giúp mình: {ref} so với {other}, thêm cả giờ đóng cổng.
Chọn giữa {ref} với {other} nếu cần loại {type}, bên nào hợp?
{ref} hay {other} ở yên tĩnh hơn, so chi tiết chút nhé.
So giá, diện tích và số người được ở của {ref} với {other}.
{lead}, so hai lựa chọn vừa gửi theo tiêu chí {utility} {voice}?""",
    ("book_appointment",): """Giữ cho mình lịch qua xem {ref} vào {time} nhé.
Mình muốn hẹn trước để tới xem {ref}; {time} đi được.
Nhắn giúp chủ nhà là em xin xem {ref} {time}.
Lên lịch để t qua coi {ref} {time} {voice}?
Cho mình một cuộc hẹn xem phòng quanh {loc} vào {time}.
{lead}, hẹn em qua xem {ref} trực tiếp được chứ?
Đặt lịch ghé {ref} với bạn quản lý vào {time}.
Chốt giùm buổi xem {ref}; mình rảnh {time}.
Mình muốn gặp chủ để coi {ref}, đặt lịch giùm {voice}?
{time} em ghé xem {ref} được, đăng ký lịch giúp em.
Xin giữ khung {time} cho buổi coi {type} ở {loc}.
{lead}, ghi em một suất đi xem thực tế {ref}, vào {time}.
Cho t đăng ký dẫn xem {ref} {voice}?""",
    ("out_of_scope",): """{lead}, viết giúp bài giới thiệu CLB nhiếp ảnh {voice}?
{lead}, đường tròn ngoại tiếp tam giác có tính chất gì?
Gợi ý phim trinh thám xem chung tối nay {voice}?
Tính hộ phép chia phân số này {voice}?
{lead}, cách làm bánh flan ít ngọt {voice}?
Điện thoại không nhận sạc, chỉ mình cách kiểm tra {voice}?
{lead}, cho công thức pha nước chấm chua ngọt đi.
Dịch chữ resilience sang tiếng Việt {voice}?
Giải thích hiện tượng mưa đá dễ hiểu {voice}?
{lead}, chỉ cách sửa lỗi máy in kẹt giấy.
Gợi ý một bài tập giãn cơ vai {voice}?
{lead}, nói cách trồng rau mầm trên giấy ướt.
Viết một lời chúc sinh nhật vui vui cho bạn thân {voice}?""",
    ("search_room", "book_appointment"): """Tìm giúp em {type} ở {loc}, rồi đặt lịch xem một căn {time}.
Kiếm chỗ thuê {max} quanh {poi} và hẹn xem {time} luôn.
Em muốn tìm {phòng|ROOM_TYPE} {area} bên {loc}; đăng ký lịch ghé coi {time} nhé.""",
    ("compare_rooms", "book_appointment"): """So sánh {ref} với {other}, rồi hẹn em xem căn rẻ hơn {time}.
Đối chiếu {ref} và {other}, sau đó đặt lịch đi xem {ref} {time}.""",
    ("refine_search", "room_detail"): """Lọc lại những căn vừa hiện, chỉ giữ chỗ có {utility}; tiện cho hỏi {ref} cọc mấy tháng?
Sửa ngân sách danh sách trước xuống {max}, với lại {ref} có thu phí dọn vào không?""",
    ("room_detail", "book_appointment"): """Cho biết {ref} có cho {utility} không, đồng thời hẹn mình xem {ref} {time}.""",
}


def drop_accents(text):
    return "".join(
        char for char in unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
        if not unicodedata.combining(char)
    )


def normalized(text):
    return " ".join(re.findall(r"\w+", drop_accents(text).casefold()))


def labeled_parts(text, label):
    """Return (start, end, label) spans relative to one marked field."""
    if label is None:
        return []
    if label in {"PRICE_MAX", "PRICE_MIN", "AREA_MIN"}:
        # The v2 training data excludes comparison cues for a single amount.
        amount = re.search(r"\d.*?(?= trở lên$|$)", text)
        return [(amount.start(), amount.end(), label)]
    if label == "DATETIME":
        # The chat annotation guide keeps the time itself.
        cue = re.match(r"(?:tầm|khoảng) ", text)
        return [(cue.end() if cue else 0, len(text), label)]
    if label == "ROOM_TYPE" and text == "phòng có gác":
        return [(0, len("phòng"), label), (len("phòng có "), len(text), "UTILITY")]
    if label == "ROOM_TYPE" and text == "phòng ở ghép":
        # In v2, "ở ghép" is occupancy rather than a dedicated entity class.
        return [(0, len("phòng"), label)]
    return [(0, len(text), label)]


def render(template, values, style):
    def transform(value):
        if style == "no_diacritics":
            return drop_accents(value).lower()
        if style == "lowercase":
            return value.lower()
        return value

    text = ""
    entities = []
    cursor = 0
    for match in PART.finditer(template):
        text += transform(template[cursor:match.start()])
        field = match["field"]
        if field:
            label, _ = FIELDS[field]
            original = values[field]
        else:
            label, original = match["label"], match["text"]
        for start, end, kind in labeled_parts(original, label):
            entities.append({
                "start": len(text) + len(transform(original[:start])),
                "end": len(text) + len(transform(original[:end])),
                "label": kind,
            })
        text += transform(original)
        cursor = match.end()
    text += transform(template[cursor:])
    previous_end = 0
    for entity in entities:
        start, end = entity["start"], entity["end"]
        if not previous_end <= start < end <= len(text) or text[start:end] != text[start:end].strip():
            raise ValueError(f"Invalid entity offset: {text!r}, {entity}")
        previous_end = end
    if any(char in text for char in "{}") or text.startswith("+"):
        raise ValueError(f"Unresolved markup: {text!r}")
    return text, entities


def build():
    rng = random.Random(4517)
    excluded = set()
    for path in HERE.glob("*.jsonl"):
        if path == OUTPUT:
            continue
        with path.open(encoding="utf-8-sig") as file:
            for line in file:
                row = json.loads(line)
                if isinstance(row.get("text"), str):
                    excluded.add(normalized(row["text"]))
    styles = ("as_written", "lowercase", "no_diacritics", "as_written", "as_written")
    rows = []
    template_id = 0
    for intents, block in SCENARIOS.items():
        for template in block.splitlines():
            template_id += 1
            # Give each row a distinct choice for each placeholder within a template.
            variants = {key: rng.sample(values.split("|"), len(styles))
                        for key, (_, values) in FIELDS.items()}
            if not any(match["field"] for match in PART.finditer(template)):
                template += " {voice}"
            for index, style in enumerate(styles):
                values = {key: samples[index] for key, samples in variants.items()}
                sentence, entities = render(template, values, style)
                key = normalized(sentence)
                if key in excluded:
                    raise ValueError(f"Duplicate sentence at template {template_id}: {sentence}")
                excluded.add(key)
                rows.append({
                    "source": "synthetic_template", "annotation_revision": 2,
                    "template_id": template_id, "style": style,
                    "text": sentence, "intents": list(intents), "entities": entities,
                })
    if len(rows) != 500 or template_id != 100:
        raise ValueError(f"Expected 500 rows from 100 templates, got {len(rows)} from {template_id}")
    rng.shuffle(rows)
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as file:
        for index, row in enumerate(rows, start=1):
            file.write(json.dumps({"id": f"syn-{index:03}", **row}, ensure_ascii=False) + "\n")
    print(Counter(row["intents"][0] if len(row["intents"]) == 1 else "multi" for row in rows))
    print(Counter(e["label"] for row in rows for e in row["entities"]))


if __name__ == "__main__":
    build()
