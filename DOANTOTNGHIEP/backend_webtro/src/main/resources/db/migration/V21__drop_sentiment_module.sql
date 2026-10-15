-- =====================================================================================
-- V21 — Loại bỏ module phân tích cảm xúc (sentiment) khỏi hệ thống
-- =====================================================================================
-- Module sentiment đã bị loại khỏi phạm vi đồ án; code Java tương ứng (analyzer, service,
-- listener, job retry, API /api/ai/sentiment/**, cảnh báo AI_NEGATIVE_ALERT...) đã bị xóa.
-- Migration này dọn schema + dữ liệu để khớp entity (ddl-auto=validate):
--   1. Bảng sentiment_results.
--   2. comments: cột sentiment_* và is_risk_comment (+ CHECK/INDEX phụ thuộc).
--   3. listings: positive_comment_count / negative_comment_count (+ CHECK bộ đếm).
--   4. Loại thông báo AI_NEGATIVE_ALERT (dữ liệu + CHECK notifications / notification_preferences).
--   5. ai_configs: module SENTIMENT (dữ liệu + CHECK).
--   6. system_configs: khóa ai.sentiment.*, trọng số uy tín theo bình luận, autohide theo sentiment.
--   7. Tính lại điểm uy tín tin theo công thức mới (bỏ hai số hạng Pos·wPos − Neg·wNeg).
--
-- MySQL 8: phải DROP CHECK / INDEX tham chiếu cột trước khi DROP COLUMN; đổi CHECK = DROP rồi ADD.
-- Không thể hoàn tác dữ liệu sentiment sau khi chạy.
-- =====================================================================================

-- 1. sentiment_results ----------------------------------------------------------------
DROP TABLE IF EXISTS sentiment_results;

-- 2. comments -------------------------------------------------------------------------
ALTER TABLE comments
  DROP CHECK ck_comments_sentiment_label,
  DROP CHECK ck_comments_sentiment_score,
  DROP CHECK ck_comments_confidence,
  DROP CHECK ck_comments_weight;

ALTER TABLE comments
  DROP INDEX idx_comments_sentiment_label_status,
  DROP INDEX idx_comments_listing_id_sentiment,
  DROP INDEX idx_comments_is_risk_comment;

ALTER TABLE comments
  DROP COLUMN sentiment_label,
  DROP COLUMN sentiment_score,
  DROP COLUMN sentiment_confidence,
  DROP COLUMN sentiment_weight,
  DROP COLUMN is_risk_comment;

-- 3. listings -------------------------------------------------------------------------
ALTER TABLE listings DROP CHECK ck_listings_counters_non_negative;

ALTER TABLE listings
  DROP COLUMN positive_comment_count,
  DROP COLUMN negative_comment_count;

ALTER TABLE listings ADD CONSTRAINT ck_listings_counters_non_negative CHECK (
    view_count >= 0 AND favorite_count >= 0 AND contact_count >= 0 AND comment_count >= 0
    AND review_count >= 0 AND need_review_count >= 0 AND renew_count >= 0);

-- 4. AI_NEGATIVE_ALERT ----------------------------------------------------------------
DELETE FROM notifications WHERE type = 'AI_NEGATIVE_ALERT';
DELETE FROM notification_preferences WHERE notification_type = 'AI_NEGATIVE_ALERT';

ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
    'LISTING_LOCKED','LISTING_HIDDEN','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW',
    'PAYMENT_SUCCESS','PAYMENT_FAILED','REPORT_THRESHOLD','ACCOUNT_LOCKED',
    'VIOLATION_WARNING','ACCOUNT_VERIFICATION','FOLLOWED_LANDLORD_NEW_LISTING','NEW_MATCHING_LISTING'));

ALTER TABLE notification_preferences
  DROP CHECK ck_notification_preferences_type,
  DROP CHECK ck_notification_preferences_mandatory;
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_type CHECK (notification_type IN (
    'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
    'LISTING_LOCKED','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW','PAYMENT_SUCCESS','PAYMENT_FAILED',
    'REPORT_THRESHOLD','ACCOUNT_LOCKED','VIOLATION_WARNING',
    'FOLLOWED_LANDLORD_NEW_LISTING'));
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_mandatory CHECK (
    notification_type NOT IN (
        'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_LOCKED',
        'PAYMENT_SUCCESS','PAYMENT_FAILED','ACCOUNT_LOCKED','VIOLATION_WARNING',
        'REPORT_THRESHOLD')
    OR (in_app = TRUE AND email = TRUE));

-- 5. ai_configs -----------------------------------------------------------------------
DELETE FROM ai_configs WHERE module = 'SENTIMENT';

ALTER TABLE ai_configs DROP CHECK ck_ai_configs_module;
ALTER TABLE ai_configs ADD CONSTRAINT ck_ai_configs_module CHECK (module IN ('RECOMMENDATION','CHATBOT','PRICE'));

-- 6. system_configs -------------------------------------------------------------------
DELETE FROM system_configs
WHERE config_key LIKE 'ai.sentiment.%'
   OR config_key IN ('trust.weight.positive_comment',
                     'trust.weight.negative_comment',
                     'moderation.autohide.sentiment_requires_prior_warning');

-- 7. Tính lại điểm uy tín tin -----------------------------------------------------------
-- Khớp TrustScoreServiceImpl.recalculateAndSaveListing: base + avg_rating·wRating, kẹp [min, max]
-- (báo cáo hợp lệ / cảnh báo tính ở cấp chủ trọ). Điểm chủ trọ được TrustScoreRecalcJob tính lại
-- ở lần chạy kế tiếp (02:00 UTC).
UPDATE listings l
JOIN (
    SELECT
      MAX(CASE WHEN config_key = 'trust.base_score' THEN CAST(config_value AS DECIMAL(10,4)) END) AS base_score,
      MAX(CASE WHEN config_key = 'trust.weight.average_rating' THEN CAST(config_value AS DECIMAL(10,4)) END) AS w_rating,
      MAX(CASE WHEN config_key = 'trust.min' THEN CAST(config_value AS DECIMAL(10,4)) END) AS t_min,
      MAX(CASE WHEN config_key = 'trust.max' THEN CAST(config_value AS DECIMAL(10,4)) END) AS t_max
    FROM system_configs
    WHERE deleted_at IS NULL
) c
SET l.trust_score = GREATEST(c.t_min, LEAST(c.t_max,
        ROUND(c.base_score + IFNULL(l.average_rating, 0) * c.w_rating, 0)))
WHERE l.deleted_at IS NULL
  AND c.base_score IS NOT NULL AND c.w_rating IS NOT NULL
  AND c.t_min IS NOT NULL AND c.t_max IS NOT NULL;
