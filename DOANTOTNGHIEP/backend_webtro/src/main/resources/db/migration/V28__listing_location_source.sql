-- =====================================================================================
-- V27 — Nguồn gốc tọa độ tin đăng + điền tọa độ tâm phường cho tin chưa có tọa độ
-- =====================================================================================
-- Form đăng tin chưa có ô chọn vị trí nên mọi tin đều thiếu latitude/longitude, khiến lọc theo
-- bán kính (tìm kiếm, chatbot "gần trường X") luôn rỗng. Từ nay tin thiếu tọa độ được gán tạm
-- tọa độ tâm phường (ListingServiceImpl.applyCoordinates) — như ước lượng giá vẫn làm.
--   1. listings.location_source: OWNER = chủ trọ tự cung cấp (chính xác),
--                                WARD  = tâm phường (gần đúng, giao diện không cắm ghim chính xác).
--   2. Tin đã có tọa độ -> OWNER.
--   3. Tin thiếu tọa độ -> tọa độ tâm phường, WARD (phường chưa có tọa độ thì để trống).
-- Giữ nguyên updated_at: đây là dọn dữ liệu, không phải chủ trọ sửa tin.
-- =====================================================================================

-- 1. Cột mới ---------------------------------------------------------------------------
ALTER TABLE listings
    ADD COLUMN location_source VARCHAR(10) NULL AFTER longitude,
    ADD CONSTRAINT ck_listings_location_source
        CHECK (location_source IS NULL OR location_source IN ('OWNER','WARD'));

-- 2. Tin đã có tọa độ ------------------------------------------------------------------
UPDATE listings
SET location_source = 'OWNER',
    updated_at      = updated_at
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL;

-- 3. Tin thiếu tọa độ -> tâm phường -----------------------------------------------------
UPDATE listings l
    JOIN wards w ON w.id = l.ward_id
SET l.latitude        = w.latitude,
    l.longitude       = w.longitude,
    l.location_source = 'WARD',
    l.updated_at      = l.updated_at
WHERE (l.latitude IS NULL OR l.longitude IS NULL)
  AND w.latitude IS NOT NULL
  AND w.longitude IS NOT NULL;
