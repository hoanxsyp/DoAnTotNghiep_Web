-- =====================================================================================
-- V9 — Seed ai_configs: bật/tắt + cấu hình mặc định cho 4 module AI [§10.10]
-- -------------------------------------------------------------------------------------
-- Mỗi module AI (SENTIMENT, RECOMMENDATION, CHATBOT, PRICE) có 1 dòng cấu hình mặc định.
-- Bảng ai_configs có UNIQUE (module, config_key) nên dùng config_key='default_params'
-- làm bản ghi cấu hình gốc để Admin bật/tắt và chỉnh tham số từng module.
-- config_value: JSON tham số mặc định. is_enabled=TRUE (bật sẵn). value_schema='JSON'.
-- Idempotent không bắt buộc (Flyway chỉ chạy 1 lần). Không FK phụ thuộc.
-- =====================================================================================

INSERT INTO ai_configs (id, module, config_key, config_value, value_schema, description, is_enabled, version, created_at, updated_at)
VALUES
  (1, 'SENTIMENT', 'default_params',
   CAST('{"provider":"internal","model":"phobert-sentiment-vi","language":"vi","threshold_positive":0.60,"threshold_negative":0.40,"batch_size":32,"timeout_ms":5000,"auto_hide_toxic":true}' AS JSON),
   'JSON',
   N'Phân tích cảm xúc bình luận/đánh giá phòng trọ (tích cực/tiêu cực/trung tính); tự động ẩn nội dung độc hại.',
   TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

  (2, 'RECOMMENDATION', 'default_params',
   CAST('{"provider":"internal","algorithm":"hybrid","top_n":12,"weight_content":0.60,"weight_collaborative":0.40,"radius_km":5,"min_interactions":3,"cache_ttl_minutes":30}' AS JSON),
   'JSON',
   N'Gợi ý phòng trọ phù hợp cho người dùng dựa trên lịch sử xem, vị trí và mức giá (lai nội dung + cộng tác).',
   TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

  (3, 'CHATBOT', 'default_params',
   CAST('{"provider":"openai","model":"gpt-4o-mini","language":"vi","temperature":0.30,"max_tokens":1024,"history_turns":6,"system_prompt":"Bạn là trợ lý tìm phòng trọ, trả lời ngắn gọn bằng tiếng Việt.","fallback_to_human":true}' AS JSON),
   'JSON',
   N'Trợ lý ảo hỗ trợ người thuê tìm phòng, giải đáp thắc mắc và hướng dẫn đặt lịch xem phòng bằng tiếng Việt.',
   TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

  (4, 'PRICE', 'default_params',
   CAST('{"provider":"internal","model":"price-estimator-vi","currency":"VND","confidence_min":0.70,"deviation_warn_percent":20,"features":["area_m2","province_id","district_id","amenities","room_type"],"refresh_days":7}' AS JSON),
   'JSON',
   N'Ước lượng giá thuê hợp lý và cảnh báo lệch giá thị trường dựa trên diện tích, khu vực và tiện ích.',
   TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
