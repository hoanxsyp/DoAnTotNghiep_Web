-- =====================================================================================
-- V22 — Loại bỏ tính năng điểm uy tín (trust score) của tin đăng và chủ trọ
-- =====================================================================================
-- Hệ thống không còn tính/hiển thị điểm uy tín (TrustScoreService, TrustScoreRecalcJob, nhãn
-- TrustLabel, bộ lọc minTrustScore, sắp xếp TRUST_DESC, số hạng uy tín trong gợi ý tin đã bị xóa).
-- Số sao đánh giá (average_rating), số cảnh báo vi phạm (warning_count) và các bộ đếm khác giữ
-- nguyên. Migration dọn schema + dữ liệu để khớp entity (ddl-auto=validate):
--   1. listings.trust_score (+ INDEX, CHECK).
--   2. landlord_profiles.trust_score (+ INDEX, CHECK).
--   3. recommendation_logs.trust_score_norm (điểm thành phần uy tín của log gợi ý).
--   4. system_configs: toàn bộ khóa trust.*.
-- MySQL 8: phải DROP CHECK / INDEX tham chiếu cột trước khi DROP COLUMN.
-- =====================================================================================

-- 1. listings -------------------------------------------------------------------------
ALTER TABLE listings DROP CHECK ck_listings_trust_score;
ALTER TABLE listings DROP INDEX idx_listings_status_trust_score;
ALTER TABLE listings DROP COLUMN trust_score;

-- 2. landlord_profiles ----------------------------------------------------------------
ALTER TABLE landlord_profiles DROP CHECK ck_landlord_profiles_trust;
ALTER TABLE landlord_profiles DROP INDEX idx_landlord_profiles_trust_score;
ALTER TABLE landlord_profiles DROP COLUMN trust_score;

-- 3. recommendation_logs --------------------------------------------------------------
ALTER TABLE recommendation_logs DROP COLUMN trust_score_norm;

-- 4. system_configs -------------------------------------------------------------------
DELETE FROM system_configs WHERE config_key LIKE 'trust.%';
