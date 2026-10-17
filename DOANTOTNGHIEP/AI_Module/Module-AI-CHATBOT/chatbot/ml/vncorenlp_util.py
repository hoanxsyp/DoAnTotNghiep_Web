"""Tiện ích dùng chung: bọc VnCoreNLP word-segmenter + tách từ có kèm offset ký tự.

SPEC §11 lưu ý: PhoBERT được pretrain trên văn bản ĐÃ word-segment (ghép âm tiết
bằng dấu "_"). Bỏ qua bước này làm F1 tụt vài điểm cho intent; với NER, việc
word-segment còn ảnh hưởng ranh giới token nên càng cần làm đúng.

`segment_with_offsets` trả về danh sách token đã segment KÈM offset trong VĂN BẢN
GỐC (chưa segment) — cần thiết để quy các entity (đã gán nhãn theo offset gốc
trong *_train.jsonl / *_test_real.jsonl) sang đúng token sau khi segment.

Cách quy offset: dò TỪNG KÝ TỰ của token đã segment trên văn bản gốc, bỏ qua
khoảng trắng ("_" trong token = khoảng trắng trong văn bản gốc).

Bản cũ (xoá ở commit ad27738) giả định VnCoreNLP chỉ GHÉP các từ-khoảng-trắng
nên đếm "_" + 1 từ gốc cho mỗi token. Sai: VnCoreNLP còn TÁCH NHỎ — dấu câu dính
chữ ("mèo," -> "mèo" ","), "<5tr5" -> "<" "5" "tr5", "m1/8" -> "m" "1/8" — nên
offset trượt dần về cuối câu (vd. "2tr5" ra 76-78 thay vì 78-82), nhãn NER của
PhoBERT cũ bị gán lệch chỗ.
"""

import unicodedata
from pathlib import Path

_SEGMENTER = None


_BASE_URL = "https://raw.githubusercontent.com/vncorenlp/VnCoreNLP/master"


def _download_wseg_only(save_dir: Path):
    """Tải tối thiểu cho annotator "wseg" (jar + wordsegmenter) bằng urllib —
    `py_vncorenlp.download_model()` gốc gọi `wget` qua os.system(), không có sẵn
    trên Windows, và còn tải cả model dep/ner/postagger không cần thiết cho wseg."""
    import urllib.request

    for sub in ("dep", "ner", "postagger", "wordsegmenter"):
        (save_dir / "models" / sub).mkdir(parents=True, exist_ok=True)

    files = [
        ("VnCoreNLP-1.2.jar", save_dir / "VnCoreNLP-1.2.jar"),
        ("models/wordsegmenter/vi-vocab", save_dir / "models/wordsegmenter/vi-vocab"),
        ("models/wordsegmenter/wordsegmenter.rdr", save_dir / "models/wordsegmenter/wordsegmenter.rdr"),
    ]
    for rel_url, dest in files:
        if dest.exists():
            continue
        print(f"Downloading {rel_url} ...")
        urllib.request.urlretrieve(f"{_BASE_URL}/{rel_url}", str(dest))


def _resolve_save_dir(save_dir: Path) -> Path:
    """Tránh thư mục có DẤU CÁCH trong đường dẫn.

    VnCoreNLP (phía Java) suy ra vị trí thư mục `models/` từ vị trí file
    VnCoreNLP-1.2.jar lấy qua URL của classloader — URL này ENCODE dấu cách
    thành "%20" nhưng VnCoreNLP dùng thẳng làm đường dẫn file, không decode
    lại. Nên nếu repo nằm ở đường dẫn có dấu cách (vd. "D:\\Code\\Java Code\\...")
    thì Java đi tìm "D:\\Code\\Java%20Code\\...\\wordsegmenter.rdr" và báo
    "is not found!". Lỗi nằm trong VnCoreNLP, không sửa được từ phía Python.

    Cách xử lý: khi save_dir có dấu cách, đặt jar + model vào thư mục cache
    không dấu cách (mặc định %LOCALAPPDATA%\\vncorenlp, ghi đè bằng biến môi
    trường VNCORENLP_DIR). Nội dung tải về giống hệt — chỉ đổi chỗ đặt.
    """
    import os

    override = os.environ.get("VNCORENLP_DIR")
    if override:
        return Path(override)

    if " " not in str(Path(save_dir).resolve()):
        return save_dir

    base = os.environ.get("LOCALAPPDATA") or os.path.expanduser("~")
    fallback = Path(base) / "vncorenlp"
    if " " in str(fallback.resolve()):
        raise RuntimeError(
            f"Khong tim duoc thu muc khong dau cach cho VnCoreNLP (thu: {fallback}). "
            "Dat bien moi truong VNCORENLP_DIR toi mot duong dan khong co dau cach."
        )
    return fallback


def get_segmenter(save_dir: Path):
    """Trả về VnCoreNLP segmenter đã sẵn sàng (tải model 1 lần nếu chưa có).

    LƯU Ý: `py_vncorenlp.VnCoreNLP.__init__` tự `os.chdir(save_dir)` và KHÔNG
    trả lại thư mục làm việc ban đầu — nếu không khôi phục, mọi đường dẫn
    tương đối gọi SAU lệnh này (vd. `--output-dir out-intent`) sẽ bị lệch vào
    trong `save_dir`. Ta khôi phục cwd ngay sau khi khởi tạo xong.
    """
    global _SEGMENTER
    if _SEGMENTER is not None:
        return _SEGMENTER

    import os

    import py_vncorenlp

    save_dir = _resolve_save_dir(Path(save_dir))
    save_dir.mkdir(parents=True, exist_ok=True)
    jar = save_dir / "VnCoreNLP-1.2.jar"
    if not jar.exists():
        _download_wseg_only(save_dir)

    cwd = os.getcwd()
    try:
        _SEGMENTER = py_vncorenlp.VnCoreNLP(save_dir=str(save_dir), annotators=["wseg"])
    finally:
        os.chdir(cwd)
    return _SEGMENTER


def bmp_safe(text: str) -> str:
    """Thay ký tự ngoài BMP (emoji 🏠📌...) bằng "□" — 1 ký tự thay 1 ký tự.

    VnCoreNLP (Java, UTF-16) làm hỏng ký tự ngoài BMP: "🏠" (U+1F3E0) trả về
    "". Thay trước khi segment để không lệch offset và dò khớp được.
    """
    return "".join(c if ord(c) <= 0xFFFF else "□" for c in text)


def segment_plain(segmenter, text: str) -> str:
    """Trả câu đã word-segment, ghép các câu con lại (dùng cho intent — không cần offset)."""
    sentences = segmenter.word_segment(bmp_safe(text))
    return " ".join(sentences)


def _base(ch: str) -> str:
    """Chữ cái gốc, bỏ dấu thanh: VnCoreNLP chuẩn hoá vị trí dấu ("hòa" -> "hoà",
    "hủy" -> "huỷ") — đổi ký tự nhưng giữ nguyên độ dài, nên so theo chữ gốc."""
    return unicodedata.normalize("NFD", ch)[0]


def segment_with_offsets(segmenter, text: str):
    """Trả list[(token, start, end)] — offset tính trên `text` GỐC (chưa segment).

    Ném ValueError nếu một ký tự của token không khớp văn bản gốc (VnCoreNLP đổi
    ký tự) — thà báo lỗi còn hơn gán nhãn lệch mà không ai biết.
    """
    text = bmp_safe(text)  # cùng độ dài với text gốc nên offset vẫn đúng
    seg_tokens = []
    for sent in segmenter.word_segment(text):
        seg_tokens.extend(t for t in sent.split(" ") if t)

    out = []
    pos = 0
    for tok in seg_tokens:
        start = None
        for ch in tok:
            if ch == "_" and (pos >= len(text) or text[pos] != "_"):
                continue  # "_" do segmenter chèn = khoảng trắng gốc
            while pos < len(text) and text[pos].isspace():
                pos += 1
            if pos >= len(text) or _base(text[pos]) != _base(ch):
                raise ValueError(f"Khong khop token {tok!r} tai vi tri {pos} trong {text!r}")
            if start is None:
                start = pos
            pos += 1
        if start is not None:
            out.append((tok, start, pos))
    return out
