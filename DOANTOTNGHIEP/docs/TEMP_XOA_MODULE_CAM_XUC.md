# Màn hình và API bị ảnh hưởng khi xoá module phân tích cảm xúc

> Nhánh `refactor/delete_sentiment` — migration `V21__drop_sentiment_module.sql`.

## Màn hình

| Màn hình | Đường dẫn | Ai dùng | Thay đổi |
|---|---|---|---|
| Chi tiết tin, phần bình luận | `/tin/:slugId` | Mọi người | Bỏ chip "Tích cực / Trung lập / Tiêu cực" cạnh mỗi bình luận |
| Thống kê tin | `/quan-ly/tin-dang/:id/thong-ke` | Chủ trọ | Bỏ biểu đồ "Cảm xúc bình luận" và cảnh báo "tỷ lệ bình luận tiêu cực cao". Biểu đồ diễn biến theo thời gian giờ chiếm hết chiều ngang |
| Tổng quan chủ trọ | `/quan-ly/tong-quan` | Chủ trọ | Mục cảnh báo "tin cần kiểm tra do tín hiệu AI/bình luận" đổi thành "tin đang chờ kiểm duyệt lại" |
| Thông báo | `/tai-khoan/thong-bao` và chuông 🔔 | Mọi người | Không còn thông báo "Cảnh báo: tin có nhiều bình luận tiêu cực". Các thông báo loại này đã có trong DB bị xoá |
| Admin: Kiểm duyệt bình luận | `/admin/binh-luan` | Admin, Kiểm duyệt viên | Bỏ cột "Cảm xúc" và bộ lọc "Cảm xúc". Câu xác nhận khi đánh dấu spam đổi thành "Đánh dấu bình luận này là spam?" |
| Admin: Log & cảnh báo AI | `/admin/ai/log` | Admin, Kiểm duyệt viên | Bỏ tab "Cảnh báo cảm xúc" và nút "Phân tích lại". Bộ lọc module không còn "Phân tích cảm xúc". Còn 2 tab: Log gọi AI, Tin lệch giá |
| Admin: Cấu hình AI | `/admin/ai/cau-hinh` | Admin | Bỏ nhóm cấu hình "Phân tích cảm xúc", công tắc bật/tắt module cảm xúc, và 2 trọng số điểm uy tín theo bình luận tích cực/tiêu cực |
| Admin: Dashboard | `/admin/dashboard` | Admin | Thẻ "Cảnh báo AI" giờ chỉ đếm tin lệch giá, bỏ phần "sentiment" |

## API

### Bị xoá hẳn (gọi vào sẽ trả 404)

| API | Trước đây dùng để |
|---|---|
| `POST /api/ai/sentiment/analyze` | Phân tích cảm xúc một đoạn văn bản hoặc bình luận |
| `POST /api/admin/ai/sentiment/reanalyze` | Admin phân tích lại một bình luận |
| `GET /api/admin/ai/alerts` | Danh sách tin bị cảnh báo vì nhiều bình luận tiêu cực |

### Vẫn giữ nhưng bỏ trường hoặc tham số

| API | Thay đổi |
|---|---|
| `GET /api/listings/{id}/comments` | Bỏ `sentimentLabel`, `sentimentScore` ở từng bình luận và khối `sentimentSummary` |
| `POST /api/listings/{id}/comments`, trả lời bình luận | Bình luận mới không còn bị đưa đi phân tích cảm xúc chạy nền |
| `PUT /api/comments/{id}` (sửa bình luận) | Không còn phân tích lại sau khi sửa. Bỏ trường `reanalysisTriggered` |
| `GET /api/admin/comments` | Bỏ tham số lọc `sentiment`. Bỏ các trường `sentimentLabel/LabelText/Score/Confidence` và `isRiskComment` |
| Ẩn / hiện / đánh dấu spam bình luận (admin) | Response bỏ các trường sentiment |
| `GET /api/listings/{id}/stats` | Bỏ khối `sentimentSummary` |
| `GET /api/admin/ai/config` | Bỏ nhóm `sentiment`. Nhóm `trustWeights` không còn 2 trọng số theo bình luận |
| `PUT /api/admin/ai/config` | Không nhận các khoá `ai.sentiment.*` nữa, gửi lên sẽ báo lỗi khoá không hợp lệ |
| `GET /api/admin/ai/logs` | `module=SENTIMENT` giờ trả 400. Log bỏ các trường sentiment |
| `GET /api/admin/dashboard` | Khối `aiAlerts` bỏ `listingsFlaggedBySentiment`, `pendingSentimentAnalysis`, `sentimentModuleEnabled` |
| `GET /api/landlord/dashboard` | Mục cảnh báo đổi khoá từ `AI_NEGATIVE_ALERT` thành `NEED_REVIEW` |
| `GET /api/notifications`, cài đặt thông báo | Không còn loại `AI_NEGATIVE_ALERT` |

### Đổi hành vi, không đổi cấu trúc dữ liệu

Các API trả điểm uy tín của tin (tìm kiếm, chi tiết tin, gợi ý, tin của chủ trọ…). Điểm giờ chỉ
còn tính từ đánh giá sao, nên với cấu hình hiện tại (`trust.base_score = 100`, `trust.max = 100`)
**mọi tin đều 100 điểm**.

> **Cập nhật 2026-10-14:** vì lý do trên, điểm uy tín của tin và chủ trọ đã bị **bỏ hẳn** (Flyway
> `V22__drop_trust_score.sql`, canonical mục 18). Các dòng nhắc điểm uy tín / `trustWeights` trong
> file này chỉ còn đúng ở thời điểm xoá module cảm xúc.

### Không bị ảnh hưởng

Chat, lưu tin, tìm kiếm, gợi ý tin, chatbot, dự đoán giá, báo cáo vi phạm, thanh toán.
