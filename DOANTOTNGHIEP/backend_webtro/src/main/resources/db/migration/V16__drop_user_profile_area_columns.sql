-- Remove unused administrative-area links from user_profiles.
-- The profile screen stores only address_detail text and never sets province_id/district_id.

SET @fk_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND CONSTRAINT_NAME = 'fk_user_profiles_provinces'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP FOREIGN KEY fk_user_profiles_provinces');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND CONSTRAINT_NAME = 'fk_user_profiles_districts'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP FOREIGN KEY fk_user_profiles_districts');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND INDEX_NAME = 'idx_user_profiles_province_id'
);
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP INDEX idx_user_profiles_province_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND INDEX_NAME = 'idx_user_profiles_district_id'
);
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP INDEX idx_user_profiles_district_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND COLUMN_NAME = 'province_id'
);
SET @sql := IF(@column_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP COLUMN province_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profiles'
    AND COLUMN_NAME = 'district_id'
);
SET @sql := IF(@column_exists = 0, 'SET @noop = 0', 'ALTER TABLE user_profiles DROP COLUMN district_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
