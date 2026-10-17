"""NLU service (FastAPI) — SPEC §11 bước 2.3.

Bọc 2 model PhoBERT đã train ở GĐ2 (`ml/out-intent`, `ml/out-ner`) thành HTTP
service cho Spring Boot gọi qua `LocalNluServiceImpl` (bước 2.4). Hợp đồng:

    POST /nlu  {"text": "tìm phòng tầm 2tr ở cầu giấy"}
    → {"intent": "search_room", "confidence": 0.99,
       "entities": [{"label": "PRICE_MAX", "text": "2tr", "start": 14, "end": 17,
                     "score": 0.98}, ...]}

Entity trả về là SPAN THÔ theo offset ký tự trên văn bản gốc — KHÔNG chuẩn hóa
giá trị ở đây. Việc quy "2tr" → 2_000_000, "cầu giấy" → "Cầu Giấy"... thuộc tầng
`EntityNormalizer` phía Java (§3.3) — giữ 1 nơi duy nhất biết luật chuẩn hóa,
service này chỉ làm đúng phần mô hình.

Suy luận phải LẶP LẠI đúng cách encode lúc train (xem `ml/phobert_encoding.py`):
word-segment bằng VnCoreNLP (cần Java) → mỗi từ tách BPE → nhãn NER đọc ở
subword ĐẦU mỗi từ → gộp BIO thành span theo offset ký tự. `max_length` và cách
encode đọc từ `config.json` của model (khoá `nlu_max_length`, `nlu_encoding`, do
`train_*.py` ghi) — model không khớp `nlu_encoding` (vd. ViSoBERT cũ) bị từ chối
lúc startup thay vì âm thầm ra kết quả rác.

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
    VNCORENLP_DIR      nơi có VnCoreNLP-1.2.jar + models/ (mặc định $NLU_ML_DIR/vncorenlp;
                       thiếu thì tự tải). Đường dẫn KHÔNG được có dấu cách.
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
from nlu_encoding import decode_bio_offsets, resolve_max_length  # noqa: E402
from phobert_encoding import CONFIG_ENCODING, ENCODING_NAME, PhoBertEncoder  # noqa: E402

_M = {}  # models, nạp 1 lần lúc startup


@asynccontextmanager
async def lifespan(app: FastAPI):
    from sentence_transformers import SentenceTransformer
    from transformers import AutoModelForSequenceClassification, AutoModelForTokenClassification

    # Nạp trong try/except: thiếu/hỏng model thì service vẫn LÊN nhưng ở trạng
    # thái "degraded" (mọi request trả 503 kèm lý do) thay vì chết lúc startup.
    # Quan trọng vì `ml/out-*` có thể còn trống khi chưa train.
    try:
        torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
        _M["intent_model"] = AutoModelForSequenceClassification.from_pretrained(INTENT_DIR).eval()
        _M["ner_model"] = AutoModelForTokenClassification.from_pretrained(NER_DIR).eval()
        for path, model in ((INTENT_DIR, _M["intent_model"]), (NER_DIR, _M["ner_model"])):
            got = getattr(model.config, CONFIG_ENCODING, None)
            if got != ENCODING_NAME:
                raise RuntimeError(
                    f"{path}: config.json co {CONFIG_ENCODING}={got!r}, can {ENCODING_NAME!r}. "
                    "Model khong phai PhoBERT train boi ml/train_*.py hien tai — train lai."
                )
        # max_length phải khớp lúc train, nếu không sẽ cắt câu mà không báo lỗi.
        _M["intent_len"] = resolve_max_length(_M["intent_model"].config)
        _M["ner_len"] = resolve_max_length(_M["ner_model"].config)
        # 2 encoder dùng chung 1 VnCoreNLP (singleton, 1 JVM). Tắt cache: câu
        # người dùng gần như không lặp, cache chỉ làm bộ nhớ phình dần.
        _M["intent_enc"] = PhoBertEncoder.from_pretrained(INTENT_DIR, cache=False)
        _M["ner_enc"] = PhoBertEncoder.from_pretrained(NER_DIR, cache=False)
        _M["embed_model"] = SentenceTransformer(EMBED_MODEL)
        # Warm-up: request đầu tiên mất ~1.5s (JVM của VnCoreNLP + PyTorch khởi
        # tạo lười), vượt timeout 300ms phía Spring -> làm 1 lượt ngay lúc startup.
        classify_intent("tìm phòng tầm 2tr ở cầu giấy")
        extract_entities("tìm phòng tầm 2tr ở cầu giấy")
        _M["ready"] = True
        print(f"[NLU] san sang | max_length: intent={_M['intent_len']} ner={_M['ner_len']}")
    except Exception as exc:
        _M["ready"] = False
        _M["startup_error"] = str(exc)
        print(f"[NLU] KHOI DONG LOI -> /health = 'degraded', moi request tra 503: {exc}")
    yield
    _M.clear()


app = FastAPI(title="RoomFinder NLU (PhoBERT)", lifespan=lifespan)


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
    ids, mask = _M["intent_enc"].intent(text, _M["intent_len"])
    with torch.no_grad():
        logits = _M["intent_model"](input_ids=torch.tensor([ids]),
                                    attention_mask=torch.tensor([mask])).logits[0]
    probs = torch.softmax(logits, dim=-1)
    idx = int(probs.argmax())
    return _M["intent_model"].config.id2label[idx], float(probs[idx])


def extract_entities(text: str) -> list[EntitySpan]:
    """Nhãn đọc ở subword ĐẦU mỗi từ (span None = bỏ qua); gộp BIO thành span ký tự."""
    ids, mask, spans = _M["ner_enc"].ner(text, _M["ner_len"])
    inputs = {"input_ids": torch.tensor([ids]), "attention_mask": torch.tensor([mask])}
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
