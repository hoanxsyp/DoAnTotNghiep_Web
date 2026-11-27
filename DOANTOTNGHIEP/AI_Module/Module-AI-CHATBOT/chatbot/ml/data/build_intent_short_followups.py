"""Build intent data for short chat turns, with accent-folded holdout isolation."""

import argparse
import random
import unicodedata
from pathlib import Path

from build_ner_short_followups import DATA, normalize, read, write

REFINEMENTS = (
    "giờ tự do", "tự do giờ giấc", "không giới nghiêm", "giờ giấc thoải mái",
    "ra vào tự do", "ra vào bất cứ lúc nào", "không khóa cổng ban đêm",
    "điều hòa", "nóng lạnh", "máy giặt riêng", "chỗ để xe", "thang máy", "vskk",
    "ban công", "không chung chủ", "nuôi được mèo", "bếp riêng", "wifi riêng",
)
EXAMPLES = {
    "refine_search": (
        "thêm điều kiện đi|thôi bỏ ban công|chỗ khác rẻ hơn|rộng hơn chút|dưới 3tr5|"
        "thêm 500k cũng được|trên 20m2 nhé|ở Nhân Chính thôi|sang Trung Văn đi|"
        "đổi sang studio|ít nhất 25 mét vuông|bỏ giới hạn giá|tầm 4 củ thôi|có gác nữa"
    ),
    "search_room": (
        "tìm trọ Thanh Xuân|kiếm phòng Hà Đông|có trọ khu Bưởi không|tìm nhà nguyên căn|"
        "tìm chỗ thuê dưới 3 triệu|có phòng gần bến xe không|trọ quanh Đống Đa còn không|"
        "cho tìm trọ có gác|tìm phòng có giờ tự do|kiếm chung cư mini|bắt đầu tìm lại|tìm phòng cho hai người"
    ),
    "room_detail": (
        "phòng 1 có điều hòa không|căn đó còn trống không|phòng này có giới nghiêm không|"
        "căn số 3 giá bao nhiêu|phòng trên có ảnh không|xem địa chỉ phòng 2|"
        "phòng này mở cổng mấy giờ|chủ căn đó ở cùng à|căn đầu có thang máy chứ|"
        "điện phòng 3 bao nhiêu|xem ảnh căn đầu|tin này có chỗ để xe không"
    ),
    "compare_rooms": (
        "so phòng 1 với 3|căn nào rộng hơn|so hai căn đầu|2 với 4 bên nào rẻ|"
        "phòng 1 hay phòng 2|giữa 2 và 3 căn nào có cửa sổ|so giá hai phòng|căn nào gần hơn|"
        "chọn căn 1 hay 4|so tiền cọc của hai căn|hai phòng khác gì|căn nào có giờ thoải mái hơn"
    ),
    "book_appointment": (
        "đặt lịch phòng 2|hẹn xem nhà mai nhé|cho em xem phòng 1 chiều mai|xem căn 3 lúc 7 giờ|"
        "đăng ký xem căn này|hẹn chủ nhà tối thứ ba|chốt lịch xem chỗ đó|dời lịch xem sang mai|"
        "mai qua xem được không|hẹn lại cuối tuần|đặt lịch xem căn đầu|xin lịch xem phòng chiều nay"
    ),
    "out_of_scope": (
        "mấy giờ rồi|giờ làm việc tự do|tự do tài chính|nhạc tự do|giờ nghỉ trưa|"
        "tự do sáng tạo|thời khóa biểu|giờ mở cửa siêu thị|điều hòa bị hỏng sửa sao|"
        "cho hỏi giá vàng|wifi nhà mình bị chậm|cách vệ sinh máy giặt|đổi giờ báo thức|hôm nay trời mưa không"
    ),
}
HOLDOUT = {
    "refine_search": (
        "giờ giấc tự do|không có giờ đóng cổng nha|ra vô 24/24|miễn không quản giờ|"
        "có ban công riêng nữa|cần máy lạnh cơ|phòng trên 24m2 thôi|giảm xuống 3tr8|"
        "dưới 3tr8 thôi|từ 4tr2 trở lên nhé|đổi sang khu Nhân Hòa|thu hẹp quanh Cầu Giấy|"
        "tìm rẻ hơn hồi nãy|chỗ để ô tô nữa|chỉ lấy căn dưới 4 triệu|bỏ điều kiện cửa sổ"
    ),
    "search_room": (
        "ở khu thanh xuân có không|tìm phòng trọ Hoàng Mai|có nhà cho thuê gần chợ không|"
        "cho tìm phòng dưới hai triệu|tìm trọ mới quanh Cầu Giấy|"
        "kiếm phòng ở khu Vĩnh Hưng|tìm nhà thuê giờ giấc tự do|tìm studio quanh Mỹ Đình"
    ),
    "room_detail": (
        "phòng 2 có giờ giấc tự do không|căn đầu cho về muộn không|phòng này có bếp riêng không|"
        "cái phòng kia có gác không|căn 4 tiền cọc sao|cho địa chỉ căn 5|căn trên cho nuôi mèo chứ|phòng kia còn trống à"
    ),
    "compare_rooms": (
        "so căn 2 và căn 5|căn 1 hay 3 tiện hơn|so hai căn giá 4 triệu|"
        "giữa căn 1 và 2 thì cái nào thoáng|phòng nào tự do giờ hơn trong hai phòng|"
        "so phòng cuối với phòng đầu|hai căn chênh nhau bao nhiêu|căn nào có giá tốt hơn trong hai căn đó"
    ),
    "book_appointment": (
        "hẹn xem căn cuối sáng mai|đặt lịch phòng 3 tối nay|em qua xem được lúc 9h không|"
        "hẹn chủ phòng 4 chiều nay|tới coi căn số 2 thứ năm|cho coi phòng cuối trưa mai|"
        "đổi lịch xem sang chủ nhật|xếp lịch xem nhà sáng thứ sáu"
    ),
    "out_of_scope": (
        "tự do ngôn luận|cách chỉnh giờ làm|tin thời tiết tối nay|tự do làm việc tại nhà|"
        "chế độ ban đêm điều hòa là gì|mở khóa cổng thông minh thế nào|"
        "giờ đóng cửa bưu điện|làm sao để đổi mật khẩu wifi|bài tập toán lớp 9"
    ),
}


def unaccent(text):
    decomposed = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    return "".join(char for char in decomposed if not unicodedata.combining(char))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mixed-output", type=Path, help="Write the new examples with stratified replay.")
    args = parser.parse_args()
    test = [{"text": unaccent(text), "intent": label}
            for label, block in HOLDOUT.items() for text in block.split("|")]
    excluded = {normalize(row["text"]) for row in test}
    for name in ("intent_test_gold.jsonl", "intent_test_real.jsonl", "intent_test_synthetic_challenge_v1.jsonl",
                 "nlu_test_synthetic_500.jsonl"):
        path = DATA / name
        if path.is_file():
            excluded.update(normalize(row["text"]) for row in read(path))

    seen = set()
    train = []
    for label, block in EXAMPLES.items():
        phrases = block.split("|")
        if label == "refine_search":
            phrases += [template.format(feature) for feature in REFINEMENTS
                        for template in ("{}", "{} nhé", "thêm {}", "ưu tiên {}")]
        for text in phrases:
            if normalize(text) in excluded:
                continue
            for variant in dict.fromkeys((text, unaccent(text))):
                if variant not in seen:
                    train.append({"text": variant, "intent": label})
                    seen.add(variant)
    write(DATA / "intent_train_short_followups.jsonl", train)
    write(DATA / "intent_test_short_followups.jsonl", test)

    if args.mixed_output:
        rng = random.Random(42)
        focused = read(DATA / "intent_train_focused_v1.jsonl")
        replay = []
        blocked = excluded | {normalize(row["text"]) for row in train}
        for label in EXAMPLES:
            eligible = []
            for row in focused:
                key = normalize(row["text"])
                if row["intent"] == label and key not in blocked:
                    eligible.append(row)
                    blocked.add(key)
            replay.extend(rng.sample(eligible, min(100, len(eligible))))
        reality = [row for row in read(DATA / "intent_train_real.jsonl")
                   if normalize(row["text"]) not in excluded]
        mixed = replay + reality + train
        rng.shuffle(mixed)
        write(args.mixed_output, mixed)
        print(f"Short train: {len(train)}, replay: {len(replay)}, real: {len(reality)}, "
              f"mixed: {len(mixed)}, test: {len(test)}")


if __name__ == "__main__":
    main()
