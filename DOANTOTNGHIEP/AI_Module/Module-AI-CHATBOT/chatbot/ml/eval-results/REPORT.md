# So sánh NLU — SPEC §14.4

## 1. Intent trên GOLD thật (28 câu held-out) — bộ đánh giá CHÍNH

| Phương án | Intent Acc | Intent Macro-F1 |
|---|---|---|
| A. LLM prompt JSON (GĐ1) | 0.893 | 0.757 |
| B. PhoBERT fine-tuned (GĐ2) | 0.893 | 0.735 |
| C. LLM Function Calling | 0.821 | 0.674 |

- Test set: `data/intent_test_gold.jsonl` — 28 câu DỮ LIỆU THẬT held-out (data.md + data2.md), tách bởi `data/split_real_data.py`.
- GOLD chỉ có 6/8 intent (thiếu `calculate_cost`, `policy_inquiry` — 0 mẫu). Macro-F1 tính trên các nhãn có xuất hiện (cùng quy ước sklearn/evaluate lúc train). **Accuracy là số dẫn**; macro-F1 chỉ tham chiếu vì GOLD còn nhỏ.

## 2. Slot / Span-F1 + Latency + Chi phí — bộ PROXY (NER chưa có data thật)

| Phương án | Intent Acc | Slot-F1 | Span-F1 (strict) | Latency TB | p95 | Chi phí/1000 msg |
|---|---|---|---|---|---|---|
| A. LLM prompt JSON (GĐ1) | 0.923 | 0.921 | — | 850ms | 1004ms | ~2.342đ (~$0.090; 615 in + 71 out tok/msg) |
| B. PhoBERT fine-tuned (GĐ2) | 0.923 | 0.872 | 0.686 | 86ms | 100ms | ≈0đ (self-host CPU) |
| C. LLM Function Calling | 0.908 | 0.950 | — | 669ms | 803ms | ~1.163đ (~$0.045; 331 in + 29 out tok/msg) |

- Test set: `intent_test_real.jsonl` (142 câu) + `ner_test_real.jsonl` (87 câu) — bộ PROXY tay viết.
- Slot-F1: mọi phương án quy về cùng dạng (slot, giá_trị) chuẩn hóa; DATETIME/RADIUS chấm có/không.
- Span-F1 strict (label+start+end) chỉ đo được với PhoBERT (LLM không trả span).
- Latency LLM phụ thuộc mạng + tier; PhoBERT đo trên CPU local, không GPU.
- Chi phí LLM tính theo giá flash-lite $0.1/1M in, $0.4/1M out (KIỂM TRA lại bảng giá trước khi trích dẫn); free tier = 0đ trong quota.
