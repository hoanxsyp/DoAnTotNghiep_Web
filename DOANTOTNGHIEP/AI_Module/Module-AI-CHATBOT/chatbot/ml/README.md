# GĐ2 — Dataset + Huấn luyện PhoBERT (SPEC §11, §13)

Chuẩn bị dataset và huấn luyện 2 mô hình cho tầng NLU: **intent classifier**
(6 nhãn) và **NER** (10 loại entity, BIO).

Base model: **`vinai/phobert-base-v2`** (PhoBERT, VinAI). Chọn sau khi so sánh với
ViSoBERT (`uitnlp/visobert`, dùng 2026-09 → 2026-10) trên cùng dữ liệu, 3 seed,
không chọn checkpoint trên bộ test — `eval-results/backbone_compare_full.md`:

| | ViSoBERT | **PhoBERT** |
|---|---:|---:|
| Intent GOLD acc | 0.905 ± 0.021 | **0.929 ± 0.000** |
| Intent proxy acc | 0.721 | **0.770** |
| NER Span-F1 (proxy) | 0.595 | **0.745** |
| Latency CPU (gồm word-segment) | 82 ms | **61 ms** |

(Bảng trên đo lúc còn 8 intent; khoảng cách NER không do cách gán nhãn — ViSoBERT
gán nhãn theo từ y hệt PhoBERT chỉ đạt 0.631, xem `backbone_compare_visobert_word.md`.)

PhoBERT được pretrain trên văn bản **đã word-segment** nên pipeline có thêm bước
tách từ bằng **VnCoreNLP** (jar Java) — máy train và máy chạy `nlu-service` đều
cần Java.

## 6 intent

`search_room`, `refine_search`, `room_detail`, `compare_rooms`,
`book_appointment`, `out_of_scope`.

`calculate_cost` và `policy_inquiry` đã **gộp vào `room_detail`** (0 và 1 câu thật
— không đủ để học riêng; bản chất đều là hỏi thông tin một phòng). Template sinh
câu hỏi chi phí/quy định vẫn giữ, chỉ đổi nhãn (`MERGED_INTENTS` trong
`data/generate_dataset_v2.py`). Phía Java, `ChatOrchestrator` tách lại câu
`room_detail` có từ khóa chi phí/quy định để gọi handler riêng — người dùng không
mất tính năng.

## ⚠️ Đọc trước — về bộ test

`data/intent_test_real.jsonl` và `data/ner_test_real.jsonl` là bộ **PROXY** do người
phát triển tự viết tay — **KHÔNG PHẢI** dữ liệu thật thu thập như SPEC §13.3 yêu
cầu. Xem cảnh báo trong `data/build_proxy_testset.py`.

Bộ GOLD (`data/intent_test_gold.jsonl`) là data thật nhưng chỉ **28 câu** → 1 câu
= 3,6 điểm accuracy. Chênh lệch 1–2 câu giữa các lần train là **nhiễu**.

NER có bộ GOLD thật từ 2026-10-17 (`data/ner_test_gold.jsonl`): **cùng 28 câu**
với GOLD intent, **69 entity** → 1 entity ≈ 1,4 điểm F1. Nhãn: 11 câu bài đăng gán
bằng luật (`label_data_md.py`), 17 câu chat gán tay (`label_data2_ner.py`) — chưa
có người thứ hai rà độc lập.

## Cấu trúc

```
ml/
├── requirements.txt          # torch, transformers, datasets, evaluate, seqeval, py_vncorenlp
├── phobert_encoding.py       # encode PhoBERT (word-segment + gán nhãn theo từ) — dùng chung train + nlu-service
├── vncorenlp_util.py         # bọc VnCoreNLP, tách từ KÈM offset ký tự trên văn bản gốc
├── nlu_encoding.py           # gán/gộp BIO theo offset, span_prf, max_length (không phụ thuộc model)
├── vncorenlp/                # jar + model word-segment (tự tải lần đầu)
├── data/
│   ├── generate_dataset_v2.py   # synthetic văn phong thật → intent_train_v2 / ner_train_v2 (đang dùng)
│   ├── generate_dataset.py      # synthetic v1 (văn phong sạch, giữ để đối chứng)
│   ├── build_proxy_testset.py   # bộ PROXY tay viết
│   ├── label_data_md.py         # gán nhãn luật cho data.md (bài đăng) → *_data_md_labeled
│   ├── label_data2_ner.py       # gán nhãn NER TAY cho data2.md (câu chat) → ner_data2_labeled
│   └── split_real_data.py       # tách data thật → {intent,ner}_train_real + {intent,ner}_test_gold
├── train_intent.py           # sequence classification (SPEC §11 bước 2.1)
├── train_ner.py              # token classification / BIO (SPEC §11 bước 2.2)
├── compare_backbones.py      # so sánh PhoBERT / ViSoBERT (nhiều seed)
├── eval_nlu_compare.py       # bảng so sánh 3 phương án NLU (§14.4) → eval-results/REPORT.md
└── eval_local_intent_gold.py # intent trên GOLD không cần dựng service
```

## Cài đặt

Cần **Java** (JRE/JDK, có `JAVA_HOME`) cho VnCoreNLP.

```bash
cd ml
python -m venv .venv
source .venv/Scripts/activate        # Windows Git Bash; PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

VnCoreNLP tự tải jar + model lần đầu. **Đường dẫn chứa nó không được có dấu
cách** (lỗi của VnCoreNLP) — nếu repo nằm ở đường dẫn có dấu cách, nó tự chuyển
sang `%LOCALAPPDATA%\vncorenlp`; ghi đè bằng `VNCORENLP_DIR`.

## Chạy

```bash
# (tuỳ chọn) sinh lại dataset
python data/generate_dataset_v2.py
python data/build_proxy_testset.py
python data/split_real_data.py

# Smoke test
python train_intent.py --max-train 64 --epochs 1 --output-dir out-intent-smoke
python train_ner.py    --max-train 64 --epochs 1 --output-dir out-ner-smoke

# Full training — mặc định đã là cấu hình dùng cho model hiện tại
python train_intent.py      # 5 epoch, lr 2e-5, intent_train_v2 + thật ×10, ~5 phút GPU
python train_ner.py         # 8 epoch, lr 3e-5, ner_train_v2 + thật ×1,  ~10 phút GPU
```

- Lấy model ở **epoch cuối**, không lưu checkpoint, không chọn epoch theo bộ test
  (trước đây `load_best_model_at_end` chọn theo GOLD làm số đo lạc quan giả).
- `train_*.py` ghi `nlu_max_length` (128) và `nlu_encoding` (`phobert_wseg`) vào
  `config.json`. `nlu-service` đọc 2 khoá này: lệch `max_length` sẽ cắt câu không
  báo lỗi; sai `nlu_encoding` (vd. nạp model ViSoBERT cũ) bị từ chối lúc startup.
- Model lưu ở `out-intent/`, `out-ner/` (~515MB/cái, đã gitignore).

## Kết quả — model hiện tại (2026-10-17)

| Bộ đánh giá | Metric | Giá trị | Ngưỡng DoD |
|---|---|---|---|
| GOLD (data thật, 28 câu) | Accuracy | **0.929** (26/28) | DoD-1 ≥ 0.90 ✅ |
| GOLD | Macro-F1 | 0.916 | — |
| PROXY (tay viết, 142 câu) | Accuracy | 0.775 | — |
| NER GOLD (data thật, 28 câu / 69 entity) | Span-F1 strict | **0.935** (P 0.929 / R 0.942) | DoD-2 ≥ 0.85 ✅ |
| NER PROXY (87 câu) | Span-F1 strict | 0.759 (P 0.701 / R 0.827) | — |
| NER PROXY | Slot-F1 (sau chuẩn hoá) | 0.962 (đo với model NER trước) | — |
| Latency CPU qua nlu-service | TB / p95 | 87 ms / 107 ms | — |

2 câu GOLD còn sai: *"Còn ko em ở luôn ạ"* (gold `room_detail`, đoán
`refine_search` — thiếu intent kiểu `check_availability`, xem `TODO.md`) và *"em
cần vào luôn đầu tuần sau nhé mọi người"* (gold `refine_search`, đoán
`book_appointment`).

NER GOLD theo nhãn: LOCATION/POI/RADIUS/ROOM_TYPE 1.00, PRICE_MAX 0.95, ROOM_REF
0.91, UTILITY 0.89, DATETIME 0.82. Model NER trước (chưa có data2, nhãn data.md
lệch quy ước) đo trên **cùng GOLD** chỉ được **0.720** — dù nó đã thấy 11/28 câu
GOLD lúc train.

**Đã sửa quy ước nhãn `label_data_md.py` cho khớp bộ synthetic v2** (thứ model
học): khoảng giá "2tr5-3tr" là 1 span (trước chỉ giữ "3tr"), đuôi "/tháng" nằm
trong span, thêm ROOM_TYPE (trước không có nhãn này) và DATETIME "quay đầu"/"ở
luôn". Lỗi do lệch quy ước được phát hiện khi xem lỗi trên GOLD, nhưng cách sửa là
luật áp cho TOÀN BỘ data.md theo phân bố của v2, không chỉnh tay từng câu.

Log: `train-intent-phobert.log`, `train-ner-v3.log`. So với LLM: xem
`eval-results/REPORT.md`.

## Quyết định kỹ thuật đáng chú ý

- **Entity lưu theo offset ký tự** (`{"start","end","label"}`), quy sang BIO lúc
  train (`nlu_encoding.bio_labels_from_offsets`) — dataset độc lập với model.
- **Tách từ kèm offset** (`vncorenlp_util.segment_with_offsets`): dò từng ký tự
  của token đã segment trên văn bản gốc. VnCoreNLP vừa ghép ("cầu_giấy") vừa TÁCH
  NHỎ ("<5tr5" → "<" "5" "tr5") và chuẩn hoá vị trí dấu ("hòa" → "hoà") nên không
  đếm "_" được. Emoji (ngoài BMP) bị VnCoreNLP làm hỏng nên được thay bằng "□"
  (1 ký tự → 1 ký tự, giữ offset). Câu nào vẫn không dò được thì tách theo
  khoảng trắng thay vì chết.
- **Nhãn NER chỉ ở subword đầu mỗi từ** (cách chuẩn của PhoBERT NER). Ranh giới
  entity vì thế chỉ rơi đúng ranh giới từ: trần round-trip Span-F1 là 0.977
  (proxy) / 0.950 (56 câu thật). Phần còn lại là do model.
- **`max_length=128`** cho cả 2 model: 64 cắt mất phần cuối 10–20% câu thật
  (post Facebook dài); ở 128 còn 1/2785 câu bị cắt.
- **Interface `NluService` ở backend không đổi** (§9.1): `POST /nlu` trả span
  thô; chuẩn hoá giá trị là việc của `EntityNormalizer` phía Java.

## Việc còn lại

1. **GOLD NER nhỏ** (69 entity) và nhãn chưa được người thứ hai rà — nên nhờ
   một người khác rà `ner_data2_labeled.jsonl` + `ner_test_gold.jsonl`.
   Lỗi còn lại: DATETIME ("ở luôn" vs "vào ở luôn", "trước 12h"), UTILITY hiếm
   ("đủ đồ", "giờ giấc").
2. **Khoảng giá bị gộp 1 span**: "1tr5-2tr" ra 1 span `PRICE_MAX` — normalizer
   Java cần tách min/max.
3. **`UtilityNormalizer`** phía Java chỉ map 4 nhóm, data v2 có 51 biến thể tiện ích.
4. Thu thêm data thật, nhất là `out_of_scope` và câu hỏi tình trạng phòng.
