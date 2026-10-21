-- Chỉ chặn trùng đối với đánh giá chưa xóa mềm. MySQL cho phép nhiều giá trị NULL
-- trong unique index, vì vậy active_marker = NULL giải phóng cặp user/listing đã xóa.
ALTER TABLE reviews
  DROP INDEX uk_reviews_user_listing,
  MODIFY COLUMN content VARCHAR(2000) NULL,
  ADD COLUMN active_marker TINYINT NULL DEFAULT 1,
  ADD UNIQUE KEY uk_reviews_active_user_listing (user_id, listing_id, active_marker);

UPDATE reviews
SET active_marker = NULL
WHERE deleted_at IS NOT NULL;
