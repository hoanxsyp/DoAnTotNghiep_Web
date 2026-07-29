-- =====================================================================================
-- V5__seed_system_configs.sql — Seed toàn bộ 105 config key vào bảng system_configs
-- Spring Boot 3.3.5 / Java 21 / MySQL 8.4 / Flyway
-- Charset utf8mb4, engine InnoDB, timezone UTC.
-- Nguồn sự thật: docs/00_CANONICAL_DECISIONS.md mục 9 (105 config key).
--
-- Bảng đích: system_configs (định nghĩa ở V1__baseline_schema.sql, bảng thứ 45).
-- Cột NOT NULL bắt buộc: config_key, config_value, default_value, value_type, group_name, label.
-- value_type ∈ (STRING, INT, DECIMAL, BOOLEAN, JSON) — ép ở SystemConfigService.
-- config_value = default_value tại thời điểm seed. group_name suy từ tiền tố key.
-- page.about / page.terms: value_type=JSON, chứa chuỗi JSON bao HTML tĩnh mẫu (Admin sửa qua UI).
-- Idempotent không bắt buộc (Flyway chỉ chạy 1 lần). Không phụ thuộc FK nào.
-- =====================================================================================

INSERT INTO system_configs
  (config_key, config_value, default_value, value_type, group_name, label, description, display_order)
VALUES
  -- ===== Nhóm listing (12) =====
  ('listing.display_days', '30', '30', 'INT', 'listing', 'Số ngày hiển thị tin', 'Số ngày tin đăng hiển thị trước khi hết hạn', 1),
  ('listing.image.min', '1', '1', 'INT', 'listing', 'Số ảnh tối thiểu', 'Số ảnh tối thiểu bắt buộc cho mỗi tin', 2),
  ('listing.image.max', '10', '10', 'INT', 'listing', 'Số ảnh tối đa', 'Số ảnh tối đa cho phép mỗi tin', 3),
  ('listing.image.max_size_mb', '5', '5', 'INT', 'listing', 'Dung lượng ảnh tối đa (MB)', 'Dung lượng tối đa mỗi ảnh tính bằng megabyte', 4),
  ('listing.title.min', '10', '10', 'INT', 'listing', 'Độ dài tiêu đề tối thiểu', 'Số ký tự tối thiểu của tiêu đề tin', 5),
  ('listing.title.max', '150', '150', 'INT', 'listing', 'Độ dài tiêu đề tối đa', 'Số ký tự tối đa của tiêu đề tin', 6),
  ('listing.description.min', '30', '30', 'INT', 'listing', 'Độ dài mô tả tối thiểu', 'Số ký tự tối thiểu của mô tả tin', 7),
  ('listing.description.max', '3000', '3000', 'INT', 'listing', 'Độ dài mô tả tối đa', 'Số ký tự tối đa của mô tả tin', 8),
  ('listing.expiry.reminder_days', '3,1', '3,1', 'STRING', 'listing', 'Ngày nhắc sắp hết hạn', 'Danh sách số ngày trước hạn để gửi nhắc nhở', 9),
  ('listing.renew.free_per_month', '2', '2', 'INT', 'listing', 'Số lần gia hạn miễn phí/tháng', 'Số lần gia hạn tin miễn phí mỗi tháng cho chủ trọ', 10),
  ('listing.need_review.publicly_visible', 'true', 'true', 'BOOLEAN', 'listing', 'Tin NEED_REVIEW hiển thị công khai', 'Cho phép tin ở trạng thái NEED_REVIEW vẫn hiển thị công khai', 11),
  ('listing.auto_approve.trusted_landlord', 'false', 'false', 'BOOLEAN', 'listing', 'Tự duyệt tin chủ trọ uy tín', 'Tự động duyệt tin của chủ trọ có uy tín cao', 12),
  -- ===== Nhóm moderation (12) =====
  ('moderation.autohide.report_count', '5', '5', 'INT', 'moderation', 'Ngưỡng report tự ẩn', 'Số report đạt ngưỡng để tự động ẩn tin', 13),
  ('moderation.autohide.distinct_reporters', '5', '5', 'INT', 'moderation', 'Số người report khác nhau', 'Số người report khác nhau để tự động ẩn tin', 14),
  ('moderation.autohide.window_hours', '24', '24', 'INT', 'moderation', 'Cửa sổ tính report (giờ)', 'Khoảng thời gian tính số report để tự động ẩn', 15),
  ('moderation.threshold.warning_count', '3', '3', 'INT', 'moderation', 'Ngưỡng số cảnh báo', 'Số cảnh báo vi phạm đạt ngưỡng để xử lý', 16),
  ('moderation.threshold.warning_window_days', '30', '30', 'INT', 'moderation', 'Cửa sổ đếm cảnh báo (ngày)', 'Khoảng thời gian đếm số cảnh báo vi phạm', 17),
  ('moderation.threshold.locked_listing_count', '5', '5', 'INT', 'moderation', 'Ngưỡng tin bị khóa', 'Số tin bị khóa đạt ngưỡng để xử lý chủ trọ', 18),
  ('moderation.threshold.locked_listing_window_days', '60', '60', 'INT', 'moderation', 'Cửa sổ đếm tin bị khóa (ngày)', 'Khoảng thời gian đếm số tin bị khóa', 19),
  ('moderation.threshold.spam_comment_count', '10', '10', 'INT', 'moderation', 'Ngưỡng bình luận spam', 'Số bình luận spam đạt ngưỡng để hạn chế', 20),
  ('moderation.threshold.spam_comment_window_hours', '1', '1', 'INT', 'moderation', 'Cửa sổ đếm bình luận spam (giờ)', 'Khoảng thời gian đếm số bình luận spam', 21),
  ('moderation.autohide.sentiment_requires_prior_warning', 'true', 'true', 'BOOLEAN', 'moderation', 'Tự ẩn theo sentiment cần cảnh báo trước', 'Chỉ tự ẩn theo sentiment tiêu cực khi tin đã từng bị cảnh báo', 22),
  -- (moderation nhóm còn 1 key dời xuống cùng miền — giữ đủ 105) --
  -- ===== Nhóm trust (14) =====
  ('trust.base_score', '100', '100', 'INT', 'trust', 'Điểm uy tín khởi tạo', 'Điểm uy tín ban đầu của chủ trọ', 23),
  ('trust.weight.positive_comment', '1', '1', 'INT', 'trust', 'Trọng số bình luận tích cực', 'Điểm cộng cho mỗi bình luận tích cực', 24),
  ('trust.weight.negative_comment', '2', '2', 'INT', 'trust', 'Trọng số bình luận tiêu cực', 'Điểm trừ cho mỗi bình luận tiêu cực', 25),
  ('trust.weight.average_rating', '5', '5', 'INT', 'trust', 'Trọng số điểm đánh giá', 'Hệ số quy đổi điểm đánh giá trung bình vào uy tín', 26),
  ('trust.weight.valid_report', '10', '10', 'INT', 'trust', 'Trọng số report hợp lệ', 'Điểm trừ cho mỗi report được xác nhận hợp lệ', 27),
  ('trust.weight.violation_warning', '15', '15', 'INT', 'trust', 'Trọng số cảnh báo vi phạm', 'Điểm trừ cho mỗi cảnh báo vi phạm', 28),
  ('trust.min', '0', '0', 'INT', 'trust', 'Điểm uy tín tối thiểu', 'Giá trị sàn của điểm uy tín', 29),
  ('trust.max', '100', '100', 'INT', 'trust', 'Điểm uy tín tối đa', 'Giá trị trần của điểm uy tín', 30),
  ('trust.threshold.risky', '40', '40', 'INT', 'trust', 'Ngưỡng uy tín rủi ro', 'Dưới ngưỡng này chủ trọ bị gắn nhãn rủi ro', 31),
  ('trust.threshold.need_review', '25', '25', 'INT', 'trust', 'Ngưỡng uy tín cần xem xét', 'Dưới ngưỡng này chủ trọ cần được xem xét', 32),
  ('trust.weight.landlord_response_rate', '10', '10', 'INT', 'trust', 'Trọng số tỷ lệ phản hồi', 'Hệ số cộng uy tín theo tỷ lệ phản hồi người thuê', 33),
  ('trust.response_rate.window_days', '30', '30', 'INT', 'trust', 'Cửa sổ tính tỷ lệ phản hồi (ngày)', 'Khoảng thời gian tính tỷ lệ phản hồi của chủ trọ', 34),
  ('trust.response_rate.sla_hours', '24', '24', 'INT', 'trust', 'Ngưỡng phản hồi nhanh (giờ)', 'Số giờ tối đa được coi là phản hồi nhanh', 35),
  ('trust.response_rate.min_conversations', '3', '3', 'INT', 'trust', 'Số hội thoại tối thiểu tính phản hồi', 'Dưới ngưỡng này không đủ mẫu, số hạng phản hồi bằng 0', 36),
  ('trust.response_rate.neutral_percent', '70', '70', 'INT', 'trust', 'Mốc trung tính tỷ lệ phản hồi (%)', 'Mốc phần trăm phản hồi được xem là trung tính', 37),
  -- ===== Nhóm ai (30) =====
  ('ai.sentiment.enabled', 'true', 'true', 'BOOLEAN', 'ai', 'Bật phân tích cảm xúc', 'Bật/tắt module phân tích cảm xúc bình luận', 38),
  ('ai.sentiment.min_comments_l1', '5', '5', 'INT', 'ai', 'Số bình luận tối thiểu mức 1', 'Số bình luận tối thiểu để kích hoạt cảnh báo mức 1', 39),
  ('ai.sentiment.negative_ratio_l1', '0.40', '0.40', 'DECIMAL', 'ai', 'Tỷ lệ tiêu cực mức 1', 'Tỷ lệ bình luận tiêu cực kích hoạt cảnh báo mức 1', 40),
  ('ai.sentiment.min_comments_l2', '10', '10', 'INT', 'ai', 'Số bình luận tối thiểu mức 2', 'Số bình luận tối thiểu để kích hoạt cảnh báo mức 2', 41),
  ('ai.sentiment.negative_ratio_l2', '0.50', '0.50', 'DECIMAL', 'ai', 'Tỷ lệ tiêu cực mức 2', 'Tỷ lệ bình luận tiêu cực kích hoạt cảnh báo mức 2', 42),
  ('ai.sentiment.need_review_count_for_lock', '3', '3', 'INT', 'ai', 'Số lần NEED_REVIEW để khóa', 'Số lần vào NEED_REVIEW đề xuất khóa tin', 43),
  ('ai.sentiment.need_review_window_days', '30', '30', 'INT', 'ai', 'Cửa sổ đếm NEED_REVIEW (ngày)', 'Khoảng thời gian đếm số lần vào NEED_REVIEW', 44),
  ('ai.sentiment.landlord_alert_listing_count', '3', '3', 'INT', 'ai', 'Số tin cảnh báo chủ trọ', 'Số tin tiêu cực để gửi cảnh báo cho chủ trọ', 45),
  ('ai.sentiment.min_length', '10', '10', 'INT', 'ai', 'Độ dài bình luận tối thiểu', 'Bình luận ngắn hơn ngưỡng này coi là NEUTRAL', 46),
  ('ai.sentiment.new_account_days', '7', '7', 'INT', 'ai', 'Ngưỡng tài khoản mới (ngày)', 'Tài khoản mới hơn ngưỡng này có trọng số thấp hơn', 47),
  ('ai.sentiment.new_account_weight', '0.5', '0.5', 'DECIMAL', 'ai', 'Trọng số tài khoản mới', 'Trọng số bình luận từ tài khoản mới', 48),
  ('ai.sentiment.timeout_ms', '2000', '2000', 'INT', 'ai', 'Timeout phân tích cảm xúc (ms)', 'Thời gian chờ tối đa cho phân tích cảm xúc', 49),
  ('ai.sentiment.max_retry', '5', '5', 'INT', 'ai', 'Số lần thử lại sentiment', 'Số lần retry khi phân tích cảm xúc lỗi/timeout', 50),
  ('ai.sentiment.low_confidence_threshold', '0.5', '0.5', 'DECIMAL', 'ai', 'Ngưỡng độ tin cậy thấp', 'Dưới ngưỡng này không kích hoạt hành động nặng', 51),
  ('ai.recommendation.enabled', 'true', 'true', 'BOOLEAN', 'ai', 'Bật gợi ý tin', 'Bật/tắt module gợi ý tin đăng', 52),
  ('ai.recommendation.size', '12', '12', 'INT', 'ai', 'Số tin gợi ý', 'Số tin trả về trong một lần gợi ý', 53),
  ('ai.recommendation.cache_ttl_minutes', '15', '15', 'INT', 'ai', 'TTL cache gợi ý (phút)', 'Thời gian sống của cache kết quả gợi ý', 54),
  ('ai.recommendation.promoted_boost', '1.15', '1.15', 'DECIMAL', 'ai', 'Hệ số ưu tiên tin đẩy', 'Hệ số nhân điểm cho tin được đẩy, trần 1.15', 55),
  ('ai.recommendation.timeout_ms', '1500', '1500', 'INT', 'ai', 'Timeout gợi ý (ms)', 'Thời gian chờ tối đa cho gợi ý tin', 56),
  ('ai.recommendation.notify_enabled', 'true', 'true', 'BOOLEAN', 'ai', 'Bật thông báo tin phù hợp', 'Bật/tắt thông báo tin mới phù hợp cho người dùng', 57),
  ('ai.recommendation.notify_min_score', '0.65', '0.65', 'DECIMAL', 'ai', 'Điểm tối thiểu để thông báo', 'Điểm gợi ý tối thiểu để gửi thông báo tin mới', 58),
  ('ai.recommendation.notify_max_per_user', '3', '3', 'INT', 'ai', 'Số thông báo tối đa/người', 'Số thông báo tin phù hợp tối đa mỗi người dùng', 59),
  ('ai.recommendation.notify_lookback_hours', '24', '24', 'INT', 'ai', 'Cửa sổ quét tin mới (giờ)', 'Khoảng thời gian quét tin mới để thông báo', 60),
  ('ai.recommendation.notify_active_user_days', '30', '30', 'INT', 'ai', 'Ngưỡng người dùng hoạt động (ngày)', 'Chỉ thông báo cho người dùng hoạt động trong ngưỡng này', 61),
  ('ai.price.enabled', 'true', 'true', 'BOOLEAN', 'ai', 'Bật dự đoán giá', 'Bật/tắt module dự đoán giá', 62),
  ('ai.price.min_samples', '8', '8', 'INT', 'ai', 'Số mẫu tối thiểu dự đoán giá', 'Thiếu mẫu dưới ngưỡng này trả INSUFFICIENT_DATA', 63),
  ('ai.price.deviation_flag_ratio', '0.35', '0.35', 'DECIMAL', 'ai', 'Ngưỡng lệch giá gắn cờ', 'Tỷ lệ lệch giá so với đề xuất để gắn cờ cảnh báo', 64),
  ('ai.price.timeout_ms', '2000', '2000', 'INT', 'ai', 'Timeout dự đoán giá (ms)', 'Thời gian chờ tối đa cho dự đoán giá', 65),
  ('ai.price.hedonic.furniture_full', '0.12', '0.12', 'DECIMAL', 'ai', 'Hệ số nội thất đầy đủ', 'Hệ số điều chỉnh giá khi nội thất đầy đủ', 66),
  ('ai.price.hedonic.toilet_private', '0.08', '0.08', 'DECIMAL', 'ai', 'Hệ số toilet riêng', 'Hệ số điều chỉnh giá khi có toilet riêng', 67),
  ('ai.price.hedonic.elevator', '0.07', '0.07', 'DECIMAL', 'ai', 'Hệ số thang máy', 'Hệ số điều chỉnh giá khi có thang máy', 68),
  ('ai.price.hedonic.parking', '0.05', '0.05', 'DECIMAL', 'ai', 'Hệ số chỗ để xe', 'Hệ số điều chỉnh giá khi có chỗ để xe', 69),
  ('ai.price.hedonic.curfew_free', '0.03', '0.03', 'DECIMAL', 'ai', 'Hệ số giờ giấc tự do', 'Hệ số điều chỉnh giá khi giờ giấc tự do', 70),
  ('ai.price.hedonic.street_front', '0.15', '0.15', 'DECIMAL', 'ai', 'Hệ số mặt tiền', 'Hệ số điều chỉnh giá khi nhà mặt tiền', 71),
  ('ai.price.comparable_days', '180', '180', 'INT', 'ai', 'Cửa sổ tin so sánh (ngày)', 'Khoảng thời gian lấy tin so sánh để dự đoán giá', 72),
  ('ai.price.comparable_area_tolerance', '0.30', '0.30', 'DECIMAL', 'ai', 'Dung sai diện tích so sánh', 'Biên độ diện tích khi chọn tin so sánh', 73),
  ('ai.chatbot.enabled', 'true', 'true', 'BOOLEAN', 'ai', 'Bật chatbot', 'Bật/tắt module chatbot tư vấn tìm trọ', 74),
  ('ai.chatbot.max_clarify_turns', '3', '3', 'INT', 'ai', 'Số lượt hỏi lại tối đa', 'Số lượt chatbot hỏi lại làm rõ tối đa', 75),
  ('ai.chatbot.timeout_ms', '3000', '3000', 'INT', 'ai', 'Timeout chatbot (ms)', 'Thời gian chờ tối đa cho phản hồi chatbot', 76),
  -- ===== Nhóm security (5) =====
  ('security.login.max_attempts', '5', '5', 'INT', 'security', 'Số lần đăng nhập sai tối đa', 'Số lần đăng nhập sai trước khi khóa tạm', 77),
  ('security.login.window_minutes', '15', '15', 'INT', 'security', 'Cửa sổ đếm đăng nhập sai (phút)', 'Khoảng thời gian đếm số lần đăng nhập sai', 78),
  ('security.login.lock_minutes', '15', '15', 'INT', 'security', 'Thời gian khóa tạm (phút)', 'Thời gian khóa đăng nhập sau khi vượt ngưỡng', 79),
  ('security.login.captcha_after_attempts', '3', '3', 'INT', 'security', 'Số lần sai trước khi hiện captcha', 'Hiện captcha sau số lần đăng nhập sai này', 80),
  ('security.register.rate', '3', '3', 'INT', 'security', 'Giới hạn đăng ký/giờ/IP', 'Số lần đăng ký tối đa mỗi giờ theo IP', 81),
  ('security.refresh.grace_seconds', '10', '10', 'INT', 'security', 'Thời gian ân hạn refresh token (giây)', 'Cho phép token vừa xoay vòng còn dùng thêm để tránh nhầm reuse', 82),
  -- ===== Nhóm spam (6) =====
  ('spam.listing.new_account_daily', '3', '3', 'INT', 'spam', 'Giới hạn đăng tin tài khoản mới/ngày', 'Số tin tối đa mỗi ngày cho tài khoản mới dưới 7 ngày', 83),
  ('spam.listing.daily', '10', '10', 'INT', 'spam', 'Giới hạn đăng tin/ngày', 'Số tin tối đa mỗi ngày cho tài khoản thường', 84),
  ('spam.comment.per_minute', '5', '5', 'INT', 'spam', 'Giới hạn bình luận/phút', 'Số bình luận tối đa mỗi phút', 85),
  ('spam.report.daily', '10', '10', 'INT', 'spam', 'Giới hạn report/ngày', 'Số report tối đa mỗi ngày', 86),
  ('spam.message.per_minute', '30', '30', 'INT', 'spam', 'Giới hạn tin nhắn/phút', 'Số tin nhắn tối đa mỗi phút', 87),
  ('spam.chatbot.per_minute', '30', '30', 'INT', 'spam', 'Giới hạn chatbot/phút', 'Số câu hỏi chatbot tối đa mỗi phút', 88),
  -- ===== Nhóm upload (1) =====
  ('upload.max_pixels', '50000000', '50000000', 'INT', 'upload', 'Số điểm ảnh tối đa', 'Giới hạn số pixel để chặn decompression bomb', 89),
  -- ===== Nhóm contact / view / comment / review / promotion (5) =====
  ('contact.dedup_minutes', '60', '60', 'INT', 'contact', 'Khử trùng liên hệ (phút)', 'Khoảng thời gian gộp liên hệ trùng của cùng người', 90),
  ('view.dedup_minutes', '30', '30', 'INT', 'view', 'Khử trùng lượt xem (phút)', 'Khoảng thời gian gộp lượt xem trùng để không đếm lặp', 91),
  ('comment.edit_window_minutes', '30', '30', 'INT', 'comment', 'Cửa sổ sửa bình luận (phút)', 'Thời gian cho phép sửa bình luận sau khi đăng', 92),
  ('review.edit_window_hours', '24', '24', 'INT', 'review', 'Cửa sổ sửa đánh giá (giờ)', 'Thời gian cho phép sửa đánh giá sau khi đăng', 93),
  ('review.require_contact', 'true', 'true', 'BOOLEAN', 'review', 'Yêu cầu đã liên hệ mới đánh giá', 'Chỉ cho đánh giá khi đã từng liên hệ chủ trọ', 94),
  ('promotion.max_priority', '100', '100', 'INT', 'promotion', 'Độ ưu tiên đẩy tin tối đa', 'Giá trị ưu tiên tối đa của gói đẩy tin', 95),
  -- ===== Nhóm chat / chatbot (2) =====
  ('chat.message.max_length', '2000', '2000', 'INT', 'chat', 'Độ dài tin nhắn tối đa', 'Số ký tự tối đa của tin nhắn chat nội bộ', 96),
  ('chatbot.message.max_length', '500', '500', 'INT', 'chatbot', 'Độ dài câu hỏi chatbot tối đa', 'Số ký tự tối đa của câu hỏi gửi chatbot', 97),
  -- ===== Nhóm report (2) =====
  ('report.abuse.rejected_count', '5', '5', 'INT', 'report', 'Ngưỡng report sai bị hạn chế', 'Số report bị từ chối trước khi hạn chế người dùng', 98),
  ('report.abuse.window_days', '30', '30', 'INT', 'report', 'Cửa sổ đếm report sai (ngày)', 'Khoảng thời gian đếm số report bị từ chối', 99),
  -- ===== Nhóm payment (2) =====
  ('payment.order.expiry_minutes', '30', '30', 'INT', 'payment', 'Thời gian hết hạn đơn (phút)', 'Đơn PENDING quá hạn này sẽ chuyển FAILED', 100),
  ('payment.callback.max_skew_seconds', '300', '300', 'INT', 'payment', 'Độ lệch callback tối đa (giây)', 'Chống replay callback thanh toán', 101),
  -- ===== Nhóm search (2) =====
  ('search.keyword.max_length', '100', '100', 'INT', 'search', 'Độ dài từ khóa tìm kiếm tối đa', 'Số ký tự tối đa của từ khóa tìm kiếm', 102),
  ('search.amenity_filter.max_count', '20', '20', 'INT', 'search', 'Số tiện ích lọc tối đa', 'Số lượng tiện ích tối đa trong một bộ lọc', 103),
  -- ===== Nhóm page (2) — value_type=JSON chứa HTML tĩnh mẫu =====
  ('page.about', '"<div class=about-page><h1>Giới thiệu WebTro</h1><p>WebTro là nền tảng kết nối người thuê trọ và chủ nhà trọ trên toàn quốc, giúp tìm phòng nhanh chóng, minh bạch và an toàn.</p><p>Chúng tôi cung cấp công cụ tìm kiếm thông minh, gợi ý tin phù hợp và hệ thống kiểm duyệt nhằm hạn chế tin giả.</p></div>"', '"<div class=about-page><h1>Giới thiệu WebTro</h1><p>WebTro là nền tảng kết nối người thuê trọ và chủ nhà trọ trên toàn quốc, giúp tìm phòng nhanh chóng, minh bạch và an toàn.</p><p>Chúng tôi cung cấp công cụ tìm kiếm thông minh, gợi ý tin phù hợp và hệ thống kiểm duyệt nhằm hạn chế tin giả.</p></div>"', 'JSON', 'page', 'Nội dung trang Giới thiệu', 'Nội dung HTML tĩnh trang Giới thiệu, Admin sửa qua giao diện', 104),
  ('page.terms', '"<div class=terms-page><h1>Điều khoản sử dụng</h1><p>Khi sử dụng WebTro, người dùng cam kết cung cấp thông tin trung thực và tuân thủ pháp luật Việt Nam.</p><p>Nghiêm cấm đăng tin sai sự thật, lừa đảo hoặc nội dung vi phạm thuần phong mỹ tục. WebTro có quyền ẩn hoặc khóa tin vi phạm.</p></div>"', '"<div class=terms-page><h1>Điều khoản sử dụng</h1><p>Khi sử dụng WebTro, người dùng cam kết cung cấp thông tin trung thực và tuân thủ pháp luật Việt Nam.</p><p>Nghiêm cấm đăng tin sai sự thật, lừa đảo hoặc nội dung vi phạm thuần phong mỹ tục. WebTro có quyền ẩn hoặc khóa tin vi phạm.</p></div>"', 'JSON', 'page', 'Nội dung trang Điều khoản', 'Nội dung HTML tĩnh trang Điều khoản, Admin sửa qua giao diện', 105);

-- =====================================================================================
-- Kiểm chứng máy móc: bảng được seed = system_configs.
-- Số dòng INSERT (số bản ghi VALUES) = 105 — khớp canonical mục 9 (57 v1 + 28 v2 + 20 v2.1).
--   SELECT COUNT(*) FROM system_configs;  -- kỳ vọng: 105
-- =====================================================================================
