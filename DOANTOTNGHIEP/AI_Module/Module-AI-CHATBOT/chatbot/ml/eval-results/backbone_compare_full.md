# So sánh ViSoBERT và PhoBERT — cùng dữ liệu, cùng cách chấm

- Train intent: `intent_train_v2` + câu thật ×10, 5 epoch, lr 2e-05
- Train NER: `ner_train_v2` + `ner_data_md_labeled` ×1, 8 epoch, lr 3e-05
- batch 16, max_length 128, seeds [42, 43, 44]
- Lấy model ở epoch CUỐI, không chọn checkpoint trên bộ test.

## Kết quả (trung bình ± độ lệch chuẩn qua các seed)

| Model | Intent GOLD acc | Intent GOLD macro-F1 | Intent proxy acc | NER Span-F1 (proxy) | NER P | NER R | Latency CPU |
|---|---:|---:|---:|---:|---:|---:|---:|
| visobert | 0.905 ± 0.021 | 0.884 ± 0.040 | 0.721 ± 0.039 | 0.595 ± 0.011 | 0.519 ± 0.017 | 0.697 ± 0.004 | 82 ms (p95 101) |
| phobert | 0.929 ± 0.000 | 0.917 ± 0.002 | 0.770 ± 0.027 | 0.745 ± 0.008 | 0.704 ± 0.011 | 0.792 ± 0.004 | 61 ms (p95 76) |

## Từng seed

| Model | Seed | GOLD đúng | proxy acc | NER Span-F1 | Train intent | Train NER |
|---|---:|---:|---:|---:|---:|---:|
| visobert | 42 | 25/28 | 0.718 | 0.590 | 289s | 280s |
| visobert | 43 | 25/28 | 0.683 | 0.587 | 320s | 903s |
| visobert | 44 | 26/28 | 0.761 | 0.608 | 325s | 309s |
| phobert | 42 | 26/28 | 0.739 | 0.739 | 269s | 721s |
| phobert | 43 | 26/28 | 0.782 | 0.742 | 278s | 584s |
| phobert | 44 | 26/28 | 0.789 | 0.754 | 267s | 579s |

## Chuẩn bị dữ liệu

| Model | Câu bị cắt (>max_length) | Trần NER round-trip (test proxy) | Trần NER round-trip (56 câu thật) |
|---|---:|---:|---:|
| visobert | 1/2785 | 1.000 | 0.976 |
| phobert | 1/2785 | 0.977 | 0.950 |

Trần round-trip = encode nhãn gold rồi decode ngược. < 1.000 nghĩa là cách tách token không biểu diễn được một số ranh giới entity, model có giỏi mấy cũng không đạt.
