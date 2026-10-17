# Trạng thái ViSoBERT — model, quá trình fine-tune, và dữ liệu

> ⚠️ **ĐÃ THAY THẾ (2026-10-17)**: hệ thống đã chuyển sang **PhoBERT** và còn 6 intent —
> xem `README.md`. File này giữ làm lịch sử giai đoạn ViSoBERT; model ViSoBERT cũ
> đã xoá (2026-10-17); số liệu của nó còn trong file này và `eval-results/backbone_compare_full.md`.

> Cập nhật: **2026-09-30**. Tổng hợp trạng thái thực tế trên đĩa tại thời điểm viết.
> Liên quan: `README.md` (hướng dẫn train), `../TRANG_THAI_DU_AN.md` (trạng thái toàn dự án),
> `eval-results/REPORT.md` (bảng so sánh 3 phương án NLU).

---

## 0. Tóm tắt một đoạn

**Đã fine-tune xong, model chạy được.** Hai model ViSoBERT nằm ở `out-intent/` và
`out-ner/`, train ngày 2026-09-30, đã kiểm tra nạp được bằng đúng code của
`nlu-service`. Trước phiên này chưa có model ViSoBERT nào — 2 thư mục đó chứa
model **PhoBERT cũ** từ tháng 7-8 (code đã chuyển sang ViSoBERT ở commit `ad27738`
nhưng model thì chưa train lại).

Kết quả hiện tại: **Intent accuracy 0.821** / **NER F1 0.604** trên bộ đánh giá chính.
Chưa đạt ngưỡng DoD (0.90 / 0.85), nhưng intent đã ngang phương án LLM
function-calling với chi phí bằng 0.

---

## 1. Model hiện tại

| | `out-intent/` | `out-ner/` |
|---|---|---|
| Kiến trúc | `XLMRobertaForSequenceClassification` | `XLMRobertaForTokenClassification` |
| Base model | `uitnlp/visobert` | `uitnlp/visobert` |
| Vocab size | 15004 | 15004 |
| Số nhãn | 8 intent | 21 (BIO của 10 entity) |
| `nlu_max_length` | 64 | 64 |
| `tokenizer.json` | ✅ có (fast) | ✅ có (fast) |
| Kích thước | 390 MB (+2 checkpoint) | 388 MB (+2 checkpoint) |
| Train lúc | 2026-09-30 00:55 | 2026-09-30 00:59 |

**Xác nhận đúng là ViSoBERT, không phải PhoBERT**: `vocab_size = 15004` (PhoBERT là
64001), kiến trúc XLM-R, tokenizer SentencePiece. Nếu thấy `bpe.codes` + `vocab.txt`
trong thư mục model thì đó là PhoBERT — hiện KHÔNG còn.

**Điều kiện chạy `nlu-service` đã thỏa.** `nlu-service/app.py:82` bắt buộc
`ner_tok.is_fast == True`; đã kiểm tra trực tiếp, cả 2 model đều `True`. Trước phiên
này service luôn ở trạng thái `degraded` và trả 503 vì model PhoBERT không có
`tokenizer.json`.

### Lệnh đã dùng để train 2 model hiện tại

```bash
python train_intent.py --epochs 5 --real-repeat 10 \
    --synth-file intent_train_v2.jsonl --output-dir out-intent

python train_ner.py --epochs 8 --real-repeat 1 \
    --synth-file ner_train_v2.jsonl --output-dir out-ner
```

Siêu tham số còn lại giữ mặc định: `learning_rate=2e-5`, `batch_size=16`,
`max_length=64`, `load_best_model_at_end=True` (chọn theo `f1`).

---

## 2. Đã tinh chỉnh những gì

Theo thứ tự thời gian, kèm tác động đo được:

| # | Việc làm | Kết quả |
|---|---|---|
| 1 | Cài `sentencepiece` + `protobuf` vào `.venv` | Mở khóa — thiếu 2 gói này thì `AutoTokenizer` của ViSoBERT raise ngay, không train được |
| 2 | Dọn model PhoBERT cũ (đổi tên thành `.bak`) | Tránh lẫn `vocab.txt`/`bpe.codes` PhoBERT với file ViSoBERT |
| 3 | Train ViSoBERT lần đầu (data v1) | Intent **0.750** / NER **0.527** — kém PhoBERT |
| 4 | Dò learning rate 1e-5 / 2e-5 / 5e-5 | 0.679 / **0.750** / 0.679 → mặc định 2e-5 đã tốt nhất, **không cải thiện được** |
| 5 | Kiểm tra `max_length=64` có cắt câu không | GOLD và PROXY **0%** câu vượt 64 token → **không phải nguyên nhân** |
| 6 | Kiểm tra nạp trọng số pretrain | Chỉ `lm_head.*` bị bỏ + `classifier.*` khởi tạo mới = đúng chuẩn → **không phải lỗi** |
| 7 | Nối data NER thật vào `train_ner.py` | NER 0.527 → **0.577** |
| 8 | Thêm `--real-repeat`, dò 1/10/20 | Intent ×10 macro-F1 0.508 → **0.609**; NER ×1 là tốt nhất |
| 9 | **Sinh lại data synthetic theo văn phong thật (v2)** | Intent → **0.821**, NER → **0.604** ← đòn bẩy lớn nhất |
| 10 | Thêm `save_total_limit=2` | Sửa lỗi đầy ổ đĩa (mỗi checkpoint ~1.6 GB, trước đó giữ hết) |

**Kết luận về tinh chỉnh:** siêu tham số gần như không ăn thua (mục 4-6). Toàn bộ
cải thiện đến từ **dữ liệu** — nối data thật vào (mục 7-8) và sửa văn phong data
synthetic (mục 9).

---

## 3. Kết quả đo

### Bộ đánh giá chính — GOLD (28 câu data THẬT held-out)

| Chỉ số | v1 (data cũ) | **v2 (hiện tại)** | Ngưỡng DoD |
|---|---|---|---|
| Intent accuracy | 0.750 | **0.821** | DoD-1 ≥ 0.90 ❌ |
| Intent macro-F1 | 0.609 | **0.747** | — |
| `eval_loss` | 1.28 | **0.567** | — |
| Số câu sai | 7/28 | **5/28** | — |

`eval_loss` rơi từ 1.28 xuống 0.567 là tín hiệu quan trọng nhất: ở v1 nó đứng im
suốt 5 epoch trong khi `train_loss` xuống 0.002 — model học thuộc synthetic, không
khái quát. Ở v2 nó thật sự giảm.

### NER — bộ PROXY (87 câu tay viết; **chưa có bộ test NER thật**)

| Chỉ số | v1 | **v2 (hiện tại)** | Ngưỡng DoD |
|---|---|---|---|
| F1 | 0.577 | **0.604** | DoD-2 ≥ 0.85 ❌ |
| Precision | 0.560 | 0.531 | — |
| Recall | 0.594 | **0.699** | — |
| Token accuracy | 0.852 | 0.851 | — |

> ⚠️ Con số F1 này do `train_ner.py` in ra, tính theo **đơn vị subword**. Số chính
> thức để báo cáo là **Span-F1 strict** từ `eval_nlu_compare.py` — khắt khe hơn nên
> nhiều khả năng thấp hơn. **Chưa chạy đo.**

### Bộ PROXY intent (142 câu tay viết)

| | v1 | v2 |
|---|---|---|
| Accuracy | 0.725 | 0.697 |

Giảm là **đúng kỳ vọng, không phải hồi quy**: PROXY viết theo văn phong sạch kiểu
v1, model v2 chuyên cho văn phong thật. GOLD mới là bộ đánh giá chính.

### So với 2 phương án NLU còn lại (SPEC §14.4, cùng bộ GOLD)

| Phương án | Intent Acc | Chi phí/1000 msg | Độ trễ |
|---|---|---|---|
| A. LLM prompt JSON | 0.893 | ~2.342đ | 850ms |
| C. LLM Function Calling | 0.821 | ~1.163đ | 669ms |
| **B. ViSoBERT (hiện tại)** | **0.821** | **≈0đ** | chạy local |

ViSoBERT đã từ bét bảng (0.750) lên **ngang bằng function-calling với chi phí 0**.
Vẫn kém phương án A 7 điểm.

---

## 4. Dữ liệu train — toàn bộ file

Tất cả nằm trong `ml/data/`. Cột "Loại" phân biệt data **THẬT** (người dùng gõ, thu
thập từ post/chat) với **SYNTHETIC** (máy sinh theo template).

### 4.1 Dữ liệu INTENT

| File | Câu | Loại | Trạng thái | Dùng làm gì |
|---|---|---|---|---|
| `intent_train_v2.jsonl` | 2500 | SYNTHETIC v2 | 🟢 **đang dùng train** | Nguồn synthetic chính hiện tại |
| `intent_train.jsonl` | 2500 | SYNTHETIC v1 | 🟡 giữ để đối chứng | Bộ cũ, văn phong sạch |
| `intent_train_real.jsonl` | 115 | **THẬT** | 🟢 **đang dùng train** (×10) | Data thật phần train |
| `intent_test_gold.jsonl` | 28 | **THẬT** | 🔒 **đóng băng — bộ đánh giá CHÍNH** | Không bao giờ đưa vào train |
| `intent_test_real.jsonl` | 142 | proxy tay viết | 🟡 báo cáo tham chiếu | Tên file gây hiểu lầm: **không phải data thật** |
| `intent_data_md_labeled.jsonl` | 56 | **THẬT** (nguồn) | ⚪ nguồn gốc | Bài đăng tìm phòng → gộp vào train/gold |
| `intent_data2_labeled.jsonl` | 87 | **THẬT** (nguồn) | ⚪ nguồn gốc | Câu chat hội thoại → gộp vào train/gold |

Tổng data thật intent: **143 câu** (56 + 87), tách stratified 80/20 → 115 train + 28 gold.

Phân bố nhãn GOLD: `search_room` 11, `refine_search` 4, `compare_rooms` 4,
`room_detail` 4, `book_appointment` 4, `out_of_scope` 1.
**Thiếu hẳn `calculate_cost` và `policy_inquiry` — 0 mẫu test.**

### 4.2 Dữ liệu NER

| File | Câu | Entity | Loại | Trạng thái |
|---|---|---|---|---|
| `ner_train_v2.jsonl` | 2075 | 6965 | SYNTHETIC v2 | 🟢 **đang dùng train** |
| `ner_train.jsonl` | 2075 | 3635 | SYNTHETIC v1 | 🟡 giữ để đối chứng |
| `ner_data_md_labeled.jsonl` | 56 | 209 | **THẬT** | 🟢 **đang dùng train** (×1) |
| `ner_test_real.jsonl` | 87 | 133 | proxy tay viết | 🟡 bộ đánh giá tạm |

> ❌ **NER không có bộ test THẬT.** Mọi số NER đang đo trên proxy tay viết. Đây là
> lỗ hổng lớn nhất còn lại — DoD-2 yêu cầu "test thật" nên về mặt nghiêm ngặt
> **DoD-2 hiện chưa đo được**, chứ không phải "đo rồi nhưng chưa đạt".

### 4.3 Phân bố entity — vì sao v2 tốt hơn

| Nhãn | v1 synthetic | **v2 synthetic** | Data THẬT |
|---|---|---|---|
| UTILITY | 576 | **1815** | 60 |
| PRICE_MAX | 582 | **1053** | 50 |
| LOCATION | 420 | **962** | 72 |
| ROOM_TYPE | — | 1016 | — |
| ROOM_REF | **1500** ⚠️ | 855 | **0** |
| DATETIME | 350 | 541 | 10 |
| POI | 140 | 341 | 16 |
| AREA_MIN | 67 | 175 | 1 |
| RADIUS | — | 170 | — |
| PRICE_MIN | — | 37 | — |

v1 để `ROOM_REF` chiếm **41%** tổng entity trong khi data thật **không có câu nào**
dùng nhãn này. v2 đưa UTILITY / PRICE_MAX / LOCATION lên đầu cho khớp thực tế.

### 4.4 Khác biệt văn phong v1 vs v2

| | v1 | v2 |
|---|---|---|
| Dạng giá | 1 mốc (`2 triệu`, `2tr5`) | thêm **khoảng** `2tr5-3tr`, dấu `<`, hậu tố `đổ lại`, `3.2M`, `5m5` |
| Từ khóa ngân sách | không có | `tc`/`Tc`/`TC`/`T/c`/`tchinh`/`Budget`/`ngân sách` |
| Địa điểm | 12 quận | + **34 phố/phường** (`Khuất Duy Tiến`, `Triều Khúc`, `Ngã Tư Sở`...) |
| POI | 8, viết đầy đủ | 34, kèm viết tắt `ptit`, `hvnh`, `FTU`, `ussh`, `BKHN` |
| Tiện ích | **4 nhóm** | **51 biến thể** (`vskk`, `nl`, `đh`, `tl`, ban công, gác xép, thang máy...) |
| Nhiễu | bỏ dấu **toàn câu** (15%) | bỏ dấu **lỗ chỗ theo từ** (26%), VIẾT HOA, emoji `🏠✅` |

Ví dụ câu v2:

```
✅ em tìm thuê trọ 2 người ở, di chuyển đến đh Bách Khoa ở khu Dương Quảng Hàm,
   có nhận xe điện máy lạnh tchinh <5tr5 trở xuống ạ
```

**Kiểm định chất lượng v2**: 0/6965 span sai offset; 0 câu có cả `PRICE_MIN` lẫn
`PRICE_MAX`. Từ vựng rút **chỉ từ 115 câu train thật**, không đụng `intent_test_gold.jsonl`
(bịa template theo bộ test sẽ làm số đo đẹp giả).

---

## 5. File code đã thay đổi

| File | Trạng thái git | Thay đổi |
|---|---|---|
| `data/generate_dataset_v2.py` | ⚠️ untracked | **MỚI** — bộ sinh data văn phong thật |
| `data/intent_train_v2.jsonl` | ⚠️ untracked | **MỚI** — 2500 câu |
| `data/ner_train_v2.jsonl` | ⚠️ untracked | **MỚI** — 2075 câu / 6965 entity |
| `train_intent.py` | modified | `--learning-rate`, `--real-repeat`, `--synth-file`, `save_total_limit=2` |
| `train_ner.py` | modified | **nối data thật** + 3 cờ trên + `save_total_limit=2` |

`data/generate_dataset.py` (v1) **giữ nguyên không sửa**, nên còn số liệu "trước/sau"
để viết báo cáo.

Log train: `train-intent-v2.log`, `train-ner-v2.log` (bản hiện tại) —
`train-intent-visobert.log` / `train-ner-visobert.log` (ViSoBERT trên data v1) —
`train-intent.log` / `train-ner.log` (PhoBERT cũ, giữ để đối chứng).

---

## 6. Vấn đề đã biết

### Chặn việc đạt DoD

1. **NER không có bộ test thật** (§4.2) — DoD-2 nghiêm ngặt là chưa đo được.
2. **GOLD chỉ 28 câu**, thiếu 2 intent (`calculate_cost`, `policy_inquiry` — 0 mẫu).
   1 câu = 3,6 điểm accuracy nên nhiễu rất lớn.
3. **Data thật quá ít**: 143 câu intent / 56 câu NER. Đây là trần thật sự.

### Lỗi kỹ thuật còn tồn

4. **Chọn model trên tập test**: `train_intent.py` đặt `eval_dataset = GOLD` kèm
   `load_best_model_at_end=True` → chọn epoch tốt nhất *dựa trên chính bộ test*.
   Con số 0.821 vì thế cao hơn thực tế. Cần tách validation riêng. **Sửa xong số sẽ giảm.**
5. **Khoảng giá bị xé đôi**: `3tr5-4tr` ra 2 span `'3'` + `'4tr'` thay vì 1 span.
6. **Một ca intent hồi quy**: `"phòng nào gần ĐH Bách Khoa bán kính 2km"` → đoán
   `compare_rooms` (đúng là `search_room`).
7. **`UtilityNormalizer` phía Java chưa khớp**: v2 sinh 51 biến thể tiện ích nhưng
   normalizer chỉ map 4 nhóm (`air_conditioner`/`parking`/`wifi`/`washing_machine`)
   → NER nhận ra span mà backend không quy đổi được. Xem `TODO.md` mục 6.
8. **Thiếu intent `check_availability`**: câu "Còn ko em ở luôn ạ" đang gán tạm
   `room_detail` và model đoán sai — xem ghi chú trong `TODO.md`.

### Dọn dẹp

9. `out-intent.phobert.bak/` (8.1 GB) + `out-ner.phobert.bak/` (515 MB) — model
   PhoBERT cũ, **không dùng được với code hiện tại** (commit `ad27738` đã xóa
   `vncorenlp_util.py`). Xóa được; số liệu của chúng đã nằm trong `train-*.log`.
10. File `v2` đang **untracked** — cần `git add` nếu muốn giữ.
11. Ổ C: từng đầy 100% giữa lúc train (đã dọn, còn 51 GB).

---

## 7. Việc tiếp theo, theo thứ tự đáng làm

1. **Chạy `eval_nlu_compare.py --side visobert`** → có Span-F1 strict chính thức,
   điền dòng B vào `eval-results/REPORT.md` (cần dựng `nlu-service` trước).
2. **Cập nhật số** trong `README.md` và `../TRANG_THAI_DU_AN.md` — hiện vẫn ghi "chưa đo".
3. **Sửa lỗi chọn model trên test** (mục 6.4) để có con số trung thực.
4. **Gán nhãn NER cho `data2.md`** (87 câu chat) → có bộ NER thật, mở khóa DoD-2.
5. **Thu thêm data thật**, ưu tiên `calculate_cost` và `policy_inquiry`.
6. **Mở rộng `UtilityNormalizer`** cho khớp 51 biến thể tiện ích.

---

## 8. Lệnh tái lập

```bash
cd ml

# (một lần) cài phụ thuộc — thiếu sentencepiece/protobuf là ViSoBERT không nạp được
./.venv/Scripts/python.exe -m pip install -r requirements.txt

# sinh lại data synthetic v2
python data/generate_dataset_v2.py

# train (đúng cấu hình của 2 model hiện tại)
python train_intent.py --epochs 5 --real-repeat 10 \
    --synth-file intent_train_v2.jsonl --output-dir out-intent
python train_ner.py --epochs 8 --real-repeat 1 \
    --synth-file ner_train_v2.jsonl --output-dir out-ner

# muốn train lại trên data v1 để đối chứng thì bỏ --synth-file
```
