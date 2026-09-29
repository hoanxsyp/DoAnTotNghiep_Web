"""Encode/decode dùng chung cho intent + NER — dùng bởi cả `train_*.py` lẫn `nlu-service`.

Base model là **ViSoBERT** (`uitnlp/visobert`) — XLM-R + SentencePiece, có fast
tokenizer. Hai điểm quyết định cách encode:

  1. **Đưa văn bản THÔ vào tokenizer.** SentencePiece tự tách subword; KHÔNG
     word-segment, không ghép âm tiết bằng dấu "_" (dạng "cầu_giấy" là OOV với
     vocab này).
  2. **Nhãn NER gán TRỰC TIẾP lên từng subword** theo `offset_mapping`, nên ranh
     giới entity nằm GIỮA một "từ" theo khoảng trắng vẫn biểu diễn được — cần
     thiết vì 7,5% entity trong `data/ner_*.jsonl` bị dấu câu dính vào token
     (vd. "khoảng 6tr," → PRICE_MAX chỉ là "6tr", không gồm dấu phẩy).

`max_length` lúc train được ghi vào `config.json` (`nlu_max_length`) để
`nlu-service` suy luận đúng bằng cách encode lúc train — xem `resolve_max_length`.
"""

import os

DEFAULT_BASE_MODEL = os.environ.get("NLU_BASE_MODEL", "uitnlp/visobert")

# Giữ 64 vì toàn bộ data synthetic/proxy đều dưới ngưỡng này (dài nhất 36 token).
# CẢNH BÁO nếu train trên dữ liệu THẬT: post Facebook dài hơn nhiều — đo trên
# repo này, 9,6% câu của `intent_train_real.jsonl` và 19,6% câu của
# `ner_data_md_labeled.jsonl` vượt 64 token (dài nhất 307), tức bị CẮT MẤT phần
# cuối. ViSoBERT cho tối đa 514 vị trí nên nâng `--max-length 128/256` là khả
# thi, và gần như miễn phí vì collator padding động theo câu dài nhất mỗi batch.
MAX_LENGTH = 64

CONFIG_MAX_LEN = "nlu_max_length"  # khoá trong config.json — train và inference PHẢI khớp


def resolve_max_length(config) -> int:
    """`max_length` mà model được train với — đọc từ config.json, ghi bởi `train_*.py`.

    Train ở 128 rồi suy luận ở 64 sẽ cắt mất phần cuối câu mà KHÔNG báo lỗi, nên
    giá trị này đi theo model chứ không hardcode ở service. Ghi đè bằng biến môi
    trường `NLU_MAX_LENGTH`.
    """
    override = os.environ.get("NLU_MAX_LENGTH")
    if override:
        return int(override)
    value = getattr(config, CONFIG_MAX_LEN, None)
    return int(value) if value else MAX_LENGTH


def tidy_span(text: str, start: int, end: int):
    """Co span về phần không phải khoảng trắng.

    Phòng trường hợp fast tokenizer gộp khoảng trắng đứng trước vào offset của
    token "▁xxx" — nếu không co lại thì span trả về sẽ lệch 1 ký tự so với nhãn
    gốc trong `data/ner_*.jsonl` (và trượt Span-F1 strict).
    """
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    return start, end


# Dấu câu / dấu phân cách — cắt khỏi 2 biên của span entity đã gộp. Ngoài dấu
# kết câu còn có "-", "/", "~" vì dữ liệu thật hay viết giá dạng khoảng hoặc
# xấp xỉ ("1tr5-2tr", "2tr5/tháng", "~2tr5") và SentencePiece thường gộp dấu đó
# vào cùng subword với chữ số.
# KHÔNG gồm ngoặc/ngoặc kép: một POI hợp lệ có thể kết thúc bằng ")"
# (vd. "ĐH Bách Khoa (HN)"), cắt đi sẽ làm span sai.
_ENTITY_TRIM = ",.;:!?-/~"


def tidy_entity_span(text: str, start: int, end: int):
    """Như `tidy_span` nhưng cắt thêm dấu câu ở 2 biên của span ENTITY đã gộp.

    Cần vì SentencePiece đôi khi sinh subword VẮT QUA ranh giới entity — vd.
    "tối đa 2tr8, diện tích..." tách thành "▁2","tr","8," nên token cuối chứa cả
    "8" (thuộc PRICE_MAX) lẫn dấu phẩy (không thuộc). Token đó overlap entity
    nên được gán nhãn, làm span dài thêm 1 ký tự và trượt Span-F1 strict.
    An toàn với bộ nhãn hiện tại: 0/3977 entity trong `data/ner_*.jsonl` có biên
    là ký tự không alphanumeric (riêng "²" của "22m²" được coi là alnum).
    """
    start, end = tidy_span(text, start, end)
    while start < end and text[start] in _ENTITY_TRIM:
        start += 1
    while end > start and text[end - 1] in _ENTITY_TRIM:
        end -= 1
    return tidy_span(text, start, end)


def encode_with_offsets(tokenizer, text: str, max_length: int = MAX_LENGTH):
    """Encode văn bản THÔ; trả `(enc, spans)`.

    `spans[i]` = `(start, end)` trên `text` gốc của subword thứ i, hoặc `None`
    với token đặc biệt (`<s>`, `</s>`, pad) và token rỗng/toàn khoảng trắng —
    những vị trí không mang nhãn.
    """
    enc = tokenizer(
        text,
        truncation=True,
        max_length=max_length,
        return_offsets_mapping=True,
        return_special_tokens_mask=True,
    )
    spans = []
    for (start, end), special in zip(enc["offset_mapping"], enc["special_tokens_mask"]):
        if special or start >= end:
            spans.append(None)
            continue
        start, end = tidy_span(text, start, end)
        spans.append((start, end) if start < end else None)
    return enc, spans


def bio_labels_from_offsets(spans, entities, label2id):
    """Gán nhãn BIO cho TỪNG subword dựa trên overlap với entity span.

    Subword đầu tiên chồng lấn một entity nhận `B-`, các subword sau nhận `I-`
    (gán nhãn mọi subword, không chỉ subword đầu của từ — nhờ vậy lúc suy luận
    đọc thẳng nhãn ở mọi vị trí, không cần biết ranh giới "từ").
    Vị trí `None` nhận `-100` để loss bỏ qua.
    """
    labels = [-100 if span is None else label2id["O"] for span in spans]
    for ent in entities:
        first = True
        for i, span in enumerate(spans):
            if span is None:
                continue
            start, end = span
            if start < ent["end"] and end > ent["start"]:  # overlap
                labels[i] = label2id[("B-" if first else "I-") + ent["label"]]
                first = False
    return labels


def decode_bio_offsets(text: str, spans, labels, scores=None):
    """Gộp nhãn BIO theo subword thành span ký tự trên `text` gốc.

    Trả `[{"label","text","start","end","score"}]`. Chấp nhận `I-X` mở đầu một
    entity (model đôi khi bỏ lỡ `B-`) — ưu tiên recall hơn đúng chuẩn BIO.
    """
    out = []
    cur = None

    def flush():
        nonlocal cur
        if cur is None:
            return
        start, end = tidy_entity_span(text, cur["start"], cur["end"])
        if start < end:
            out.append({
                "label": cur["label"],
                "text": text[start:end],
                "start": start,
                "end": end,
                "score": sum(cur["scores"]) / len(cur["scores"]),
            })
        cur = None

    for i, span in enumerate(spans):
        if span is None:
            continue  # token đặc biệt: bỏ qua, KHÔNG cắt entity đang mở
        label = labels[i]
        score = 1.0 if scores is None else scores[i]
        if label == "O":
            flush()
            continue
        prefix, ent_type = label.split("-", 1)
        # `B-X` GIỮA TỪ được coi là tiếp tục span đang mở, không mở span mới: vì
        # decode ở mức SUBWORD, một nhãn B- model nhả sai giữa từ sẽ xé
        # "Hoàng Mai" thành 2 span rác "Ho" + "àng". "Giữa từ" = span dính liền
        # span trước, không có khoảng trắng chen giữa (`tidy_span` đã cắt khoảng
        # trắng khỏi mỗi subword nên 2 subword cách nhau bởi space sẽ KHÔNG liền
        # nhau). An toàn với dữ liệu hiện có: 0/3977 entity nằm liền nhau trong
        # cùng một từ, nên không có cặp entity cùng nhãn nào bị gộp oan (các ca
        # "1tr5-2tr" chỉ gán nhãn 1 entity).
        continues = cur is not None and cur["label"] == ent_type and (
            prefix == "I" or span[0] == cur["end"]
        )
        if continues:
            cur["end"] = span[1]
            cur["scores"].append(score)
        else:
            flush()
            cur = {"label": ent_type, "start": span[0], "end": span[1], "scores": [score]}
    flush()
    return out
