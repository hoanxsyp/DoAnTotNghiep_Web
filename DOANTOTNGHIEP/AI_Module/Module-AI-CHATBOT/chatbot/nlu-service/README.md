# nlu-service — NLU (ViSoBERT) qua FastAPI (SPEC §11 bước 2.3)

Bọc 2 model đã train ở GĐ2 (`ml/out-intent`, `ml/out-ner`) thành HTTP service.
Spring Boot gọi qua `LocalNluServiceImpl` (bước 2.4, @Primary): RestClient
timeout 300ms, service chết → fallback `LlmNluServiceImpl` → rule-based.
Cấu hình phía Spring: khối `roomfinder.nlu` trong `application.yml`
(`NLU_URL`, `NLU_ENABLED`).

## API

```
GET  /health   → {"status":"ok", ..., "max_length":{"intent":64,"ner":64}}
POST /nlu      {"text": "tìm phòng tầm 2tr ở cầu giấy"}
→ {
    "intent": "search_room",
    "confidence": 0.97,
    "entities": [
      {"label": "PRICE_MAX", "text": "2tr",      "start": 14, "end": 17, "score": 0.93},
      {"label": "LOCATION",  "text": "cầu giấy", "start": 20, "end": 28, "score": 0.92}
    ]
  }

POST /embed    {"texts": ["phòng yên tĩnh, mới xây", ...]}
→ {"vectors": [[...768d...], ...]}     # keepitreal/vietnamese-sbert
```

`max_length` trong `/health` đọc từ khoá `nlu_max_length` trong `config.json` của
model đã train (ghi đè bằng biến môi trường `NLU_MAX_LENGTH`). **Lệch giá trị này
so với lúc train sẽ cắt câu và tụt độ chính xác mà không báo lỗi**, nên kiểm
`/health` sau mỗi lần đổi model.

`/embed` phục vụ **semantic rerank** GĐ3 (SPEC §12.1): Spring gọi qua
`NluEmbeddingClient` để embed câu hỏi + mô tả phòng, rồi rerank bằng cosine trong
Java. Xem `SemanticRerankService` và README backend §1.

**Entity là span thô** (offset ký tự trên văn bản gốc) — service KHÔNG chuẩn hóa
giá trị ("2tr" → 2000000 là việc của `EntityNormalizer` phía Java, §3.3). Lý do:
giữ 1 nơi duy nhất biết luật chuẩn hóa; SPEC dòng 804 viết `normalize(entities)`
trong ví dụ Python nhưng backend đã có sẵn tầng normalizer đầy đủ, không nhân đôi.

## Chạy cục bộ (dev)

Dùng chung venv với `ml/` (đã có torch/transformers; cài thêm fastapi+uvicorn):

```bash
cd nlu-service
../ml/.venv/Scripts/pip install fastapi uvicorn   # 1 lần
../ml/.venv/Scripts/python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

**Không cần JDK** — ViSoBERT nhận văn bản thô, pipeline thuần Python. Khởi động
mất ~20s (nạp 2 model + SBERT). Yêu cầu `ml/out-intent`, `ml/out-ner` đã train
xong (xem `ml/README.md`).

Trỏ sang thư mục model khác (vd. bản smoke test) bằng biến môi trường:

```bash
NLU_INTENT_MODEL=../ml/out-intent-smoke NLU_NER_MODEL=../ml/out-ner-smoke uvicorn app:app --port 8000
```

## Chạy bằng Docker

```bash
# Compose đã gom về root repo; service tên `chatbot-nlu` (cổng ngoài 8003 → 8000)
cd ../../../..            # tới DOANTOTNGHIEP/ (nơi có docker-compose.yml)
docker compose up chatbot-nlu
```

Model mount qua volume `./ml/out-*` (không bake vào image — ~390MB/model). Image
không cần JRE (pipeline thuần Python) và cũng không cần `sentencepiece`/`protobuf`:
`ml/out-*` đã chứa `tokenizer.json` dạng fast do `tokenizer.save_pretrained()` lúc
train ghi ra.

## Smoke test — CHƯA CHẠY

Model chưa được train (`ml/out-*` còn trống) nên service chưa khởi động được.
Sau khi train (xem `ml/README.md`), chạy 10 câu tay viết đủ 8 intent rồi ghi lại:
tỉ lệ intent đúng + confidence, entity có bắt đúng câu không dấu ("ha dong",
"2tr5") hay không, và latency/request trên CPU.
