"""Đánh nhãn NER bằng TAY cho data2.md (dữ liệu THẬT — câu chat hội thoại).

Khác `label_data_md.py` (gazetteer + regex tự động): 87 câu chat này ngắn, nhiều
tham chiếu ("phòng này", "căn B") và thời gian nói miệng ("trưa mai", "6h30") mà
regex khó bắt đúng, nên mỗi câu được gán span bằng tay trong `ANNOTATIONS`.

Quy ước bám theo dữ liệu train (`ner_train_v2.jsonl`, `ner_data_md_labeled.jsonl`)
để nhãn test không lệch chuẩn với nhãn model đã học:
  - PRICE_MAX: chỉ phần số ("3tr5", "200-300k"), không kèm "dưới/thêm". Mọi mức
    giá nhắc tới đều là PRICE_MAX (PRICE_MIN chỉ dùng cho "từ X").
  - UTILITY: hiểu rộng — tiện ích + đặc điểm lọc được của phòng ("chung chủ",
    "nuôi mèo", "full đồ", "ngõ ô tô"). Tiện ích bị phủ định vẫn gán nhãn (như
    "thôi khỏi cần điều hòa" trong bộ train) — phủ định là việc của tầng sau.
  - ROOM_REF: tham chiếu tới MỘT phòng cụ thể ("phòng này", "căn B", "phòng
    đầu"). Cụm số nhiều/hỏi ("2 phòng này", "căn nào") KHÔNG gán.
  - DATETIME: mốc thời gian, bỏ từ ước lượng ("tầm 7h tối" -> "7h tối").
  - Địa danh chung chung ("chợ", "bến bus", "trường") KHÔNG gán POI.

Mỗi span ghi (cụm, nhãn); cụm được tìm theo RANH GIỚI TỪ, lần xuất hiện thứ
`occ` (mặc định 0) — có assert để offset luôn khớp văn bản gốc.

Xuất: ner_data2_labeled.jsonl : {"text", "entities":[{start,end,label}]}

    python data/label_data2_ner.py
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE / "intent_data2_labeled.jsonl"   # cùng 87 câu, cùng thứ tự với data2.md
OUT = HERE / "ner_data2_labeled.jsonl"

P, L, R, U, D, RAD = "PRICE_MAX", "LOCATION", "ROOM_REF", "UTILITY", "DATETIME", "RADIUS"

ANNOTATIONS = {
    # ---- refine_search
    "thôi xa tí cũng đc ạ, miễn phòng sạch sẽ": [],
    "khu đấy khó tìm quá thì quanh Mỹ Đình cũng ok bác nhé": [("Mỹ Đình", L)],
    "em tăng được lên 3tr5, có đồ cơ bản càng tốt ạ": [("3tr5", P), ("đồ cơ bản", U)],
    "không cần ban công đâu, có cửa sổ là được rồi": [("ban công", U), ("cửa sổ", U)],
    "à em cần thêm chỗ để xe nữa nhé mn": [("chỗ để xe", U)],
    "phòng bé tí cũng được, em ở có một mình thôi": [],
    "có điều hoà thì tốt, không có cũng ko sao ạ": [("điều hoà", U)],
    "em đổi sang tìm quanh Cầu Giấy nhé, bán kính tầm 3km": [("Cầu Giấy", L), ("bán kính tầm 3km", RAD)],
    "xa trường chút cũng được nhưng đừng sâu trong ngõ quá": [],
    "thôi bỏ tiêu chí máy giặt riêng ạ, dùng chung cũng oke": [("máy giặt riêng", U)],
    "em cần vào luôn đầu tuần sau nhé mọi người": [("đầu tuần sau", D)],
    "ưu tiên chỗ nào cho nuôi mèo với ạ": [("nuôi mèo", U)],
    "giờ giấc không cần tự do hẳn đâu, trước 12h là được": [("giờ giấc", U)],
    "phòng trống hay full đồ đều được nhé, giá hợp lý là ok": [("full đồ", U)],
    "nếu khó quá thì phòng chung chủ em cũng cân nhắc ạ": [("chung chủ", U)],
    "em chỉ cần nóng lạnh với điều hoà thôi, tủ giường tự sắm được": [
        ("nóng lạnh", U), ("điều hoà", U), ("tủ giường", U)],
    "ngân sách nhích thêm 200-300k cũng đc nhé bác": [("200-300k", P)],
    "không nhất thiết gần chợ đâu, gần bến bus là được ạ": [],
    "tầng cao cũng ok miễn có thang máy nha": [("thang máy", U)],
    "em tìm thêm cả bên Nam Từ Liêm nữa nhé mn": [("Nam Từ Liêm", L)],
    # ---- room_detail
    "phòng này còn không bác ơi": [("phòng này", R)],
    "cho em xin địa chỉ cụ thể với ạ": [],
    "phòng tầng mấy đấy bạn": [],
    "có cửa sổ không ạ hay phòng kín": [("cửa sổ", U)],
    "nhà vệ sinh riêng đúng ko bác": [("nhà vệ sinh riêng", U)],
    "ảnh này là ảnh phòng thật luôn à bạn": [],
    "cho em xin thêm cái video quay phòng được không": [],
    "phòng khoảng bao nhiêu mét thế ạ": [],
    "trong phòng có sẵn điều hoà chưa bác": [("điều hoà", U)],
    "đồ trong ảnh là để lại hết đúng không ạ": [],
    "ngõ ô tô vào được không bạn": [("ngõ ô tô", U)],
    "đi bộ ra đường lớn có xa lắm ko": [],
    "nhà này có thang máy không ạ": [("nhà này", R), ("thang máy", U)],
    "chỗ để xe có rộng không bác, em có 2 xe": [("chỗ để xe", U)],
    "phòng có bị nắng chiều không bạn": [],
    "ở đây có được nuôi mèo ko ạ": [("nuôi mèo", U)],
    "phòng này 2 người ở có chật lắm không": [("phòng này", R)],
    "khu này tối về có vắng không bác": [],
    "máy giặt dùng chung hay mỗi phòng một máy thế ạ": [("máy giặt", U)],
    "bếp nấu ở trong phòng luôn à bạn": [("bếp", U)],
    "Còn ko em ở luôn ạ": [("ở luôn", D)],
    "Phòng này có bếp riêng hay nấu chung ạ?": [("Phòng này", R), ("bếp riêng", U)],
    "Vcd b ơi, giá chuẩn kh vậy, 2tr7 mà to vậy á": [("2tr7", P)],
    "ib bạn ơi mình qua xem phòng luôn với": [],
    "Vsc hay vskk vậy b": [("Vsc", U), ("vskk", U)],
    "Còn p k ạ, vs a còn pass gì khác ko a": [],
    "Xuất hiện muộn tí ngon r. Mình vẫn còn hợp đồng nhà tí": [],
    # ---- compare_rooms
    "mn thấy căn nào ổn hơn ạ, em phân vân quá": [],
    "2 phòng này thì chốt phòng nào hợp lý hơn nhỉ": [],
    "căn A gần công ty hơn nhưng bé, căn B xa tí mà rộng": [("căn A", R), ("căn B", R)],
    "theo các bác thêm 500k lấy phòng có ban công đáng không": [("500k", P), ("ban công", U)],
    "một bên 3tr điện cao, một bên 3tr3 điện dân thì chọn bên nào ạ": [("3tr", P), ("3tr3", P)],
    "em ở 1 mình thì phòng đầu hay phòng sau hợp hơn": [("phòng đầu", R), ("phòng sau", R)],
    "phòng này đẹp hơn mà trong ngõ sâu quá, có nên lấy ko mn": [("phòng này", R)],
    "căn bên Cầu Giấy với căn Mỹ Đình thì khu nào dễ sống hơn ạ": [("Cầu Giấy", L), ("Mỹ Đình", L)],
    "1 phòng chung chủ nhưng rẻ, 1 phòng riêng chủ mà đắt hơn 700k": [
        ("chung chủ", U), ("riêng chủ", U), ("700k", P)],
    "các bác xem hộ em căn nào đỡ bí hơn với": [],
    "hai căn này tổng phí chắc chênh nhau nhiều không nhỉ": [],
    "căn đầu gần nhưng không có thang máy, căn sau xa hơn mà đủ đồ": [
        ("căn đầu", R), ("thang máy", U), ("căn sau", R), ("đủ đồ", U)],
    "nếu xác định ở lâu thì chọn phòng nào ổn hơn ạ": [],
    "em đi làm bên Duy Tân thì ở phòng nào tiện hơn": [("Duy Tân", L)],
    "phòng A mới hơn, phòng B rộng hơn, khó chọn ghê": [("phòng A", R), ("phòng B", R)],
    "các bác từng ở khu này xem bên nào đỡ tắc đường hơn": [],
    "một bên tầng 1 hơi ẩm, bên kia tầng 5 không thang máy, chọn gì giờ": [("thang máy", U)],
    "căn 3tr2 trống đồ với căn 3tr8 full đồ thì căn nào lời hơn": [
        ("3tr2", P), ("3tr8", P), ("full đồ", U)],
    "em ưu tiên yên tĩnh thì nên chọn cái nào mn": [],
    "2 chỗ đều ổn mà không biết bên nào an ninh hơn": [],
    # ---- book_appointment
    "chiều nay em qua xem được không bác": [("chiều nay", D)],
    "tầm 7h tối có ai mở cửa cho xem phòng ko ạ": [("7h tối", D)],
    "mai em qua xem nhé, bác rảnh khung nào": [("mai", D)],
    "trưa mai em tranh thủ chạy qua được không bạn": [("trưa mai", D)],
    "phòng còn thì cho em hẹn xem tối nay với": [("tối nay", D)],
    "cuối tuần em mới qua được, bác giữ lịch giúp em nhé": [("cuối tuần", D)],
    "khoảng 6h30 em tan làm, qua lúc đấy oke không ạ": [("6h30", D)],
    "sáng chủ nhật em ghé xem được ko bác": [("sáng chủ nhật", D)],
    "bác cho em xin cái lịch nào sớm nhất với": [],
    "lát tầm 8h em qua có muộn quá không": [("8h", D)],
    "mai bạn có ở nhà không, mình qua xem phòng tí": [("mai", D)],
    "em muốn xem trực tiếp rồi mới cọc ạ": [],
    "chiều thứ 7 em qua nhé, tầm 3h được không bác": [("chiều thứ 7", D), ("3h", D)],
    "bác gửi em định vị với, lát em chạy qua": [],
    "tối nay em qua luôn được thì tốt ạ": [("tối nay", D)],
    "mai em có việc gần đấy, tiện qua xem phòng được không": [("mai", D)],
    "em với bạn qua xem cùng có được ko bác": [],
    "khoảng 9h sáng mai em qua nhé, bác xác nhận giúp em": [("9h sáng mai", D)],
    "nếu phòng chưa có người cọc thì hẹn em chiều mai với": [("chiều mai", D)],
    "bác rảnh lúc nào báo em, em qua xem luôn ạ": [],
}


def locate(text, phrase, occ=0):
    """Offset của `phrase` theo ranh giới từ (tránh "3tr" khớp vào "3tr3")."""
    hits = [m for m in re.finditer(r"(?<!\w)" + re.escape(phrase) + r"(?!\w)", text)]
    assert len(hits) > occ, f"Khong thay {phrase!r} (lan {occ}) trong {text!r}"
    m = hits[occ]
    assert text[m.start():m.end()] == phrase
    return m.start(), m.end()


def label_row(text, spans):
    seen = {}
    entities = []
    for item in spans:
        phrase, label = item[0], item[1]
        occ = seen.get(phrase, 0)
        seen[phrase] = occ + 1
        s, e = locate(text, phrase, occ)
        entities.append({"start": s, "end": e, "label": label})
    entities.sort(key=lambda x: x["start"])
    for a, b in zip(entities, entities[1:]):
        assert a["end"] <= b["start"], f"Span chong lan trong {text!r}"
    return {"text": text, "entities": entities}


def main():
    with SRC.open(encoding="utf-8") as f:
        texts = [json.loads(line)["text"] for line in f]
    missing = [t for t in texts if t not in ANNOTATIONS]
    extra = [t for t in ANNOTATIONS if t not in set(texts)]
    assert not missing, f"Cau chua gan nhan: {missing}"
    assert not extra, f"Nhan cho cau khong co trong nguon: {extra}"

    rows = [label_row(t, ANNOTATIONS[t]) for t in texts]
    with OUT.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    from collections import Counter
    c = Counter(e["label"] for r in rows for e in r["entities"])
    print(f"{len(rows)} cau / {sum(c.values())} entity -> {OUT.name}")
    print(dict(c))


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    main()
