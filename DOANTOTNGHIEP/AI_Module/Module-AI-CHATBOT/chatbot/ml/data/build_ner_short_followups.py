"""Build labeled NER examples for short search refinements and a separate evaluation set."""

import argparse
import json
import random
import unicodedata
from pathlib import Path

DATA = Path(__file__).resolve().parent
FEATURES = (
    "giờ giấc thoải mái", "giờ tự do", "tự do giờ giấc", "không giới nghiêm",
    "ra vào tự do", "ra vào bất cứ lúc nào", "không khóa cổng ban đêm", "chìa khóa riêng",
    "điều hòa", "máy giặt chung", "bình nóng lạnh", "tủ lạnh", "wifi",
    "thang máy", "gác lửng", "cửa sổ", "ban công", "chỗ gửi xe",
    "nhà vệ sinh riêng", "vskk", "bếp riêng", "được nuôi mèo", "đủ nội thất",
    "không chung chủ", "chỗ để xe máy", "khóa vân tay",
)
NUMERIC = (
    ("PRICE_MAX", "2tr7", "dưới {}", "tối đa {} nhé"),
    ("PRICE_MAX", "4 triệu rưỡi", "không quá {}", "giá tới {} thôi"),
    ("PRICE_MAX", "3,2 triệu", "{} đổ lại", "rẻ hơn {}"),
    ("PRICE_MIN", "3tr5", "từ {} trở lên", "ít nhất {}"),
    ("PRICE_MIN", "2 triệu", "trên {}", "{} trở lên"),
    ("AREA_MIN", "23m2", "trên {}", "ít nhất {} nhé"),
    ("AREA_MIN", "19 mét vuông", "{} trở lên", "rộng tối thiểu {}"),
    ("LOCATION", "Thanh Xuân", "bên {}", "{} nhé"),
    ("LOCATION", "Hà Đông", "ở {}", "qua {} đi"),
    ("LOCATION", "Đống Đa", "khu {}", "sang {} cũng được"),
    ("ROOM_TYPE", "chung cư mini", "{} thôi", "đổi qua {}"),
    ("ROOM_TYPE", "nhà nguyên căn", "chỉ lấy {}", "{} nhé"),
    ("ROOM_TYPE", "studio", "căn {}", "đổi sang {}"),
)
HOLDOUT = (
    ("UTILITY", "{}", "giờ giấc tự do"),
    ("UTILITY", "{} được không", "giờ giấc tự do"),
    ("UTILITY", "có {} thì tốt", "giờ giấc tự do"),
    ("UTILITY", "{}", "gio giac tu do"),
    ("UTILITY", "ưu tiên {}", "không quản giờ giấc"),
    ("UTILITY", "miễn {}", "được ra vào 24/24"),
    ("UTILITY", "{} ấy", "không có giờ đóng cổng"),
    ("UTILITY", "chọn chỗ {}", "tự do ra vào"),
    ("UTILITY", "thêm {} đi", "chỗ đỗ ô tô"),
    ("UTILITY", "cần {} cơ", "máy lạnh"),
    ("UTILITY", "{} nha chị", "cửa sổ lớn"),
    ("UTILITY", "ưu tiên {}", "ban công riêng"),
    ("UTILITY", "có {} với", "nồi nước nóng"),
    ("UTILITY", "{} là ổn", "bếp nấu riêng"),
    ("UTILITY", "có {} càng tốt", "bãi gửi xe"),
    ("UTILITY", "mình cần {}", "nhà tắm riêng"),
    ("UTILITY", "{} ạ", "nuôi được chó"),
    ("PRICE_MAX", "dưới {} thôi", "3tr8"),
    ("PRICE_MAX", "{} đổ về", "2 triệu 9"),
    ("PRICE_MIN", "tối thiểu {} ạ", "4tr2"),
    ("AREA_MIN", "{} trở lên ạ", "24m2"),
    ("LOCATION", "bên {} ấy", "Hoàng Mai"),
    ("LOCATION", "qua {} xem sao", "Cầu Giấy"),
    ("ROOM_TYPE", "thử loại {}", "căn hộ dịch vụ"),
)


def normalize(text):
    folded = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    return "".join(char for char in folded if char.isalnum() and not unicodedata.combining(char))


def annotate(label, template, value):
    before, after = template.split("{}")
    return {"text": before + value + after,
            "entities": [{"start": len(before), "end": len(before) + len(value), "label": label}]}


def read(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8-sig").splitlines()]


def write(path, rows):
    with path.open("w", encoding="utf-8", newline="\n") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mixed-output", type=Path, help="Write a fine-tuning set with replay from v1.")
    args = parser.parse_args()
    test_rows = [annotate(*example) for example in HOLDOUT]
    test_rows += [{"text": text, "entities": []} for text in (
        "lúc nào cũng được", "hẹn khi khác nhé", "rẻ nữa đi", "mình phải đi bây giờ",
        "giờ mới thấy tin", "có phòng nào nữa không", "cho xem ảnh nhé", "cảm ơn bạn",
    )]
    excluded = {normalize(row["text"]) for row in test_rows}
    for name in ("ner_test_gold.jsonl", "ner_test_real.jsonl", "ner_test_synthetic_challenge_v1.jsonl",
                 "nlu_test_synthetic_500.jsonl"):
        path = DATA / name
        if path.is_file():
            excluded.update(normalize(row["text"]) for row in read(path))

    candidates = [annotate("UTILITY", pattern, feature)
                  for feature in FEATURES for pattern in ("{}", "{} nhé", "thêm {}", "ưu tiên {}")]
    candidates += [annotate(label, pattern, value) for label, value, *patterns in NUMERIC for pattern in patterns]
    candidates += [{"text": text, "entities": []} for text in (
        "đổi ý rồi", "khu khác đi", "gửi thêm đi ạ", "xin chào", "xem ảnh với", "còn phòng không",
        "để mai nhắn", "mình đang rảnh", "giờ nói tiếp nhé", "tự do lựa chọn", "giá sao",
        "lúc nãy hỏi rồi", "được đấy", "có không", "bỏ hết tiêu chí cũ", "còn xa quá",
    )]
    train_rows = []
    for row in candidates:
        key = normalize(row["text"])
        if key not in excluded:
            train_rows.append(row)
            excluded.add(key)
    write(DATA / "ner_train_short_followups.jsonl", train_rows)
    write(DATA / "ner_test_short_followups.jsonl", test_rows)
    if args.mixed_output:
        rng = random.Random(42)
        replay = []
        for name, count in (("ner_train_focused_v1.jsonl", 440), ("ner_train_real.jsonl", 100)):
            eligible = []
            for row in read(DATA / name):
                key = normalize(row["text"])
                if key not in excluded:
                    eligible.append(row)
                    excluded.add(key)
            replay.extend(rng.sample(eligible, min(count, len(eligible))))
        mixed = train_rows * 2 + replay
        rng.shuffle(mixed)
        write(args.mixed_output, mixed)
        print(f"Short train: {len(train_rows)}, replay: {len(replay)}, mixed: {len(mixed)}, test: {len(test_rows)}")


if __name__ == "__main__":
    main()
