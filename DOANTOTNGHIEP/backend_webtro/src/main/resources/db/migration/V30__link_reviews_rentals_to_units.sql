-- =====================================================================================
-- V29 — Gắn đánh giá và yêu cầu thuê vào Phòng; tính lại điểm theo phòng
-- =====================================================================================
-- Thiết kế: docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3.2 và mục 10.
--   1. reviews.rental_unit_id (NOT NULL). Mỗi người 1 đánh giá còn hiệu lực cho mỗi phòng:
--      unique (user_id, rental_unit_id, active_marker) thay cho (user_id, listing_id, active_marker).
--      reviews.listing_id giữ lại để biết người thuê thuê qua tin nào; FK đổi CASCADE -> SET NULL
--      để xoá cứng tin (nếu có) không làm mất đánh giá.
--   2. rental_requests.rental_unit_id (NOT NULL) để kiểm tra quyền đánh giá theo phòng.
--   3. Điểm phòng = AVG/COUNT đánh giá VISIBLE của phòng. listings.average_rating/review_count
--      là BẢN SAO của điểm phòng (chép từ rental_units, không tính riêng).
--   4. Điểm chủ trọ = AVG/COUNT đánh giá VISIBLE của mọi phòng thuộc chủ trọ.
--   5. Phòng đã có đánh giá VISIBLE thì khoá địa chỉ (RU-4).
-- =====================================================================================

-- 1. reviews --------------------------------------------------------------------------
ALTER TABLE reviews DROP FOREIGN KEY fk_reviews_listings;

ALTER TABLE reviews
    ADD COLUMN rental_unit_id BIGINT UNSIGNED NULL AFTER listing_id;

UPDATE reviews r
    JOIN listings l ON l.id = r.listing_id
SET r.rental_unit_id = l.rental_unit_id,
    r.updated_at     = r.updated_at;

ALTER TABLE reviews
    MODIFY COLUMN rental_unit_id BIGINT UNSIGNED NOT NULL,
    MODIFY COLUMN listing_id     BIGINT UNSIGNED NULL,
    DROP INDEX uk_reviews_active_user_listing,
    ADD UNIQUE KEY uk_reviews_active_user_unit (user_id, rental_unit_id, active_marker),
    ADD KEY idx_reviews_unit_status_created (rental_unit_id, status, created_at),
    ADD CONSTRAINT fk_reviews_rental_units FOREIGN KEY (rental_unit_id)
        REFERENCES rental_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_reviews_listings FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- 2. rental_requests --------------------------------------------------------------------
ALTER TABLE rental_requests
    ADD COLUMN rental_unit_id BIGINT UNSIGNED NULL AFTER listing_id;

UPDATE rental_requests rr
    JOIN listings l ON l.id = rr.listing_id
SET rr.rental_unit_id = l.rental_unit_id,
    rr.updated_at     = rr.updated_at;

ALTER TABLE rental_requests
    MODIFY COLUMN rental_unit_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_rental_requests_unit_tenant_status (rental_unit_id, tenant_id, status),
    ADD CONSTRAINT fk_rental_requests_rental_unit FOREIGN KEY (rental_unit_id)
        REFERENCES rental_units (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 3. Điểm phòng --------------------------------------------------------------------------
UPDATE rental_units u
    LEFT JOIN (
        SELECT r.rental_unit_id, COUNT(*) AS cnt, ROUND(AVG(r.rating), 2) AS avg_rating
        FROM reviews r
        WHERE r.status = 'VISIBLE'
          AND r.deleted_at IS NULL
        GROUP BY r.rental_unit_id
    ) x ON x.rental_unit_id = u.id
SET u.review_count   = COALESCE(x.cnt, 0),
    u.average_rating = COALESCE(x.avg_rating, 0.00),
    u.address_locked = (COALESCE(x.cnt, 0) > 0),
    u.updated_at     = u.updated_at;

-- Bản sao điểm phòng trên mọi tin của phòng (kể cả tin đã đóng / đã xoá mềm).
UPDATE listings l
    JOIN rental_units u ON u.id = l.rental_unit_id
SET l.average_rating = u.average_rating,
    l.review_count   = u.review_count,
    l.updated_at     = l.updated_at;

-- 4. Điểm chủ trọ -----------------------------------------------------------------------
UPDATE landlord_profiles lp
    LEFT JOIN (
        SELECT u.owner_id, COUNT(*) AS cnt, ROUND(AVG(r.rating), 2) AS avg_rating
        FROM reviews r
            JOIN rental_units u ON u.id = r.rental_unit_id
        WHERE r.status = 'VISIBLE'
          AND r.deleted_at IS NULL
        GROUP BY u.owner_id
    ) x ON x.owner_id = lp.user_id
SET lp.review_count   = COALESCE(x.cnt, 0),
    lp.average_rating = COALESCE(x.avg_rating, 0.00),
    lp.updated_at     = lp.updated_at;
