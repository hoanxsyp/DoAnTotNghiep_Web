"""Generate intent examples and add a stratified sample of prior training rows."""

import json
import random
import string
from collections import Counter

from build_ner_focused_train import HERE, normalized, read, unaccent

OUTPUT = HERE / "intent_train_focused_v1.jsonl"
FIELDS = {
    "area": "Tứ Liên|Láng Hạ|Mễ Trì|Phú Diễn|Yên Phụ|Đại Kim|Hạ Yên Quyết|Ô Chợ Dừa|Định Công|Trung Văn|Ngọc Lâm|Bưởi|Nghĩa Tân|Thanh Nhàn|Thành Công|Mộ Lao",
    "budget": "3tr6|4,9 triệu|2 triệu rưỡi|4tr7|3tr9|5 củ|3 triệu 4|6tr2|2tr7|5,1 triệu",
    "room": "căn số 2|phòng số 3|phòng số 8|căn thứ tư|phòng trên cùng|căn C|căn số 10|căn giữa|phòng B|căn đang mở|phòng bên trái",
    "room2": "phòng số 6|căn số 1|phòng dưới cùng|căn B|phòng bên phải|căn số 11|căn đầu tiên",
    "feature": "bàn làm việc|máy giặt sấy|cửa sổ lớn|máy nước nóng|chỗ gửi ô tô|thang máy|bếp riêng|ban công|tủ lạnh|sân phơi",
    "type": "studio|phòng đơn|phòng có gác|căn hộ dịch vụ|căn hai phòng ngủ|chung cư mini",
    "when": "tối mai|7h45 thứ bảy|chiều chủ nhật|sáng ngày 12|9h thứ tư|sau giờ làm|trưa thứ hai|tối ngày 18",
    "voice": "hộ mình nhé|giúp em với|đc k bạn|với ạ|giúp tui nha|nhé bác|cho t nha|được không ta|ạ|nhá|đi bạn|giùm e",
    "topic": "rừng ngập mặn|sự bay hơi|phân số|quy tắc bàn tay phải|từ ghép|bộ nhớ đệm|lạm phát|mùa mưa",
    "object": "màn hình máy tính|chuột không dây|nồi áp suất|tai nghe bluetooth|máy chiếu|micro thu âm|điều khiển tivi|camera hành trình",
    "food": "bánh cuốn|chè đậu xanh|gà kho gừng|canh rong biển|trà đào|bánh khoai|cơm chiên|sữa chua",
    "activity": "chạy 5 km|trồng hoa cúc|nuôi cá cảnh|vẽ bằng màu nước|học bơi|tập đàn guitar|đạp xe|chơi cờ vua",
    "media": "phim khoa học viễn tưởng|nhạc jazz|truyện trinh thám|phim hoạt hình|chương trình nấu ăn|sách về thiên văn",
    "occasion": "kỷ niệm tốt nghiệp|sinh nhật đồng nghiệp|họp lớp|ngày hội thể thao|lễ chia tay|đêm văn nghệ",
}

TEMPLATES = {
    "search_room": """
Em sắp ra ở riêng, có tin thuê {type} ở {area} chưa {voice}?
Bạn có danh sách nhà đang cho thuê ở {area} không, mình mới bắt đầu tìm.
Chỉ hộ tớ nơi thuê {type}, quanh {area} và không vượt {budget}.
Nhờ gợi ý chỗ thuê quanh {area} cho người đi làm, có {feature} càng tốt.
Nhà em cần thuê chỗ ở khu {area}, giá trong khoảng {budget} {voice}.
Mình đang kiếm trọ lần đầu, ai có tin cho thuê bên {area} giới thiệu với.
Thiếu chỗ ở từ tháng tới, có {type} phù hợp không vậy {voice}?
Cho em vài lựa chọn đang đăng cho thuê, giá tối đa {budget}.
""",
    "refine_search": """
Các chỗ vừa hiện cao quá, cho mình xem danh sách khác trong mức {budget}.
Để nguyên bộ lọc cũ, bỏ các tin thiếu {feature} đi {voice}.
Gạt khu trước ra giúp, tính lại các kết quả đang có ở {area}.
Các tin nãy chưa được, vẫn giữ nguyên tiền nhưng chuyển vùng sang {area}.
Từ mấy lựa chọn trên lọc lại mỗi loại {type} cho em.
Thêm điều kiện {feature} vào danh sách đang xem, các điều kiện khác giữ nguyên.
Kết quả vừa trả chưa hợp, thử hạ trần xuống {budget} giúp em.
Tiêu chí cũ giữ nguyên, hiển thị vài lựa chọn khác đi {voice}.
Vẫn cần {type} như lúc đầu, nhưng thu nhỏ mức thuê còn {budget}.
Từ bảng kết quả vừa rồi chỉ giữ tin khu {area} và có {feature}.
""",
    "room_detail": """
Quy định đón bạn tới chơi qua đêm ở {room} thế nào {voice}?
{room} có đóng thêm phí quản lý theo tháng không?
Đọc giùm điều khoản trả cọc của {room} nếu báo dọn trước một tháng.
Số tiền ghi trên tin của {room} đã tính phí dịch vụ chưa {voice}?
Giờ mở cửa cho khách của {room} là đến mấy giờ vậy?
{room} cho mình tự lắp {feature} hay phải xin quản lý?
Địa chỉ trên tin của {room} có ảnh mặt tiền thật chưa?
{room} ghi sẵn {feature}, là đồ bao gồm trong nhà hay dùng chung?
Chủ của {room} có yêu cầu ký hợp đồng tối thiểu bao lâu?
Có xem được kích thước đo thực của {room} trong hình không {voice}?
Mình hỏi riêng {room}: có giới hạn số người ở và phụ thu xe không?
{room} còn trống để dọn đến vào {when} không {voice}?
""",
    "compare_rooms": """
Đối chiếu chi phí ở {room} với {room2} dùm, bên nào đáng hơn?
Giữa {room2} và {room}, nơi nào hợp nếu nhà có trẻ nhỏ {voice}?
Liệt kê mặt lợi và bất tiện của {room} với {room2} cho mình.
So từng khoản cọc, phí và điều kiện thuê của {room} với {room2}.
Nếu cần {feature} thì nên nghiêng về {room} hay {room2} {voice}?
Để cạnh nhau xem, {room} chênh {room2} bao nhiêu tiền mỗi tháng?
Trong hai lựa chọn trên, tin nào lợi hơn về vị trí lẫn giá {voice}?
""",
    "book_appointment": """
Mình muốn đăng ký buổi xem thực tế {room} vào {when}.
Nhờ xếp lịch cho em tới gặp chủ ở {room} {when} {voice}.
Xin một khung giờ {when} để qua coi {room} với anh quản lý.
Báo giùm bên cho thuê cho em ghé xem {room} {when} nhé.
Cho mình chốt giờ {when} để xem chỗ ở tại {area} {voice}.
Nhờ giữ suất xem nhà để ghé {room}, em đi được {when}.
""",
    "out_of_scope": """
Giải thích cho em vì sao {topic} lại quan trọng {voice}.
Kể ví dụ đời thường dễ hiểu về {topic} {voice}.
Cho mình một câu hỏi trắc nghiệm với đáp án về {topic}.
{topic} là gì, tóm tắt kiểu người mới học dễ hiểu {voice}.
{object} tự nhiên không nhận nguồn, kiểm tra phần nào trước?
Nói mình cách kết nối {object} sau khi mới cài hệ điều hành {voice}.
Tại sao {object} nóng lên lúc dùng, có mẹo sửa không?
Có hướng dẫn nào reset {object} về cấu hình lúc đầu không {voice}?
Nhà mình muốn nấu {food} mà ít dầu, làm sao cho ngon?
Cho công thức làm {food} đủ hai người ăn {voice}.
Nếu thiếu nguyên liệu thì có cách làm {food} đơn giản hơn không?
Chỉ em cách chuẩn bị dụng cụ để {activity} cuối tuần {voice}.
Mới tập {activity} thì những lỗi nào nên tránh?
Cho lịch tập {activity} trong hai tuần {voice}.
Tối rảnh, giới thiệu mấy lựa chọn {media} dễ thưởng thức với.
Có thể giới thiệu về {media} cho người chưa từng xem hoặc nghe không?
Gợi ý mở đầu bài phát biểu cho dịp {occasion} {voice}.
Viết một tin chúc mừng dịp {occasion} ngắn dễ thương.
Tớ cần một mẫu thư mời bạn bè đến {occasion} {voice}.
Chỉ mình dùng công thức đếm ô có chữ trong bảng tính {voice}.
Giúp mình kiểm tra chính tả đoạn tiểu luận này {voice}.
Sao điện thoại gửi ảnh qua tin nhắn toàn báo thất bại {voice}?
So sánh giúp giá hai gói dữ liệu di động đang dùng {voice}.
Tìm vài cách gói quà bằng giấy cũ để giảm rác {voice}.
Đặt lịch khám răng cho người nhà vào {when} giúp mình.
Hẹn nhắc mình chuyển tiền điện vào {when} {voice}.
""",
}


def build():
    rng = random.Random(272026)
    original = read("intent_train_v2.jsonl")
    test_keys = {
        normalized(r["text"])
        for name in ("nlu_test_synthetic_500.jsonl", "intent_test_gold.jsonl",
                     "intent_test_real.jsonl", "intent_test_synthetic_challenge_v1.jsonl")
        for r in read(name)
    }
    seen = test_keys | {normalized(r["text"]) for r in original}
    seen.update(normalized(r["text"]) for r in read("intent_train_real.jsonl"))
    rows = []
    formatter = string.Formatter()
    for intent, block in TEMPLATES.items():
        for template in block.strip().splitlines():
            fields = {field for _, field, _, _ in formatter.parse(template) if field}
            target = 22 if len(fields) > 1 else 8
            count = 0
            for attempt in range(450):
                values = {key: rng.choice(FIELDS[key].split("|")) for key in sorted(fields)}
                text = template.format(**values)
                if attempt % 5 == 3:
                    text = text.lower()
                if attempt % 5 == 4:
                    text = unaccent(text).lower()
                key = normalized(text)
                if key in seen:
                    continue
                seen.add(key)
                rows.append({"text": text, "intent": intent, "source": "focused"})
                count += 1
                if count == target:
                    break
            if count < 5:
                raise ValueError(f"Insufficient variants: {template}")
    used = set(test_keys)
    for intent in TEMPLATES:
        eligible = []
        for row in original:
            key = normalized(row["text"])
            if row["intent"] == intent and key not in used:
                eligible.append(row)
                used.add(key)
        rows.extend({**row, "source": "replay"} for row in rng.sample(eligible, min(150, len(eligible))))
    rng.shuffle(rows)
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(Counter(row["source"] for row in rows))
    print(Counter(row["intent"] for row in rows))


if __name__ == "__main__":
    build()
