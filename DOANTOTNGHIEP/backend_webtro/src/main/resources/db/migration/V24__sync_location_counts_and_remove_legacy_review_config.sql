-- Rebuild denormalized location counters from the same public-visibility rule
-- used by ListingVisibilityService and the category counter migration.

UPDATE wards w
LEFT JOIN (
  SELECT l.ward_id, COUNT(*) AS listing_count
  FROM listings l
  WHERE l.deleted_at IS NULL
    AND (
      l.status = 'ACTIVE'
      OR (
        l.status = 'NEED_REVIEW'
        AND COALESCE((
          SELECT LOWER(TRIM(sc.config_value))
          FROM system_configs sc
          WHERE sc.config_key = 'listing.need_review.publicly_visible'
            AND sc.deleted_at IS NULL
          LIMIT 1
        ), 'true') = 'true'
      )
    )
  GROUP BY l.ward_id
) x ON x.ward_id = w.id
SET w.listing_count = COALESCE(x.listing_count, 0);

UPDATE districts d
LEFT JOIN (
  SELECT w.district_id, SUM(w.listing_count) AS listing_count
  FROM wards w
  GROUP BY w.district_id
) x ON x.district_id = d.id
SET d.listing_count = COALESCE(x.listing_count, 0);

UPDATE provinces p
LEFT JOIN (
  SELECT d.province_id, SUM(d.listing_count) AS listing_count
  FROM districts d
  GROUP BY d.province_id
) x ON x.province_id = p.id
SET p.listing_count = COALESCE(x.listing_count, 0);

-- V23 replaced contact-based review eligibility with verified rental history.
DELETE FROM system_configs WHERE config_key = 'review.require_contact';
