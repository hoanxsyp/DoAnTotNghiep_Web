# So sánh ViSoBERT và PhoBERT — cùng dữ liệu, cùng cách chấm

- Train intent: `intent_train_v2` + câu thật ×10, 5 epoch, lr 2e-05
- Train NER: `ner_train_v2` + `ner_data_md_labeled` ×1, 8 epoch, lr 3e-05
- batch 16, max_length 128, seeds [42, 43, 44]
- Lấy model ở epoch CUỐI, không chọn checkpoint trên bộ test.

## Kết quả (trung bình ± độ lệch chuẩn qua các seed)

| Model | Intent GOLD acc | Intent GOLD macro-F1 | Intent proxy acc | NER Span-F1 (proxy) | NER P | NER R | Latency CPU |
|---|---:|---:|---:|---:|---:|---:|---:|
| visobert_word | — | — | — | 0.631 ± 0.016 | 0.558 ± 0.017 | 0.727 ± 0.017 | — |

## Từng seed

| Model | Seed | GOLD đúng | proxy acc | NER Span-F1 | Train intent | Train NER |
|---|---:|---:|---:|---:|---:|---:|
| visobert_word | 42 | — | — | 0.647 | — | 261s |
| visobert_word | 43 | — | — | 0.614 | — | 282s |
| visobert_word | 44 | — | — | 0.632 | — | 285s |

## Chuẩn bị dữ liệu

| Model | Câu bị cắt (>max_length) | Trần NER round-trip (test proxy) | Trần NER round-trip (56 câu thật) |
|---|---:|---:|---:|
| visobert_word | 1/2785 | 0.977 | 0.932 |

Trần round-trip = encode nhãn gold rồi decode ngược. < 1.000 nghĩa là cách tách token không biểu diễn được một số ranh giới entity, model có giỏi mấy cũng không đạt.
