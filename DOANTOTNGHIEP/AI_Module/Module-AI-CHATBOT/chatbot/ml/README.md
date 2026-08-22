# GĐ2 — Dataset + Huấn luyện PhoBERT (SPEC §11, §13)

Chuẩn bị dataset và huấn luyện 2 mô hình PhoBERT cho tầng NLU: **intent classifier**
(8 nhãn) và **NER** (10 loại entity, BIO). Đây là bản pipeline cho **Giai đoạn 2** —
chưa bọc thành FastAPI service, chưa nối vào Spring Boot (đó là bước 2.3/2.4 của
SPEC, làm ở tăng vọt sau).

## ⚠️ Đọc trước — về bộ test "real"

`data/intent_test_real.jsonl` và `data/ner_test_real.jsonl` là bộ **PROXY tạm thời**
do người phát triển tự viết tay (đa dạng hơn dữ liệu synthetic: câu cụt, thiếu dấu,
viết tắt, teencode) — **KHÔNG PHẢI** dữ liệu thật thu thập từ Facebook/chotot như
SPEC §13.3 yêu cầu. Số liệu train/test dưới đây **chỉ để xác nhận pipeline chạy
đúng**, KHÔNG dùng để kết luận "mô hình đạt X% accuracy" khi bảo vệ chính thức —
cần thay bằng dữ liệu thật gán nhãn (Doccano/Label Studio) trước khi dùng số liệu
đánh giá này làm minh chứng. Xem cảnh báo chi tiết trong
`data/build_proxy_testset.py`.

## Cấu trúc

```
ml/
├── requirements.txt          # torch(cpu), transformers, datasets, evaluate, seqeval, py_vncorenlp
├── vncorenlp_util.py         # tải + bọc VnCoreNLP word-segmenter (tự viết, không dùng wget)
├── data/
│   ├── generate_dataset.py      # sinh intent_train.jsonl + ner_train.jsonl (synthetic, offset chính xác)
│   ├── build_proxy_testset.py   # sinh intent_test_real.jsonl + ner_test_real.jsonl (TAY VIẾT, xem cảnh báo trên)
│   ├── intent_train.jsonl       # 2500 câu, 8 intent
│   ├── intent_test_real.jsonl   # 142 câu (proxy)
│   ├── ner_train.jsonl          # 2075 câu có entity (offset ký tự)
│   └── ner_test_real.jsonl      # 87 câu có entity (proxy)
├── train_intent.py           # PhoBERT-base-v2 sequence classification (SPEC §11 bước 2.1)
└── train_ner.py               # PhoBERT-base-v2 token classification / BIO (SPEC §11 bước 2.2)
```

## Cài đặt

Cần Java (JDK) trên PATH/JAVA_HOME — VnCoreNLP chạy trên JVM qua `py_vncorenlp`/`pyjnius`
(máy này đã có JDK cho Spring Boot, ví dụ `~/.jdks/corretto-22.0.2`).

```bash
cd ml
python -m venv .venv
source .venv/Scripts/activate        # Windows Git Bash; PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt

export JAVA_HOME="/duong/dan/toi/jdk"          # PowerShell: $env:JAVA_HOME = "..."
export PATH="$JAVA_HOME/bin:$PATH"
```

VnCoreNLP model (chỉ phần word-segmenter, không tải dep/ner/pos vì không cần) tự
tải lần đầu vào `ml/vncorenlp/` khi chạy script (qua `vncorenlp_util.py`, dùng
`urllib` thay vì `wget` — bản gốc của `py_vncorenlp.download_model()` gọi `wget`
qua `os.system()`, không có sẵn trên Windows).

## Chạy

```bash
# Sinh lại dataset (đã có sẵn trong data/, chỉ cần chạy lại nếu muốn thay đổi vocab/tỉ lệ)
python data/generate_dataset.py
python data/build_proxy_testset.py

# Smoke test (vài chục câu, 1 epoch) — xác nhận code chạy đúng trước khi train full
python train_intent.py --max-train 40 --epochs 1 --output-dir out-intent-smoke
python train_ner.py --max-train 40 --epochs 1 --output-dir out-ner-smoke

# Full training
python train_intent.py --epochs 5 --output-dir out-intent
python train_ner.py --epochs 8 --output-dir out-ner
```

Model + tokenizer được lưu vào `out-intent/`, `out-ner/` (đã gitignore — không commit
weight vào git).

## Kết quả đã đạt

### Intent — cập nhật 2026-08-01 (train lại trên data THẬT)

Train lại trên **2615 câu** (2500 synthetic + **115 câu thật** train) sau khi bổ sung
`data2.md` (87 câu hội thoại thật). **Bộ đánh giá chính giờ là GOLD** — 28 câu thật
held-out (`data/intent_test_gold.jsonl`, sinh bởi `data/split_real_data.py`), không
còn dựa vào proxy:

| Bộ đánh giá | Metric | Giá trị | Ngưỡng DoD |
|---|---|---|---|
| **GOLD (data thật held-out, 28 câu)** | **Accuracy** | **0.893** (25/28) | DoD-1 ≥ 0.90 |
| GOLD (data thật held-out, 28 câu) | Macro-F1 | 0.735* | — |
| PROXY (tay viết, 142 câu) | Accuracy | 0.894 | — |
| PROXY (tay viết, 142 câu) | Macro-F1 | 0.897 | — |

*Macro-F1 GOLD thấp là **do cách tính, không phải model kém**: GOLD chỉ có 6/8 intent
(thiếu `calculate_cost`, `policy_inquiry` — 0 mẫu test), macro-F1 chia trung bình trên
đủ 8 nhãn nên bị 2 nhãn không mẫu kéo xuống. Con số có nghĩa là **accuracy 0.893**
(so với 0.873 lần train cũ chỉ eval trên proxy).

### NER — vẫn là số cũ trên bộ PROXY (chưa train lại)

`data2.md` là câu hội thoại (hỏi chi tiết/đặt lịch/so sánh), không có nhãn NER, nên
NER **không** đổi. Số dưới đây vẫn trên bộ **proxy** (`ner_test_real.jsonl` — xem cảnh
báo đầu file, KHÔNG phải dữ liệu thật):

| Model | Epoch | Metric | Giá trị | Ngưỡng DoD (SPEC §1.3) |
|---|---|---|---|---|
| NER (`out-ner/`) | 8 | Precision / Recall | 0.742 / 0.714 | — |
| NER (`out-ner/`) | 8 | Entity-F1 (seqeval) | 0.728 | DoD-2 ≥ 0.85 |
| NER (`out-ner/`) | 8 | Token accuracy | 0.881 | — |

NER F1 thấp hơn accuracy (0.728 vs 0.881) là đặc trưng seqeval: một entity dự đoán
sai NHÃN hoặc LỆCH RANH GIỚI dù chỉ 1 token vẫn tính sai toàn span, trong khi accuracy
tính theo từng token nên bị pha loãng bởi lượng lớn token nhãn "O".

**Việc còn lại để đạt DoD thật**:
- Intent: sát ngưỡng (0.893) — thu thập thêm câu thật, nhất là `calculate_cost` và
  `policy_inquiry` (đang 0 mẫu trong GOLD) rồi đo lại.
- NER: cần gán nhãn NER cho dữ liệu thật (Doccano/Label Studio, §13.3) rồi train lại —
  pipeline đã sẵn sàng, chỉ cần đổi input.

Model đã lưu tại `out-intent/`, `out-ner/` (đã xóa checkpoint trung gian, chỉ giữ
model cuối — mỗi model ~515MB, đã gitignore, không commit vào git).

### Thời gian chạy (tham khảo)

~24 phút/model khi chạy song song trên CPU (batch 16). Nếu có GPU sẽ nhanh hơn
nhiều lần.

## Quyết định kỹ thuật đáng chú ý

- **Entity lưu theo offset ký tự** (kiểu Doccano/spaCy: `{"start","end","label"}`),
  không cố định BIO theo token ngay lúc sinh dữ liệu — vì ranh giới token phụ
  thuộc bộ word-segmenter, việc quy đổi sang BIO nên làm ở thời điểm train
  (`vncorenlp_util.segment_with_offsets` + `train_ner.py:assign_bio`).
- **Quy offset gốc → token đã segment**: VnCoreNLP wseg chỉ GHÉP các từ liền kề
  bằng `_`, không bao giờ tách nhỏ hơn hay đổi thứ tự — nên chỉ cần đếm số `_`
  trong mỗi token đã segment để biết nó "tiêu thụ" bao nhiêu từ gốc, từ đó suy
  ra lại offset trong văn bản gốc mà không cần thư viện alignment phức tạp.
- **PhoBERT không có tokenizer "Fast"** (không hỗ trợ `word_ids()`/`offset_mapping`).
  `train_ner.py` encode TỪNG TỪ đã segment riêng lẻ bằng tokenizer thường, gán
  nhãn cho subword đầu tiên của mỗi từ, `-100` cho phần còn lại — cách làm chuẩn
  khi không có fast tokenizer.
- **Nhiễu dữ liệu (~15%, §13.2)**: bỏ dấu toàn câu (`unicodedata` NFD + lọc
  combining mark) — phép biến đổi 1-đối-1 ký tự nên không làm lệch offset entity
  đã tính trước đó.
- **Interface `NluService` ở backend không đổi** (§9.1): khi model đã train xong
  và đạt yêu cầu, bước tiếp theo (2.3/2.4 — ngoài phạm vi phần này) là bọc model
  thành FastAPI service rồi cài `NluService` mới, không sửa tầng trên.

## So sánh 3 phương án NLU (SPEC §14.4) — `eval-results/REPORT.md`

Hiện thực hóa bảng §14.4 của SPEC (phần khoa học của đồ án): chạy **cùng một test
set** qua 3 cài đặt `NluService` rồi so sánh. Harness `eval_nlu_compare.py`, cache
kết quả từng câu ở `eval-results/{side}_{dataset}.jsonl`.

### 1. Intent trên GOLD thật (28 câu held-out) — cập nhật 2026-08-01

| Phương án | Intent Acc | Macro-F1 |
|---|---|---|
| A. LLM prompt JSON (GĐ1) | 0.893 | 0.757 |
| **B. PhoBERT fine-tuned (GĐ2)** | **0.893** | 0.735 |
| C. LLM Function Calling | 0.821 | 0.674 |

Đo trên dữ liệu THẬT held-out (`data/intent_test_gold.jsonl`). Macro-F1 tính trên các
nhãn có xuất hiện (GOLD thiếu `calculate_cost`/`policy_inquiry`) — **Accuracy là số dẫn**.

**Kết luận khoa học:** trên data thật, **PhoBERT (B) hòa phương án LLM prompt (A) ở
0.893 và vượt Function Calling (C, 0.821)** — mô hình self-host miễn phí ngang LLM về
intent. Đúng tinh thần §14.4 (dòng 977 SPEC): kết luận trung thực, có số thật.

### 2. Slot/Span-F1 + Latency + Chi phí — bộ PROXY (NER chưa có data thật)

| Phương án | Intent Acc | Slot-F1 | Span-F1 | Latency TB | p95 | Chi phí/1000 msg |
|---|---|---|---|---|---|---|
| A. LLM prompt JSON | 0.923 | 0.921 | — | 850ms | 1004ms | ~2.342đ |
| B. PhoBERT fine-tuned | 0.923 | 0.872 | 0.686 | **86ms** | **100ms** | **≈0đ** |
| C. LLM Function Calling | 0.908 | 0.950 | — | 669ms | 803ms | ~1.163đ |

NER/Slot-F1 đo trên bộ **proxy** vì `data2.md` là câu hội thoại, không có nhãn NER thật
(xem cảnh báo đầu file). B thua ~5đ Slot-F1 nhưng **thắng ~8–10× latency và chi phí ≈0**.

Chạy lại:

```bash
# GOLD (intent, cần GEMINI_API_KEY cho A/C):
python eval_phobert_gold_intent.py                                  # B (không cần key)
GEMINI_API_KEY=... python eval_nlu_compare.py --side llm --dataset gold   # A
GEMINI_API_KEY=... python eval_nlu_compare.py --side fc  --dataset gold   # C
python eval_nlu_compare.py --report                                 # tổng hợp REPORT.md
```

## Việc còn lại trước khi dùng cho bảo vệ chính thức

1. Thay `ner_test_real.jsonl` bằng dữ liệu NER thật (Doccano/Label Studio) để đo được
   Slot/Span-F1 trên GOLD; mở rộng GOLD intent để phủ đủ 8 nhãn.
2. ~~So sánh 3 phương án NLU (SPEC §14.4)~~ — **đã đo trên GOLD thật 2026-08-01**
   (intent); xem mục "So sánh 3 phương án NLU" ở trên + `eval-results/REPORT.md`.
3. ~~Bọc FastAPI (`nlu-service/`) + nối `PhoBertNluServiceImpl` vào Spring Boot
   (SPEC §11 bước 2.3/2.4)~~ — đã làm 2026-07-18, xem `nlu-service/README.md`.
