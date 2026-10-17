"""Encode PhoBERT cho intent + NER — dùng chung bởi `train_*.py`, `compare_backbones.py`
và `nlu-service`, để train và suy luận encode Y HỆT nhau.

PhoBERT (`vinai/phobert-base-v2`) được pretrain trên văn bản ĐÃ word-segment
(âm tiết ghép bằng "_", vd. "cầu_giấy"), nên:

  1. Câu được word-segment bằng VnCoreNLP (`vncorenlp_util.py`) trước khi đưa vào
     tokenizer BPE. Mỗi từ kèm offset ký tự trên văn bản GỐC.
  2. NER gán nhãn BIO cho subword ĐẦU của mỗi từ, các subword sau = -100 (cách
     chuẩn của PhoBERT NER). `spans[i]` = offset của từ chứa subword i, hoặc None
     — cùng định dạng với `nlu_encoding.encode_with_offsets` nên
     `bio_labels_from_offsets` / `decode_bio_offsets` dùng lại được nguyên.

Ranh giới entity vì thế chỉ rơi đúng ranh giới TỪ của VnCoreNLP; đo trên data
hiện có, trần round-trip Span-F1 là 0.977 (proxy) / 0.950 (56 câu thật) — xem
`eval-results/backbone_compare_full.md`.

Tokenizer PhoBERT là tokenizer "slow" (không có offset_mapping) — offset lấy từ
VnCoreNLP chứ không từ tokenizer.
"""

import os
import re
import threading
from pathlib import Path

from vncorenlp_util import get_segmenter, segment_with_offsets

ROOT = Path(__file__).parent
VNCORENLP_DIR = Path(os.environ.get("VNCORENLP_DIR", ROOT / "vncorenlp"))

# Khoá ghi vào config.json lúc train để nlu-service kiểm tra đúng cách encode —
# nạp nhầm model ViSoBERT cũ vào pipeline PhoBERT sẽ ra kết quả rác mà không lỗi.
CONFIG_ENCODING = "nlu_encoding"
ENCODING_NAME = "phobert_wseg"

_WS_WORD = re.compile(r"\S+")
# VnCoreNLP là 1 instance dùng chung (singleton trong vncorenlp_util), chạy trong
# JVM qua pyjnius; FastAPI gọi endpoint sync từ nhiều thread nên khoá lại.
_SEG_LOCK = threading.Lock()


class PhoBertEncoder:
    def __init__(self, tokenizer, segmenter, cache=True):
        self.tok = tokenizer
        self.seg = segmenter
        # Cache chỉ dùng lúc train (mỗi câu được encode nhiều lần). Service tắt đi
        # để bộ nhớ không phình theo số câu người dùng gửi.
        self._cache = {} if cache else None
        self.fallbacks = 0

    @classmethod
    def from_pretrained(cls, model_name_or_dir, cache=True):
        from transformers import AutoTokenizer

        return cls(AutoTokenizer.from_pretrained(model_name_or_dir),
                   get_segmenter(VNCORENLP_DIR), cache=cache)

    def words(self, text):
        """list[(từ_đã_segment, start, end)] — offset trên `text` gốc."""
        if self._cache is not None and text in self._cache:
            return self._cache[text]
        try:
            with _SEG_LOCK:
                out = segment_with_offsets(self.seg, text)
        except ValueError:
            # VnCoreNLP đổi ký tự lạ làm không dò lại được offset: tách theo
            # khoảng trắng thay vì chết (mất lợi ích word-segment cho câu này).
            self.fallbacks += 1
            out = [(m.group(), m.start(), m.end()) for m in _WS_WORD.finditer(text)]
        if self._cache is not None:
            self._cache[text] = out
        return out

    def segmented(self, text):
        return " ".join(w for w, _, _ in self.words(text))

    def _pieces(self, word):
        ids = self.tok.convert_tokens_to_ids(self.tok.tokenize(word))
        return ids or [self.tok.unk_token_id]

    def n_tokens(self, text):
        return 2 + sum(len(self._pieces(w)) for w, _, _ in self.words(text))

    def intent(self, text, max_len):
        """`(input_ids, attention_mask)` của câu đã word-segment."""
        enc = self.tok(self.segmented(text), truncation=True, max_length=max_len)
        return enc["input_ids"], enc["attention_mask"]

    def ner(self, text, max_len):
        """`(input_ids, attention_mask, spans)`; chỉ subword đầu mỗi từ có span."""
        ids, spans = [self.tok.cls_token_id], [None]
        for word, start, end in self.words(text):
            for j, pid in enumerate(self._pieces(word)):
                ids.append(pid)
                spans.append((start, end) if j == 0 else None)
        ids = ids[: max_len - 1] + [self.tok.sep_token_id]
        spans = spans[: max_len - 1] + [None]
        return ids, [1] * len(ids), spans
