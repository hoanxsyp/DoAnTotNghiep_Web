"""Create targeted NER examples and mix in previously used training data."""

import json
import random
import re
import unicodedata
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "ner_train_focused_v1.jsonl"
POOL = {
    "lower": ("PRICE_MIN", "2tr3|3,4 triệu|2 triệu rưỡi|4tr7|3 triệu|5 triệu|3tr6|4 củ|1tr9|4,1tr|5tr4|2 triệu 7|3tr3|2 triệu tám"),
    "upper": ("PRICE_MAX", "4tr4|5,7 triệu|6tr8|5 triệu rưỡi|7tr|6 củ|4 triệu 9|8 triệu|3tr9|4,8tr|5tr6|5 triệu 2|7tr2|6 triệu tư"),
    "place": ("LOCATION", "Thanh Nhàn|Láng Hạ|Hạ Yên Quyết|Bạch Đằng|Định Công|Ô Chợ Dừa|Xuân Đỉnh|Mễ Trì|Phú Diễn|Trung Hòa|Đại Kim|Hàng Bột|Nghĩa Tân|Ngọc Lâm|Bưởi|Vĩnh Hưng|Mộ Lao|Thịnh Quang|Khâm Thiên|Trần Cung|Trung Văn|Phương Liệt|Tứ Liên|Thanh Lương"),
    "landmark": ("POI", "bến xe Giáp Bát|bến xe Nước Ngầm|ga Hà Đông|ga La Thành|nhà ga Long Biên|bến xe Gia Lâm|chợ Đồng Tâm|chợ Nghĩa Tân|chợ Phùng Khoang|siêu thị Aeon Hà Đông|hồ Đống Đa|hồ Hoàng Cầu|hồ Thành Công|hồ Đền Lừ|công viên Thống Nhất|công viên Hòa Bình|vườn hoa Lý Thái Tổ|cầu Thanh Trì|cầu Chương Dương|cầu Nhật Tân|cầu Mai Động|bệnh viện Việt Đức|Bệnh viện E|bệnh viện Xanh Pôn|Đại học Kiến trúc|ĐH Điện Lực|Đại học Luật Hà Nội|ĐH Xây dựng|Cung Văn hóa Hữu nghị|bưu điện Hà Đông|rạp chiếu phim Quốc Gia|Nhà thi đấu Cầu Giấy|ga Văn Quán|hồ Tai Trâu|chợ Bưởi|Đại học Mỏ Địa chất"),
    "room": ("ROOM_TYPE", "căn hộ 1 phòng ngủ|căn một phòng ngủ|chung cư 2 phòng ngủ|căn 2PN 1PK|căn hai phòng ngủ một phòng khách|căn một ngủ một khách|căn studio|studio|căn 1PN khép kín|căn hộ dịch vụ|chung cư mini|phòng đơn|nhà nguyên căn|phòng khép kín|căn hộ chung cư|căn 1 ngủ|phòng trọ|phòng|trọ|căn một PN|phòng trọ khép kín|duplex"),
    "feature": ("UTILITY", "tủ lạnh nhỏ|tủ quần áo|bếp khép kín|bếp nấu riêng|nhà vệ sinh riêng|chỗ để ô tô|bãi xe có mái|đường truyền internet|wifi riêng|máy giặt sấy|máy nước nóng|máy giặt chung|cho nuôi chó nhỏ|sân phơi|chỗ đỗ xe đạp|bình nóng lạnh|thang máy|cửa sổ thoáng|gác xép|gác|lò vi sóng|điều hòa hai chiều|để xe máy trong nhà|ban công nhỏ"),
    "ref": ("ROOM_REF", "căn số 2|phòng số 3|phòng số 8|căn số 10|căn áp cuối|phòng trên cùng|căn thứ tư|phòng liền sau|căn giữa danh sách|phòng kế cuối|phòng B|căn C|phòng vừa gửi|căn chót|phòng bên trái|căn số một"),
    "ref2": ("ROOM_REF", "căn số 12|phòng thứ nhất|căn tiếp theo|căn số 11|phòng thứ năm|phòng A|phòng bên phải|căn dưới cùng|phòng 7|phòng thứ sáu|căn thứ hai|phòng thứ ba"),
    "date": ("DATETIME", "7h45 tối nay|chiều thứ năm|sáng ngày 15|21h ngày mai|tối thứ hai tuần sau|cuối tháng này|giữa tháng tới|sáng thứ 6|9 giờ trưa thứ bảy|chín rưỡi tối mai|thứ ba tuần này|chiều 14/9"),
    "radius": ("RADIUS", "trong vòng 750m|bán kính 2,3 km|cách không quá 500 m|cách tầm 1,4km|trong vòng 950 mét|bán kính 600m|cách dưới 700 mét|bán kính 1km"),
    "area": ("AREA_MIN", "18 m²|21m2|26 mét vuông|33m²|20m2|16 mét vuông|29m2|36 m²"),
    "voice": (None, "giùm mình|cho em tham khảo|nha bác|giúp tui với|nhé mọi người|được hông|với ạ|nha anh|được ko|nhé c|ạ|giúp em"),
}

# Price indicators are literal context. The labeled span is the amount.
TEMPLATES = {
    "price_floor": """
Tin giá rẻ hơn bỏ qua giúp, em lấy mốc sàn {lower} một tháng {voice}.
Khoảng nào cũng đc miễn tiền thuê từ {lower} trở lên; em ở phía {place}.
Có chỗ nào bên {place} mà giá phải ít nhất {lower} mỗi tháng ko?
Đặt giá sàn ở {lower} nhé, em không giới hạn giá trên.
Em chọn căn giá trên {lower}, xem khu {place} {voice}.
Có thể cho em các căn không dưới {lower} quanh {landmark} không?
Ở gần {landmark} thì cho mình phòng mức thấp nhất {lower} thôi.
Tăng mốc tối thiểu thành {lower}, phần giá trần vẫn là {upper}.
Chỉ gửi tin nào giá từ {lower} và dưới {upper} ở {place}.
Ngân sách tối đa là {upper} nhưng đừng chọn tin thấp hơn {lower} nhé.
Nếu có {room} giá tối thiểu {lower} thì gửi em xem {voice}.
Lọc cho chị tiền thuê phải trên {lower}, có {feature}.
Tiền phòng mỗi tháng khởi điểm {lower}, khu {place} thì càng tiện.
Bỏ mức giá cũ, lấy từ {lower} trở đi và tối đa {upper} {voice}.
""",
    "landmark": """
Mình ra vào {landmark} hằng ngày, nhà thuê ở quanh đó thì tiện.
Quanh {landmark} có {room} vừa túi tiền không {voice}?
Đi làm ở {place} nhưng ưu tiên chỗ ở dễ đi tới {landmark}.
Thuê giúp em ở {place}, ưu tiên nhà đối diện {landmark}.
Mình không rành bên {place}, có chỗ nào sát {landmark} {voice}?
Muốn đi bộ từ nhà đến {landmark}, phạm vi {radius}.
So giúp {ref} với {ref2}: bên nào đi tới {landmark} nhanh hơn?
Theo tin thì {ref} ở bên cạnh {landmark}, có phải đi vòng qua đường lớn không?
Giữ khu {place}, đổi mốc tìm kiếm sang {landmark}.
Tìm khu nào đi qua {landmark} nhanh và có {feature}.
Em cần phòng gần chợ nói chung, riêng khu {place} cũng được {voice}.
Nhờ tìm quanh trường hay bến xe, tiền thuê dưới {upper} {voice}.
Giữa {ref2} với {ref}, có cái nào đi tới bến xe ít phải chuyển tuyến không {voice}?
Có lựa chọn nào {radius} từ {landmark} và giá chặn ở {upper}?
""",
    "room_type": """
Lần này đổi bộ lọc qua loại {room}, địa bàn cứ để {place}.
Anh đang tìm {room}, ở cạnh {landmark} thì tốt.
Ưu tiên đúng {room}, không cần có {feature} {voice}.
Cho mình coi ảnh của loại {room}, giá kịch trần {upper}.
Có còn {room} để ở từ {date} không {voice}?
Loại {room} thì phí dọn vào tính thế nào hả anh, em hỏi về {ref}.
Cho xem thêm {room} có diện tích ít nhất {area} nhé.
Em muốn tìm {room} quanh {place}, có {feature} càng tốt.
Mình tìm <phòng|ROOM_TYPE> thoáng có {feature} trong khu {place}.
Ở {place} có <phòng|ROOM_TYPE> cho thuê dài hạn không {voice}?
Hoãn tiêu chí lúc nãy, em cần <phòng|ROOM_TYPE> có {feature}, dưới {upper}.
Trong {ref} và {ref2}, căn nào là kiểu {room} {voice}?
Con nhỏ nên nhà mình chỉ lấy {room}, không ở ghép, tìm quanh {landmark}.
Giới thiệu cho mình {room} ở {place} với diện tích từ {area} trở lên.
""",
    "chat_and_negative": """
Hỏi thêm phí giữ xe của {ref} tính riêng hay đưa vào hợp đồng {voice}?
{ref} có tính thêm tiền điện theo giá dịch vụ mỗi tháng không?
Chỗ ngủ trong {ref} có tính vào diện tích ghi trên tin không {voice}?
Giá điện ở {ref} và {ref2} chỗ nào ổn hơn, cọc có khác nhau không?
{ref} còn trống không, mình muốn hẹn tới coi khoảng {date}.
Em ghé {ref} lúc {date} để xem thử {feature}, đặt lịch hộ em.
Cho hỏi {ref} có nội quy khách tới chơi qua đêm không {voice}?
Hẹn đưa em coi {ref} tầm {date}; cho em hỏi phải đem giấy tờ gì?
Phòng có thu tiền cọc xem nhà không {voice}?
Em có thể qua xem phòng trực tiếp trước khi ký hợp đồng {voice}?
Cổng có khóa buổi tối không, mình hỏi về {ref} {voice}?
Chỉ mình cách đổi định dạng ngày tháng trong bảng tính {voice}.
Sao máy ảnh mở lên không hiện ống kính, hỗ trợ mình {voice}?
Hướng dẫn cách ủ bột làm bánh ở nhà {voice}.
""",
}
PART = re.compile(r"\{(\w+)\}|<([^<>|]+)\|([A-Z_]+)>")


def unaccent(text):
    return "".join(
        c for c in unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
        if not unicodedata.combining(c)
    )


def normalized(text):
    return " ".join(re.findall(r"\w+", unaccent(text).casefold()))


def render(template, rng, style):
    transform = (str, str.lower, lambda text: unaccent(text).lower())[style]
    choices = {}
    sentence, entities, cursor = "", [], 0
    for part in PART.finditer(template):
        sentence += transform(template[cursor:part.start()])
        field, phrase, label = part.groups()
        if field:
            label, values = POOL[field]
            if field not in choices:
                choices[field] = rng.choice(values.split("|"))
            phrase = choices[field]
        phrase = transform(phrase)
        if label:
            entities.append({"start": len(sentence), "end": len(sentence) + len(phrase), "label": label})
        sentence += phrase
        cursor = part.end()
    return {"text": sentence + transform(template[cursor:]), "entities": entities}


def read(filename):
    return [json.loads(line) for line in (HERE / filename).read_text(encoding="utf-8-sig").splitlines()]


def build():
    rng = random.Random(94027)
    replay_sources = ("ner_train_v2.jsonl", "ner_train_chat_synthetic_2750.jsonl")
    prior = [read(name) for name in replay_sources]
    # Use test text only to reject collisions. Never read it to generate examples.
    test_keys = {
        normalized(row["text"])
        for filename in ("nlu_test_synthetic_500.jsonl", "ner_test_synthetic_challenge_v1.jsonl",
                         "ner_test_gold.jsonl", "ner_test_real.jsonl", "intent_test_gold.jsonl")
        for row in read(filename)
    }
    seen = test_keys | {normalized(row["text"]) for corpus in prior for row in corpus}
    seen.update(normalized(row["text"]) for row in read("ner_train_real.jsonl"))
    rows = []
    for group, block in TEMPLATES.items():
        for template in block.strip().splitlines():
            target = 30 if len(list(PART.finditer(template))) >= 2 else 12
            accepted = 0
            for attempt in range(800):
                row = render(template, rng, (0, 0, 0, 1, 2)[attempt % 5])
                key = normalized(row["text"])
                if key in seen:
                    continue
                seen.add(key)
                rows.append({**row, "source": f"focused_{group}"})
                accepted += 1
                if accepted == target:
                    break
            if accepted < 8:
                raise ValueError(f"Insufficient variants: {template}")
    replay_keys = set()
    for source, corpus in zip(replay_sources, prior):
        eligible = []
        for row in corpus:
            key = normalized(row["text"])
            if key not in test_keys and key not in replay_keys:
                replay_keys.add(key)
                eligible.append(row)
        for row in rng.sample(eligible, 400):
            rows.append({"text": row["text"], "entities": row["entities"], "source": f"replay_{source}"})
    rng.shuffle(rows)
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False) + "\n")
    print("Rows:", Counter(r["source"] for r in rows))
    print("Entities:", Counter(e["label"] for r in rows for e in r["entities"]))


if __name__ == "__main__":
    build()
