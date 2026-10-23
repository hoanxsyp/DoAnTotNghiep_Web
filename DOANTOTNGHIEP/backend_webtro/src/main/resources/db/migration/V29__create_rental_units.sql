-- =====================================================================================
-- V28 — Tách thực thể Phòng (rental_units) khỏi Tin đăng (listings)
-- =====================================================================================
-- Thiết kế: docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3 (đã chốt 2026-10-22).
--   1. Bảng rental_units: phòng thật của chủ trọ, giữ địa chỉ gốc và điểm đánh giá của phòng.
--   2. Backfill: mỗi tin hiện có tạo đúng 1 phòng (KHÔNG tự gộp — gộp sai còn tệ hơn không gộp).
--      Phòng của tin đã xoá mềm được lưu trữ (ARCHIVED).
--   3. listings.rental_unit_id (NOT NULL, FK), listings.duplicate_address (cờ trùng địa chỉ với
--      phòng của chủ trọ khác, RU-6), listings.close_reason (trước đây chỉ trả về trong response).
-- Điểm đánh giá của phòng được tính ở V29 (sau khi reviews có rental_unit_id).
-- Giữ nguyên updated_at của listings: đây là dọn dữ liệu, không phải chủ trọ sửa tin.
-- =====================================================================================

-- 1. Bảng phòng ------------------------------------------------------------------------
CREATE TABLE rental_units (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  owner_id          BIGINT UNSIGNED  NOT NULL,
  name              VARCHAR(150)     NOT NULL,
  unit_label        VARCHAR(50)      NULL,
  ward_id           BIGINT UNSIGNED  NOT NULL,
  address_detail    VARCHAR(255)     NOT NULL,
  latitude          DECIMAL(10,7)    NULL,
  longitude         DECIMAL(10,7)    NULL,
  location_source   VARCHAR(10)      NULL,
  status            VARCHAR(10)      NOT NULL DEFAULT 'ACTIVE',
  archived_at       DATETIME(6)      NULL,
  address_locked    BOOLEAN          NOT NULL DEFAULT FALSE,
  average_rating    DECIMAL(3,2)     NOT NULL DEFAULT 0.00,
  review_count      INT UNSIGNED     NOT NULL DEFAULT 0,
  legacy_listing_id BIGINT UNSIGNED  NULL,
  created_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by        BIGINT UNSIGNED  NULL,
  updated_by        BIGINT UNSIGNED  NULL,
  deleted_at        DATETIME(6)      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rental_units_legacy_listing (legacy_listing_id),
  KEY idx_rental_units_owner_status (owner_id, status),
  KEY idx_rental_units_ward_status (ward_id, status),
  CONSTRAINT fk_rental_units_users FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_rental_units_wards FOREIGN KEY (ward_id)  REFERENCES wards (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_rental_units_status CHECK (status IN ('ACTIVE','ARCHIVED')),
  CONSTRAINT ck_rental_units_archived_at CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL)),
  CONSTRAINT ck_rental_units_location_source CHECK (location_source IS NULL OR location_source IN ('OWNER','WARD')),
  CONSTRAINT ck_rental_units_latitude  CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_rental_units_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_rental_units_rating CHECK (average_rating BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Backfill: 1 tin = 1 phòng --------------------------------------------------------
INSERT INTO rental_units (owner_id, name, ward_id, address_detail, latitude, longitude, location_source,
                          status, archived_at, legacy_listing_id,
                          created_at, updated_at, created_by, updated_by)
SELECT l.owner_id,
       LEFT(l.title, 150),
       l.ward_id,
       l.address_detail,
       l.latitude,
       l.longitude,
       l.location_source,
       CASE WHEN l.deleted_at IS NULL THEN 'ACTIVE' ELSE 'ARCHIVED' END,
       l.deleted_at,
       l.id,
       l.created_at,
       l.updated_at,
       l.created_by,
       l.updated_by
FROM listings l;

-- 3. Cột mới trên listings -------------------------------------------------------------
ALTER TABLE listings
    ADD COLUMN rental_unit_id    BIGINT UNSIGNED NULL AFTER owner_id,
    ADD COLUMN duplicate_address BOOLEAN         NOT NULL DEFAULT FALSE AFTER price_deviation_flag,
    ADD COLUMN close_reason      VARCHAR(25)     NULL AFTER auto_hide_reason,
    ADD CONSTRAINT ck_listings_close_reason
        CHECK (close_reason IS NULL OR close_reason IN ('RENTED_OUT','NO_LONGER_AVAILABLE','OTHER'));

UPDATE listings l
    JOIN rental_units u ON u.legacy_listing_id = l.id
SET l.rental_unit_id = u.id,
    l.updated_at     = l.updated_at;

ALTER TABLE listings
    MODIFY COLUMN rental_unit_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_listings_rental_unit_status (rental_unit_id, status),
    ADD CONSTRAINT fk_listings_rental_units FOREIGN KEY (rental_unit_id)
        REFERENCES rental_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 4. Bỏ cột tạm dùng để ghép phòng ↔ tin -------------------------------------------------
ALTER TABLE rental_units
    DROP INDEX uk_rental_units_legacy_listing,
    DROP COLUMN legacy_listing_id;
