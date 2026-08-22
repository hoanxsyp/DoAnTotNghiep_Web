-- Backfill landlord listing aggregates used by profile screens.

UPDATE landlord_profiles lp
LEFT JOIN (
  SELECT
    l.owner_id,
    COUNT(*) AS total_listings,
    SUM(CASE WHEN l.status = 'ACTIVE' THEN 1 ELSE 0 END) AS total_active_listings
  FROM listings l
  WHERE l.deleted_at IS NULL
  GROUP BY l.owner_id
) x ON x.owner_id = lp.user_id
SET lp.total_listings = COALESCE(x.total_listings, 0),
    lp.total_active_listings = COALESCE(x.total_active_listings, 0)
WHERE lp.deleted_at IS NULL;
