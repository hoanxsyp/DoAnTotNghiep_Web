# GĐ2 — Dataset + Huấn luyện ViSoBERT (SPEC §11, §13)

Chuẩn bị dataset và huấn luyện 2 mô hình cho tầng NLU: **intent classifier**
(8 nhãn) và **NER** (10 loại entity, BIO).

Base model: **`uitnlp/visobert`** (ViSoBERT, EMNLP 2023 — UIT NLP) — XLM-R +
SentencePiece, pretrain trên ~1GB comment Facebook/YouTube/TikTok. Hai lý do chọn
nó cho bài toán này:

1. **Văn phong đầu vào là chat**: teencode, mất dấu, viết tắt ("vskk", "2tr5",
   "tc 4tr5-5tr") — đúng phân phối dữ liệu pretrain của ViSoBERT.
2. **Nhận văn bản THÔ** → pipeline thuần Python, không cần bước tách từ riêng;
   và có **fast tokenizer** nên gán nhãn NER theo `offset_mapping` được (xem
   "Quyết định kỹ thuật" bên dưới).

Thông số: ~97M tham số (vocab 15.004), `max_position_embeddings=514`, 12 layer,
hidden 768 → model đã fine-tune ~390MB fp32.

## ⚠️ Đọc trước — về bộ test "real"

`data/intent_test_real.jsonl` và `data/ner_test_real.jsonl` là bộ **PROXY tạm thời**
do người phát triển tự viết tay (đa dạng hơn dữ liệu synthetic: câu cụt, thiếu dấu,
viết tắt, teencode) — **KHÔNG PHẢI** dữ liệu thật thu thập từ Facebook/chotot như
SPEC §13.3 yêu cầu. Số liệu train/test dưới đây **chỉ để xác nhận pipeline chạy
đúng**, KHÔNG dùng để kết luận "mô hình đạt X% accuracy" khi bảo vệ chính thức —
cần thay bằng dữ liệu thật gán nhãn (Doccano/Label Studio) trước khi dùng số liệu
đánh giá này làm minh chứng. Xem cảnh báo chi tiết trong
`data/build_proxy_testset.py`.

Bộ GOLD (`data/intent_test_gold.jsonl`) là data thật nhưng chỉ **28 câu** → 1 câu
= 3,6 điểm accuracy. Chênh lệch 1–2 câu giữa các lần train là **nhiễu**, không
phải cải thiện.

## Cấu trúc

```
ml/
├── requirements.txt          # torch(cpu), transformers, datasets, evaluate, seqeval, sentencepiece, protobuf
├── nlu_encoding.py           # encode/decode dùng chung train + nlu-service
├── data/
│   ├── generate_dataset.py      # sinh intent_train.jsonl + ner_train.jsonl (synthetic, offset chính xác)
│   ├── build_proxy_testset.py   # sinh intent_test_real.jsonl + ner_test_real.jsonl (TAY VIẾT, xem cảnh báo trên)
│   ├── split_real_data.py       # tách data thật → intent_train_real.jsonl + intent_test_gold.jsonl
│   ├── intent_train.jsonl       # 2500 câu, 8 intent
│   ├── intent_test_real.jsonl   # 142 câu (proxy)
│   ├── ner_train.jsonl          # 2075 câu có entity (offset ký tự)
│   └── ner_test_real.jsonl      # 87 câu có entity (proxy)
├── train_intent.py           # sequence classification (SPEC §11 bước 2.1)
├── train_ner.py              # token classification / BIO (SPEC §11 bước 2.2)
├── eval_nlu_compare.py       # bảng so sánh 3 phương án NLU (§14.4)
└── eval_local_intent_gold.py # cache intent trên GOLD cho eval_nlu_compare
```

## Cài đặt

Không cần Java/JDK — ViSoBERT nhận văn bản thô, pipeline thuần Python.

```bash
cd ml
python -m venv .venv
source .venv/Scripts/activate        # Windows Git Bash; PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

`sentencepiece` + `protobuf` là **bắt buộc**: repo `uitnlp/visobert` chỉ ship
`sentencepiece.bpe.model`, KHÔNG có `tokenizer.json`, nên
`AutoTokenizer.from_pretrained` phải convert slow → fast lúc tải lần đầu và bước
convert cần cả 2 gói. Thiếu 1 gói là raise ngay, và thông báo lỗi lại nói về
`tiktoken` nên khá dễ hiểu sai nguyên nhân.

## Chạy

```bash
# Sinh lại dataset (đã có sẵn trong data/, chỉ cần chạy lại nếu muốn thay đổi vocab/tỉ lệ)
python data/generate_dataset.py
python data/build_proxy_testset.py
python data/split_real_data.py                # tách train/test từ data thật

# Smoke test (vài chục câu, 1 epoch) — xác nhận code chạy đúng trước khi train full
python train_intent.py --max-train 40 --epochs 1 --output-dir out-intent-smoke
python train_ner.py --max-train 40 --epochs 1 --output-dir out-ner-smoke

# Full training
python train_intent.py --epochs 5 --output-dir out-intent
python train_ner.py --epochs 8 --output-dir out-ner
```

`train_*.py` ghi khoá `nlu_max_length` vào `config.json` của model, nên
`nlu-service` tokenize y hệt lúc train mà không cần cấu hình tay (ghi đè bằng
`NLU_MAX_LENGTH` nếu cần). Đây là chỗ mà sai lệch giữa train và inference sẽ **cắt
câu và tụt độ chính xác mà không báo lỗi** — kiểm bằng `GET /health` của service.

Model + tokenizer được lưu vào `out-intent/`, `out-ner/` (đã gitignore — không
commit weight vào git).

## Kết quả

> **CHƯA ĐO.** Pipeline đã sẵn sàng nhưng 2 model chưa được train nên `out-intent/`
> và `out-ner/` còn trống. Chạy phần "Full training" ở trên rồi điền bảng dưới đây.

| Bộ đánh giá | Metric | Giá trị | Ngưỡng DoD |
|---|---|---|---|
| GOLD (data thật held-out, 28 câu) | Accuracy | *chưa đo* | DoD-1 ≥ 0.90 |
| GOLD (data thật held-out, 28 câu) | Macro-F1 | *chưa đo* | — |
| PROXY (tay viết, 142 câu) | Accuracy | *chưa đo* | — |
| NER (`ner_test_real.jsonl`, proxy) | Span-F1 strict | *chưa đo* | DoD-2 ≥ 0.85 |

Hai lưu ý khi đọc số sau khi train:

- **Macro-F1 trên GOLD sẽ thấp giả tạo**: GOLD chỉ có 6/8 intent (thiếu
  `calculate_cost`, `policy_inquiry` — 0 mẫu test), macro-F1 chia trung bình trên
  đủ 8 nhãn nên bị 2 nhãn không mẫu kéo xuống. **Accuracy là số dẫn.**
- **F1 mà `train_ner.py` in ra tính theo đơn vị SUBWORD**, không phải theo entity
  trên văn bản gốc — nó là tín hiệu để chọn checkpoint, KHÔNG phải số để báo cáo.
  Số để báo cáo là **Span-F1 strict** (khớp đúng label+start+end trên offset ký
  tự) do `eval_nlu_compare.py` đo.

Nói thêm về seqeval: F1 luôn thấp hơn token-accuracy khá nhiều, vì một entity dự
đoán sai NHÃN hoặc LỆCH RANH GIỚI dù chỉ 1 token vẫn tính sai toàn span, trong khi
accuracy tính theo từng token nên bị pha loãng bởi lượng lớn token nhãn "O".

## Quyết định kỹ thuật đáng chú ý

- **Entity lưu theo offset ký tự** (kiểu Doccano/spaCy: `{"start","end","label"}`),
  không cố định BIO theo token ngay lúc sinh dữ liệu — vì ranh giới token phụ
  thuộc tokenizer, việc quy đổi sang BIO nên làm ở thời điểm train
  (`nlu_encoding.bio_labels_from_offsets`). Nhờ vậy dataset độc lập với model.
- **Gán nhãn BIO trực tiếp lên từng subword** theo `offset_mapping` của fast
  tokenizer, thay vì gán theo "từ". Đây là điểm ăn nhau so với cách làm word-level:
  **7,5%** entity trong `data/ner_*.jsonl` có ranh giới nằm giữa một "từ" theo
  khoảng trắng (vd. "khoảng 6tr," — PRICE_MAX chỉ là "6tr", không gồm dấu phẩy),
  gán nhãn theo từ sẽ không biểu diễn được những ca này.
- **Cắt dấu câu ở biên span entity** (`nlu_encoding.tidy_entity_span`):
  SentencePiece đôi khi sinh subword VẮT QUA ranh giới entity — "tối đa 2tr8,
  diện tích..." tách thành `▁2`/`tr`/`8,` nên token cuối chứa cả "8" (thuộc
  PRICE_MAX) lẫn dấu phẩy (không thuộc), làm span dài thêm 1 ký tự và trượt
  Span-F1 strict. Cắt cả `-`, `/`, `~` vì data thật hay viết giá dạng khoảng/xấp
  xỉ ("1tr5-2tr", "2tr5/tháng", "~2tr5"). An toàn với bộ nhãn hiện tại: **0/3977**
  entity có biên là ký tự không alphanumeric (riêng "²" của "22m²" được Python coi
  là alnum nên không bị cắt). Không cắt ngoặc/ngoặc kép vì một POI hợp lệ có thể
  kết thúc bằng ")".
- **`B-` giữa từ được coi là tiếp tục span**, không mở span mới. Vì decode ở mức
  SUBWORD, một nhãn `B-` model nhả sai giữa từ sẽ xé "Hoàng Mai" thành 2 span rác
  `"Ho"` + `"àng"` → `EntityNormalizer` tra địa danh sẽ miss. An toàn: **0/3977**
  entity nằm liền nhau trong cùng một từ nên không gộp oan cặp nào (các ca
  "1tr5-2tr" trong data thật chỉ gán nhãn 1 entity).
- **Nhiễu dữ liệu (~15%, §13.2)**: bỏ dấu toàn câu (`unicodedata` NFD + lọc
  combining mark) — phép biến đổi 1-đối-1 ký tự nên không làm lệch offset entity
  đã tính trước đó.
- **Interface `NluService` ở backend không đổi** (§9.1): model được bọc thành
  FastAPI service (`nlu-service/`) rồi cài `NluService` mới, không sửa tầng trên.
  Hợp đồng `POST /nlu` trả **span thô** — chuẩn hóa giá trị là việc của
  `EntityNormalizer` phía Java, không nhân đôi luật.

### Cách encode đã được kiểm chứng không mất mát

Encode toàn bộ `data/ner_train.jsonl` (2075 câu / 3635 entity) và
`data/ner_test_real.jsonl` (87 câu / 133 entity) bằng `nlu_encoding`, gán BIO từ
nhãn gold rồi decode lại → khớp **100%** span gốc (tp=3635 fp=0 fn=0 và tp=133
fp=0 fn=0), không câu nào bị truncate ở `max_length=64`.

Nghĩa là **trần Span-F1 của cách encode này = 1.0000**: mọi sai số đo được về sau
hoàn toàn do model, không do pipeline. Đây là phép kiểm nên chạy lại mỗi khi sửa
`nlu_encoding.py`.

Chi tiết khác đã xác nhận:

- `AutoTokenizer.from_pretrained("uitnlp/visobert").is_fast == True` (khi có
  `sentencepiece` + `protobuf`).
- `offset_mapping` KHÔNG gộp khoảng trắng đứng trước vào offset của token `▁xxx`;
  token đặc biệt có offset `(0,0)` + `special_tokens_mask=1`.
- `tokenizer.save_pretrained()` xuất `tokenizer.json`, nên `nlu-service` nạp lại
  KHÔNG cần `sentencepiece`/`protobuf` — chỉ bên train cần.

### `max_length=64` sẽ cắt dữ liệu THẬT

Số token ViSoBERT trên từng file:

| File | n | TB | p95 | max | vượt 64 |
|---|---|---|---|---|---|
| `ner_train.jsonl` (synthetic) | 2075 | 20,5 | 29 | 36 | **0%** |
| `ner_test_real.jsonl` (proxy) | 87 | 18,3 | 25 | 29 | **0%** |
| `intent_train.jsonl` (synthetic) | 2500 | 19,3 | 28 | 36 | **0%** |
| `intent_test_gold.jsonl` (thật) | 28 | 24,8 | 38 | 56 | 0% |
| `intent_train_real.jsonl` (thật) | 115 | 30,6 | 86 | 307 | **9,6%** |
| `ner_data_md_labeled.jsonl` (thật) | 56 | 45,1 | 105 | 307 | **19,6%** |

Data synthetic/proxy không bị ảnh hưởng (0% vượt) nên mặc định 64 vẫn dùng được
cho pipeline hiện tại. Nhưng **post thật dài hơn nhiều**: ~10–20% câu bị cắt mất
phần cuối, và phần cuối post Facebook thường chính là chỗ ghi giá/tiện ích.
ViSoBERT cho 514 vị trí nên nâng được: `--max-length 128` (hoặc 256), gần như miễn
phí vì collator padding động theo câu dài nhất mỗi batch. **Khi train trên dữ liệu
thật thì nâng cho cả 2 model.**

Ngoài ra `data/ner_data_md_labeled.jsonl` (56 câu thật, 209 entity NER đã gán
nhãn) hiện **KHÔNG** được `train_ner.py` dùng — `ner_train.jsonl` vẫn thuần
synthetic. Đây là nguồn data thật sẵn có nếu muốn cải thiện NER.

## So sánh 3 phương án NLU (SPEC §14.4) — `eval-results/REPORT.md`

Hiện thực hóa bảng §14.4 của SPEC (phần khoa học của đồ án): chạy **cùng một test
set** qua 3 cài đặt `NluService` rồi so sánh. Harness `eval_nlu_compare.py`, cache
kết quả từng câu ở `eval-results/{side}_{dataset}.jsonl`.

### 1. Intent trên GOLD thật (28 câu held-out)

| Phương án | Intent Acc | Macro-F1 |
|---|---|---|
| A. LLM prompt JSON (GĐ1) | 0.893 | 0.757 |
| **B. ViSoBERT fine-tuned (GĐ2)** | *chưa đo* | *chưa đo* |
| C. LLM Function Calling | 0.821 | 0.674 |

### 2. Slot/Span-F1 + Latency + Chi phí — bộ PROXY (NER chưa có data thật)

| Phương án | Intent Acc | Slot-F1 | Span-F1 | Latency TB | p95 | Chi phí/1000 msg |
|---|---|---|---|---|---|---|
| A. LLM prompt JSON | 0.923 | 0.921 | — | 850ms | 1004ms | ~2.342đ |
| **B. ViSoBERT fine-tuned** | *chưa đo* | *chưa đo* | *chưa đo* | *chưa đo* | *chưa đo* | **≈0đ** |
| C. LLM Function Calling | 0.908 | 0.950 | — | 669ms | 803ms | ~1.163đ |

Số của A và C đã đo thật (Gemini flash-lite; kiểm lại bảng giá trước khi trích
dẫn). Dòng B cần train model rồi chạy:

```bash
python train_intent.py --epochs 5 --output-dir out-intent
python train_ner.py    --epochs 8 --output-dir out-ner
uvicorn app:app --port 8000                  # trong nlu-service/, kiểm GET /health
python eval_nlu_compare.py --side visobert   # PROXY (intent + ner)
python eval_local_intent_gold.py             # GOLD (intent)
python eval_nlu_compare.py --report          # tổng hợp REPORT.md
```

**Cảnh báo khi đưa vào báo cáo:** GOLD 28 câu → 1 câu = 3,6 điểm accuracy, nên đừng
kết luận mạnh từ chênh lệch nhỏ. Bộ PROXY thì train set phần lớn là synthetic do
`generate_dataset.py` sinh theo template, nên điểm cao trên đó đo khả năng khớp
template nhiều hơn là năng lực hiểu. Nút thắt thật sự là **dữ liệu nhãn** (NER còn
chưa có bộ test thật nào).

## Việc còn lại trước khi dùng cho bảo vệ chính thức

1. **Train 2 model** rồi điền các bảng "chưa đo" ở trên.
2. Thay `ner_test_real.jsonl` bằng dữ liệu NER thật (Doccano/Label Studio) để đo được
   Slot/Span-F1 trên GOLD; mở rộng GOLD intent để phủ đủ 8 nhãn. Khi đó nâng
   `--max-length` lên 128/256 vì post thật dài hơn 64 token.
3. ~~Bọc FastAPI (`nlu-service/`) + nối `LocalNluServiceImpl` vào Spring Boot
   (SPEC §11 bước 2.3/2.4)~~ — đã làm, xem `nlu-service/README.md`.
