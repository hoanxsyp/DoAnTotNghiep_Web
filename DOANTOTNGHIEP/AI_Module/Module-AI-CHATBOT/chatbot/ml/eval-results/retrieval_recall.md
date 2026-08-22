# Đánh giá tầng Retrieval — Recall@5 (DoD-3, SPEC §14.3)

- Số câu truy vấn: **30** (bộ vàng `src/test/resources/retrieval_eval.jsonl`)
- **Recall@5 trung bình (macro): 1.000** — ngưỡng DoD-3 ≥ 0.80 → **ĐẠT** ✅
- MRR trung bình: 1.000

| Câu | Mô tả | \|G\| | Recall@5 | RR | Top-K trả về | Bộ vàng |
|---|---|---|---|---|---|---|
| q01 | Phòng dưới 3 triệu ở Thanh Xuân | 4 | 1.00 | 1.00 | [42, 4, 1, 2] | [42, 4, 1, 2] |
| q02 | Phòng Thanh Xuân có máy giặt | 3 | 1.00 | 1.00 | [3, 5, 33] | [3, 5, 33] |
| q03 | Chung cư mini ở Thanh Xuân | 2 | 1.00 | 1.00 | [3, 5] | [3, 5] |
| q04 | Phòng Thanh Xuân dưới 3 triệu có điều hòa | 3 | 1.00 | 1.00 | [42, 1, 2] | [42, 1, 2] |
| q05 | Phòng Cầu Giấy dưới 3 triệu | 4 | 1.00 | 1.00 | [21, 37, 17, 6] | [21, 37, 17, 6] |
| q06 | Phòng Cầu Giấy có điều hòa và máy giặt | 4 | 1.00 | 1.00 | [21, 37, 17, 56] | [21, 37, 17, 56] |
| q07 | Nhà nguyên căn ở Cầu Giấy | 3 | 1.00 | 1.00 | [37, 35, 56] | [37, 35, 56] |
| q08 | Phòng Hà Đông dưới 3 triệu | 2 | 1.00 | 1.00 | [7, 18] | [7, 18] |
| q09 | Phòng Hà Đông có điều hòa | 2 | 1.00 | 1.00 | [31, 32] | [31, 32] |
| q10 | Phòng Đống Đa dưới 2.5 triệu | 4 | 1.00 | 1.00 | [19, 44, 22, 40] | [19, 44, 22, 40] |
| q11 | Chung cư mini Đống Đa | 5 | 1.00 | 1.00 | [44, 22, 40, 46, 45] | [44, 22, 40, 46, 45] |
| q12 | Phòng Hai Bà Trưng có máy giặt | 3 | 1.00 | 1.00 | [48, 47, 8] | [48, 47, 8] |
| q13 | Phòng Ba Đình có điều hòa dưới 3.3 triệu | 3 | 1.00 | 1.00 | [39, 27, 53] | [39, 27, 53] |
| q14 | Phòng Hoàn Kiếm dưới 2 triệu | 3 | 1.00 | 1.00 | [29, 54, 59] | [29, 54, 59] |
| q15 | Nhà nguyên căn Hoàn Kiếm | 3 | 1.00 | 1.00 | [54, 59, 16] | [54, 59, 16] |
| q16 | Phòng Bắc Từ Liêm dưới 2.5 triệu | 5 | 1.00 | 1.00 | [52, 34, 25, 24, 36] | [52, 34, 25, 24, 36] |
| q17 | Phòng Nam Từ Liêm | 3 | 1.00 | 1.00 | [13, 51, 58] | [13, 51, 58] |
| q18 | Phòng Long Biên | 2 | 1.00 | 1.00 | [30, 12] | [30, 12] |
| q19 | Phòng Hoàng Mai có điều hòa | 2 | 1.00 | 1.00 | [41, 49] | [41, 49] |
| q20 | Phòng dưới 1.8 triệu có máy giặt | 4 | 1.00 | 1.00 | [52, 21, 37, 54] | [52, 21, 37, 54] |
| q21 | Phòng trên 42m2 dưới 3 triệu | 4 | 1.00 | 1.00 | [24, 36, 22, 48] | [22, 24, 36, 48] |
| q22 | Chung cư mini có máy giặt dưới 3 triệu | 3 | 1.00 | 1.00 | [36, 17, 48] | [17, 36, 48] |
| q23 | Phòng dưới 1.8 triệu có điều hòa và chỗ để xe | 5 | 1.00 | 1.00 | [34, 21, 37, 29, 42] | [21, 29, 34, 37, 42] |
| q24 | Phòng gần PTIT dưới 3 triệu | 5 | 1.00 | 1.00 | [18, 1, 4, 2, 7] | [18, 1, 4, 2, 7] |
| q25 | Phòng gần PTIT có điều hòa | 3 | 1.00 | 1.00 | [1, 31, 2] | [1, 31, 2] |
| q26 | Phòng gần PTIT (bán kính 1km) | 2 | 1.00 | 1.00 | [18, 1] | [18, 1] |
| q27 | Phòng gần Bách Khoa dưới 4.5 triệu | 5 | 1.00 | 1.00 | [47, 50, 48, 23, 19] | [47, 50, 48, 23, 19] |
| q28 | Phòng gần Bến xe Mỹ Đình | 5 | 1.00 | 1.00 | [17, 37, 56, 14, 6] | [17, 37, 56, 14, 6] |
| q29 | Phòng Thanh Xuân trên 25m2 | 4 | 1.00 | 1.00 | [42, 2, 3, 5] | [42, 2, 3, 5] |
| q30 | Nhà nguyên căn dưới 2 triệu | 4 | 1.00 | 1.00 | [52, 37, 54, 59] | [52, 37, 54, 59] |

> **Cách đọc:** đo trực tiếp trên `RetrievalService` (bỏ qua NLU). `relevant_ids` gán độc lập từ seed `data.sql`. Mọi câu có |G| ≤ 5 để công thức `|Top5∩G|/|G|` không bị K=5 chặn cơ học. Với tầng lọc cứng, MRR=1.0 là kỳ vọng (kết quả trả về đều thỏa filter nên phần tử đầu luôn liên quan); tín hiệu chính là **Recall@5** — tụt khi filter bỏ sót phòng đúng hoặc geo tính sai khoảng cách.
