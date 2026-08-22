-- Backfill categories.listing_count from current public listings.
-- ACTIVE is always public. NEED_REVIEW follows listing.need_review.publicly_visible.

UPDATE categories c
LEFT JOIN (
  SELECT
    l.category_id,
    COUNT(*) AS listing_count
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
  GROUP BY l.category_id
) x ON x.category_id = c.id
SET c.listing_count = COALESCE(x.listing_count, 0);
