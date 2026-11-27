# Memo học cách train NER của Webtro

Memo này đi theo **một dòng thật trong file đã dùng để train**, từ JSONL đến lúc `Trainer` cập nhật trọng số. Có thể chạy debug, đọc các phần `[1]` đến `[6]` trên terminal, rồi đối chiếu với từng phần dưới đây.

## Chạy debug trước

Mở PowerShell tại thư mục `chatbot/ml`. Dùng Python trong `.venv` đã cài `requirements.txt`, Java có `JAVA_HOME`, và tài nguyên tách từ VnCoreNLP như lúc train.

```powershell
.\.venv\Scripts\python.exe debug_ner.py
```

Mặc định, script theo dõi **dòng 3**, đưa các dòng **1 đến 16** của tập tổng hợp mới vào `Trainer` và chạy **một bước cập nhật**. Lệnh sau cho dễ đọc hơn nếu chỉ muốn xem một câu.

```powershell
.\.venv\Scripts\python.exe debug_ner.py --row 3 --batch-size 1
```

`--row` tính từ 1. Có thể dùng `--synth-file ner_train_real.jsonl` để chọn một câu trong dữ liệu thật. `--base-model` mặc định là `vinai/phobert-base-v2`, có thể nhận đường dẫn model cục bộ. Script dùng thư mục tạm và in mọi kết quả ra terminal. Lần chạy kiểm chứng trong memo dùng GPU, seed `42` và bản PhoBERT đã có trong cache. Cách xáo trộn batch, dự đoán đầu và loss có thể thay đổi theo thiết bị và phiên bản thư viện.

[Script debug](debug_ner.py) · [Script train đầy đủ](train_ner.py) · [Hướng dẫn môi trường và dữ liệu](README.md)

## [1] Dữ liệu gốc là gì?

**JSONL** là định dạng mỗi dòng chứa một đối tượng JSON. Mỗi dòng NER của Webtro gồm `text` và danh sách `entities`. Dòng 3 của tập tổng hợp mới là:

```json
{"text":"À có 2n1k dưới 3tr ở Phú Đô k, thêm chỗ để xe điện càng tốt","entities":[{"start":5,"end":9,"label":"ROOM_TYPE"},{"start":15,"end":18,"label":"PRICE_MAX"},{"start":21,"end":27,"label":"LOCATION"},{"start":36,"end":50,"label":"UTILITY"}]}
```

**Thực thể (entity)** ở đây là một đoạn chữ có ý nghĩa với chatbot. **Span** là đoạn liên tục trên câu gốc. **Offset** là vị trí ký tự giúp lấy chính xác đoạn đó. `start` tính từ 0 và `end` là vị trí ngay sau đoạn. Trong Python, `text[15:18]` trả về `3tr`. **Label** là loại của đoạn, ở đây là `PRICE_MAX`. **Annotation** là đáp án được gán cho câu. Nếu câu không có đoạn nào thuộc 10 loại của dự án, danh sách là `[]`.

| Span | Offset | Ý nghĩa |
| --- | --- | --- |
| `2n1k` | `5:9` | `ROOM_TYPE`, loại phòng |
| `3tr` | `15:18` | `PRICE_MAX`, giá trần |
| `Phú Đô` | `21:27` | `LOCATION`, khu vực |
| `chỗ để xe điện` | `36:50` | `UTILITY`, tiện ích |

[Dòng 3 của tập tổng hợp đã dùng để train](data/ner_train_chat_synthetic_2750.jsonl#L3)

## [2] Từ câu gốc sang dữ liệu cho PhoBERT

`build_dataset()` gọi `encoder.ner(...)` rồi `bio_labels_from_offsets(...)`. Chạy debug cho dòng 3 thực tế in dòng tách từ sau:

```text
À có 2n1k dưới 3tr ở Phú_Đô k , thêm chỗ để xe_điện càng tốt
```

**Tách từ (word segmentation)** gom các tiếng thuộc cùng một từ hoặc cụm mà VnCoreNLP nhận diện. Ví dụ, `Phú Đô` thành `Phú_Đô`. **Tokenizer** của PhoBERT tiếp tục tách những từ khó thành các mảnh nhỏ, gọi là **subword**. Bộ tách BPE biểu diễn `3tr` thành `3@@` và `tr`. `@@` cho biết từ còn tiếp ở mảnh sau. **Token ID** là số trong từ vựng của model, chẳng hạn `3@@` có ID `2147`. **Embedding** là vector số mà model dùng để biểu diễn token trong lúc xử lý.

**BIO** chuyển đáp án theo đoạn thành đáp án trên các vị trí token. `B` bắt đầu một thực thể, `I` tiếp tục thực thể đó và `O` là ngoài thực thể. `-100` đánh dấu vị trí bỏ qua khi tính loss. Webtro chỉ gắn offset cho subword đầu của mỗi từ. Các subword tiếp theo và token đặc biệt nhận `-100`.

| Token thực tế | ID | Offset của từ gốc | Nhãn train | Giải thích |
| --- | ---: | --- | --- | --- |
| `<s>` | 0 |  | `-100` | Token mở đầu câu |
| `2@@` | 2326 | `5:9` | `B-ROOM_TYPE` | Đại diện cho cả từ `2n1k` |
| `n@@`, `1@@`, `k` | 1301, 2576, 1947 |  | `-100` | Các mảnh còn lại của `2n1k` |
| `dưới` | 280 | `10:14` | `O` | Từ ngoài đoạn giá theo quy ước nhãn |
| `3@@` | 2147 | `15:18` | `B-PRICE_MAX` | Đại diện cho cả từ `3tr` |
| `tr` | 10909 |  | `-100` | Mảnh sau của `3tr` |
| `Phú_Đô` | 41651 | `21:27` | `B-LOCATION` | Một từ sau khi VnCoreNLP tách |
| `chỗ` | 683 | `36:39` | `B-UTILITY` | Bắt đầu cụm tiện ích |
| `để` | 24 | `40:42` | `I-UTILITY` | Nối tiếp cụm tiện ích |
| `xe_điện` | 8108 | `43:50` | `I-UTILITY` | Nối tiếp cụm tiện ích |
| `</s>` | 2 |  | `-100` | Token kết thúc câu |

Câu này có **21 token**, gồm cả `<s>` và `</s>`. Trong cả bộ nhãn của dự án cũng có **21 nhãn đầu ra** vì `10` loại thực thể × `2` tiền tố `B/I` + `O`. Số token của câu khác có thể thay đổi, còn đầu phân loại trong đợt train này luôn có 21 nhãn.

[Code tạo dataset](train_ner.py#L77) · [Code tách token và giữ offset](phobert_encoding.py#L90) · [Code đổi offset sang BIO](nlu_encoding.py#L116)

## [3]–[4] Trainer nhận batch như thế nào?

**Batch** là nhóm câu được xử lý cùng lúc. Câu có độ dài khác nhau nên **collator** thêm **padding** vào câu ngắn để tất cả có cùng số vị trí trong batch. Giới hạn `max_length=128` là mức tối đa của từng câu. Collator của Webtro pad động đến câu dài nhất trong batch.

Trong lần chạy debug thực tế, `Trainer` xáo trộn 16 dòng và đưa dòng 3 vào **vị trí 7 của batch**. Đây là một lần train debug được chạy riêng trên 16 dòng, với số cập nhật giới hạn bằng 1. Một lần train đầy đủ xáo trộn trên tập dữ liệu lớn hơn.

```text
Device: cuda:0
input_ids:      (16, 24)
attention_mask: (16, 24)
labels:         (16, 24)
Selected sample position in batch: 7
Padding tokens in batch: 100
Labels used in loss: 222
Logits shape:   (16, 24, 21)
```

| Dữ liệu | Cách đọc |
| --- | --- |
| `input_ids` | Ma trận ID token, gồm 16 câu và 24 vị trí mỗi câu sau khi pad. |
| `attention_mask` | `1` cho vị trí của câu, `0` cho padding. Model dùng mask để bỏ qua padding khi tính attention. |
| `labels` | Đáp án dạng số. Ví dụ `B-PRICE_MAX` là `1`, `B-LOCATION` là `5`, `O` là `0`. Padding cũng nhận `-100`. |
| `logits` | Điểm thô model tạo cho từng nhãn. `(16, 24, 21)` nghĩa là 16 câu × 24 vị trí × 21 nhãn. |

**Tensor** là mảng số có một hoặc nhiều chiều. **Shape** là kích thước từng chiều. Trong batch này, 16 × 24 có 384 vị trí. Sau 100 vị trí padding và 62 vị trí token đặc biệt hoặc subword tiếp theo, có **222 vị trí** được dùng để tính loss. Một vị trí có thể có `attention_mask=1` và `labels=-100`, nghĩa là model đọc token để hiểu ngữ cảnh nhưng vị trí đó không bị chấm nhãn.

## [5]–[6] Model sai thì được sửa như thế nào?

**Pretrain** dạy PhoBERT biểu diễn tiếng Việt từ trước. **Fine-tune** dùng dữ liệu có nhãn của Webtro để điều chỉnh các trọng số đó cho NER. Khi nạp `vinai/phobert-base-v2`, model gắn một **đầu phân loại** mới để cho điểm 21 nhãn. Vì đầu này vừa khởi tạo, terminal có thể báo `classifier.weight` và `classifier.bias` là `MISSING` trong checkpoint nền. Transformers khởi tạo các trọng số đó cho nhiệm vụ hiện tại.

Trong lần debug, ở lượt **forward** đầu tiên, model dự đoán `B-DATETIME` cho `3@@`, trong khi đáp án là `B-PRICE_MAX`. Với `Phú_Đô`, model dự đoán `B-ROOM_TYPE`, đáp án là `B-LOCATION`. **Forward** là bước đưa tensor qua model để tính điểm. **Argmax** lấy nhãn có điểm cao nhất để hiển thị dự đoán. **Cross-entropy loss** dùng điểm của toàn bộ nhãn để phạt khi model gán xác suất thấp cho đáp án đúng, tính trên các vị trí có nhãn hợp lệ.

```text
Batch loss: 3.166159
Classifier gradient norm before clipping: 3.417062
Largest absolute classifier weight change: 0.00003000
Optimizer steps: 1
```

**Backward** tính gradient, tức mức độ mỗi trọng số ảnh hưởng đến loss. **Gradient norm** tóm tắt độ lớn các gradient của một nhóm trọng số. **Gradient clipping** giới hạn độ lớn gradient trước khi cập nhật. **Optimizer** dùng gradient để điều chỉnh trọng số, còn **learning rate** điều khiển quy mô bước điều chỉnh. Debug đo thấy ít nhất một trọng số của đầu phân loại đổi khoảng `0.00003` sau đúng một bước. Loss `3.166159` là loss của cả batch debug, và `0.00003000` là độ lớn thay đổi trọng số lớn nhất trong đầu phân loại.

```text
batch → model.forward() → logits + loss → backward() → optimizer update
```

Trong project, Hugging Face `Trainer` thực hiện vòng lặp đó khi code gọi `trainer.train()`. Script debug gắn **hook** để quan sát dữ liệu đi vào, đầu ra và gradient của chính lần chạy. Dự đoán in trong mục `[5]` là dự đoán **trước lần cập nhật vừa quan sát**. [Lệnh chạy Trainer trong code train](train_ner.py#L204) · [Hook và số liệu debug](debug_ner.py)

## So với lần train 8 epoch đã chạy

| Khái niệm | Trong lần train Webtro |
| --- | --- |
| Base model | `vinai/phobert-base-v2`, điểm xuất phát của lần fine-tune. |
| Train set | 2.750 câu tổng hợp mới + 115 câu thật có nhãn, tổng cộng 2.865 câu. |
| Batch size | Tối đa 16 câu mỗi batch. Với 2.865 câu, mỗi epoch có 180 batch và batch cuối có 1 câu. |
| Epoch | Một lượt qua tập train. Đợt này có 8 epoch, tương đương 1.440 bước cập nhật với một GPU và một batch mỗi bước. |
| Learning rate | Bắt đầu ở `3e-5`. Scheduler điều chỉnh learning rate theo tiến độ train. |
| Seed | Giá trị điều khiển một phần ngẫu nhiên như thứ tự lấy batch. Tái lập chính xác còn phụ thuộc thiết bị, phiên bản thư viện và cách đặt seed trước khi khởi tạo model. |
| Checkpoint | Bản trạng thái lưu giữa đợt train để tiếp tục hoặc so sánh. Lần chạy này lưu **model và tokenizer ở cuối epoch 8**. |

**Train set** dùng cập nhật trọng số. **Dev set** thường dùng để chọn cấu hình và phân tích lỗi. **Test set** dùng để chấm phiên bản đã chốt. Script của đợt này có theo dõi GOLD qua từng epoch để xem kết quả, rồi lấy trọng số epoch cuối. Nếu dùng kết quả GOLD để chọn dữ liệu hoặc tham số cho lần sau, nên có một tập test mới để báo cáo chất lượng cuối.

[Log train 8 epoch](cmp-runs/ner-chat-synthetic/train.log) · [Cấu hình huấn luyện](train_ner.py#L125)

## Train xong thì NER chạy và được chấm ra sao?

Khi **suy luận (inference)**, model đọc câu và sinh logits. Pipeline chọn nhãn rồi ghép `B-UTILITY` và `I-UTILITY` thành đoạn gốc **`chỗ để xe điện`**. Java chuẩn hóa `3tr` thành `3000000` để tìm phòng. **NLU** là phần hiểu câu hỏi. Trong Webtro, **intent** cho biết người dùng muốn làm gì, còn **NER** lấy thông tin xuất hiện ở đâu trong câu. Việc ghép từng mức giá với đúng yêu cầu và thực thi lần lượt thuộc phần điều phối hội thoại.

| Chỉ số | Ý nghĩa |
| --- | --- |
| TP, FP, FN | TP là thực thể đúng. FP là dự đoán sai hoặc dư. FN là thực thể trong đáp án bị bỏ sót. |
| Precision | Trong số thực thể model trả ra, bao nhiêu đúng. Công thức `TP / (TP + FP)`. |
| Recall | Trong số thực thể có trong đáp án, bao nhiêu được tìm thấy. Công thức `TP / (TP + FN)`. |
| Strict span-F1 | `2 × TP / (2 × TP + FP + FN)` với điều kiện khớp cả nhãn, `start` và `end`. |
| Tỷ lệ câu đúng toàn bộ | Một câu đúng khi mọi thực thể đều đúng, không thiếu và không dư. |

Ví dụ, bản train lại bắt đúng **65** thực thể, dự đoán sai hoặc dư **8**, và bỏ sót **4** trên tập GOLD 28 mẫu. Tính `2 × 65 / (2 × 65 + 8 + 4)` được **91,55% strict span-F1**. Bộ 210 câu thử thách tổng hợp đạt **90,91% F1** với cùng model. Các số này thuộc hai tập chấm riêng và đo tầng NER trước khi Java chuẩn hóa.

[Phép đo trên GOLD và PROXY](cmp-runs/ner-chat-synthetic/evaluation.json) · [Phép đo trên bộ tổng hợp 210 câu](cmp-runs/ner-synthetic-challenge-v1/evaluation.json)

## Ghi chú để bổ sung vào báo cáo

Khi viết báo cáo, có thể dùng dòng 3 để minh họa ba chỗ trong code. `build_dataset()` biến câu và đáp án thành tensor, `Trainer.train()` tính loss và cập nhật trọng số, `decode_bio_offsets()` khôi phục thực thể về đoạn ký tự gốc. Để bổ sung một ví dụ khác, chạy `--row` với dòng cần phân tích và ghi lại câu gốc, nhãn, dự đoán và số liệu của lượt debug đó.
