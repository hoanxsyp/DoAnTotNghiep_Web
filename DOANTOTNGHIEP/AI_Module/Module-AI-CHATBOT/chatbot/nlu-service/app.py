"""NLU service (FastAPI) — SPEC §11 bước 2.3.

Bọc 2 model ViSoBERT đã train ở GĐ2 (`ml/out-intent`, `ml/out-ner`) thành HTTP
service cho Spring Boot gọi qua `LocalNluServiceImpl` (bước 2.4). Hợp đồng:

    POST /nlu  {"text": "tìm phòng tầm 2tr ở cầu giấy"}
    → {"intent": "search_room", "confidence": 0.99,
       "entities": [{"label": "PRICE_MAX", "text": "2tr", "start": 14, "end": 17,
                     "score": 0.98}, ...]}

Entity trả về là SPAN THÔ theo offset ký tự trên văn bản gốc — KHÔNG chuẩn hóa
giá trị ở đây. Việc quy "2tr" → 2_000_000, "cầu giấy" → "Cầu Giấy"... thuộc tầng
`EntityNormalizer` phía Java (§3.3) — giữ 1 nơi duy nhất biết luật chuẩn hóa,
service này chỉ làm đúng phần mô hình.

Suy luận phải LẶP LẠI đúng cách encode lúc train (xem `ml/nlu_encoding.py`):
văn bản THÔ → fast tokenizer với `offset_mapping` → nhãn NER nằm trên TỪNG
subword → gộp BIO thành span. `max_length` đọc từ `config.json` của model
(khoá `nlu_max_length`, do `train_*.py` ghi) nên không cần cấu hình tay.

Nếu nạp model lỗi (vd. `ml/out-*` còn trống vì chưa train), service vẫn LÊN nhưng
`GET /health` trả `status: "degraded"` kèm `startup_error`, và `/nlu` + `/embed` trả
**503**. Spring bắt lỗi này như mọi lỗi khác → fallback LLM → rule-based.

Chạy:
    uvicorn app:app --host 0.0.0.0 --port 8000
Biến môi trường (mặc định chạy được ngay từ repo):
    NLU_ML_DIR         (mặc định ../ml)   — nơi có nlu_encoding.py
    NLU_INTENT_MODEL   (mặc định $NLU_ML_DIR/out-intent)
    NLU_NER_MODEL      (mặc định $NLU_ML_DIR/out-ner)
    NLU_MAX_LENGTH     ghi đè max_length cho CẢ 2 model
"""

import os
import sys
from contextlib import asynccontextmanager
from pathlib import Path

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

ROOT = Path(__file__).parent
ML_DIR = Path(os.environ.get("NLU_ML_DIR", ROOT.parent / "ml"))
INTENT_DIR = os.environ.get("NLU_INTENT_MODEL", str(ML_DIR / "out-intent"))
NER_DIR = os.environ.get("NLU_NER_MODEL", str(ML_DIR / "out-ner"))
# Embedding cho semantic rerank (GĐ3, SPEC §12.1) — SBERT tự tokenize theo BPE
# riêng, độc lập với model NLU.
EMBED_MODEL = os.environ.get("NLU_EMBED_MODEL", "keepitreal/vietnamese-sbert")

sys.path.insert(0, str(ML_DIR))
from nlu_encoding import (  # noqa: E402
    decode_bio_offsets,
    encode_with_offsets,
    resolve_max_length,
)

_M = {}  # models, nạp 1 lần lúc startup


@asynccontextmanager
async def lifespan(app: FastAPI):
    from sentence_transformers import SentenceTransformer
    from transformers import (
        AutoModelForSequenceClassification,
        AutoModelForTokenClassification,
        AutoTokenizer,
    )

    # Nạp trong try/except: thiếu/hỏng model thì service vẫn LÊN nhưng ở trạng
    # thái "degraded" (mọi request trả 503 kèm lý do) thay vì chết lúc startup.
    # Quan trọng vì `ml/out-*` có thể còn trống khi chưa train.
    try:
        torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
        _M["intent_tok"] = AutoTokenizer.from_pretrained(INTENT_DIR)
        _M["intent_model"] = AutoModelForSequenceClassification.from_pretrained(INTENT_DIR).eval()
        _M["ner_tok"] = AutoTokenizer.from_pretrained(NER_DIR)
        _M["ner_model"] = AutoModelForTokenClassification.from_pretrained(NER_DIR).eval()
        # max_length phải khớp lúc train, nếu không sẽ cắt câu mà không báo lỗi.
        _M["intent_len"] = resolve_max_length(_M["intent_model"].config)
        _M["ner_len"] = resolve_max_length(_M["ner_model"].config)
        if not _M["ner_tok"].is_fast:
            raise RuntimeError(
                f"{NER_DIR}: can fast tokenizer de lay offset_mapping. Thu muc model "
                "phai co tokenizer.json (tokenizer.save_pretrained() luc train ghi ra)."
            )
        _M["embed_model"] = SentenceTransformer(EMBED_MODEL)
        _M["ready"] = True
        print(f"[NLU] san sang | max_length: intent={_M['intent_len']} ner={_M['ner_len']}")
    except Exception as exc:
        _M["ready"] = False
        _M["startup_error"] = str(exc)
        print(f"[NLU] KHOI DONG LOI -> /health = 'degraded', moi request tra 503: {exc}")
    yield
    _M.clear()


app = FastAPI(title="RoomFinder NLU (ViSoBERT)", lifespan=lifespan)


class NluRequest(BaseModel):
    text: str


class EntitySpan(BaseModel):
    label: str
    text: str
    start: int
    end: int
    score: float


class NluResponse(BaseModel):
    intent: str
    confidence: float
    entities: list[EntitySpan]


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]


@app.get("/health")
def health():
    return {
        "status": "ok" if _M.get("ready") else "degraded",
        "intent_model": INTENT_DIR,
        "ner_model": NER_DIR,
        "embed_model": EMBED_MODEL,
        "startup_error": _M.get("startup_error"),
        # Lộ ra để debug: sai max_length là cắt câu mà không báo lỗi.
        "max_length": {"intent": _M.get("intent_len"), "ner": _M.get("ner_len")},
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    """Embedding cho semantic rerank (GĐ3, SPEC §12.1). Dùng cho cả mô tả phòng
    (tính 1 lần, cache ở backend) lẫn câu hỏi người dùng (tính mỗi lượt cần rerank)."""
    ensure_ready()
    vec = _M["embed_model"].encode(req.text.strip() or " ", normalize_embeddings=True)
    return EmbedResponse(vector=vec.tolist())


@app.post("/nlu", response_model=NluResponse)
def nlu(req: NluRequest) -> NluResponse:
    ensure_ready()
    text = req.text.strip()
    if not text:
        return NluResponse(intent="out_of_scope", confidence=1.0, entities=[])
    intent, confidence = classify_intent(text)
    entities = extract_entities(text)
    return NluResponse(intent=intent, confidence=confidence, entities=entities)


def ensure_ready():
    if not _M.get("ready"):
        raise HTTPException(status_code=503, detail={
            "message": "NLU models are not ready",
            "startup_error": _M.get("startup_error"),
        })


def classify_intent(text: str) -> tuple[str, float]:
    enc = _M["intent_tok"](text, truncation=True, max_length=_M["intent_len"], return_tensors="pt")
    with torch.no_grad():
        logits = _M["intent_model"](**enc).logits[0]
    probs = torch.softmax(logits, dim=-1)
    idx = int(probs.argmax())
    return _M["intent_model"].config.id2label[idx], float(probs[idx])


def extract_entities(text: str) -> list[EntitySpan]:
    """Nhãn nằm trên TỪNG subword; gộp BIO theo offset ký tự thành span."""
    enc, spans = encode_with_offsets(_M["ner_tok"], text, _M["ner_len"])
    inputs = {
        "input_ids": torch.tensor([enc["input_ids"]]),
        "attention_mask": torch.tensor([enc["attention_mask"]]),
    }
    with torch.no_grad():
        logits = _M["ner_model"](**inputs).logits[0]
    probs = torch.softmax(logits, dim=-1)

    id2label = _M["ner_model"].config.id2label
    labels, scores = [], []
    for i in range(len(spans)):
        idx = int(probs[i].argmax())
        labels.append(id2label[idx])
        scores.append(float(probs[i][idx]))
    return [EntitySpan(**e) for e in decode_bio_offsets(text, spans, labels, scores)]
