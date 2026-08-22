-- Normalize transitive foreign keys where the intermediate FK is mandatory.
-- Kept reports/moderation_actions listing links because their report/listing paths are nullable/polymorphic.

-- listings: ward_id -> wards -> districts -> provinces
SET @schema_name := DATABASE();

SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_listings_provinces';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE listings DROP FOREIGN KEY fk_listings_provinces');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_listings_districts';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE listings DROP FOREIGN KEY fk_listings_districts');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'listings' AND INDEX_NAME = 'idx_listings_search';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE listings DROP INDEX idx_listings_search');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'listings' AND COLUMN_NAME = 'province_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE listings DROP COLUMN province_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'listings' AND COLUMN_NAME = 'district_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE listings DROP COLUMN district_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'listings' AND INDEX_NAME = 'idx_listings_search';
SET @sql := IF(@idx_exists > 0, 'SET @noop = 0',
    'ALTER TABLE listings ADD INDEX idx_listings_search (status, ward_id, category_id, price, area)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- contact_logs: owner_id is derived through contact_logs.listing_id -> listings.owner_id
SELECT COUNT(*) INTO @check_exists FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'contact_logs'
  AND CONSTRAINT_NAME = 'ck_contact_logs_not_self' AND CONSTRAINT_TYPE = 'CHECK';
SET @sql := IF(@check_exists = 0, 'SET @noop = 0', 'ALTER TABLE contact_logs DROP CHECK ck_contact_logs_not_self');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_contact_logs_users_owner';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE contact_logs DROP FOREIGN KEY fk_contact_logs_users_owner');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'contact_logs' AND INDEX_NAME = 'idx_contact_logs_owner_id_created_at';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE contact_logs DROP INDEX idx_contact_logs_owner_id_created_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'contact_logs' AND COLUMN_NAME = 'owner_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE contact_logs DROP COLUMN owner_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- conversations: landlord_id is derived through conversations.listing_id -> listings.owner_id
SELECT COUNT(*) INTO @check_exists FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'conversations'
  AND CONSTRAINT_NAME = 'ck_conversations_not_self' AND CONSTRAINT_TYPE = 'CHECK';
SET @sql := IF(@check_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP CHECK ck_conversations_not_self');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_conversations_users_landlord';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP FOREIGN KEY fk_conversations_users_landlord');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'uk_conversations_listing_tenant_landlord';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP INDEX uk_conversations_listing_tenant_landlord');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'idx_conversations_landlord_id_last_message_at';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP INDEX idx_conversations_landlord_id_last_message_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'idx_conversations_landlord_id_created_at';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP INDEX idx_conversations_landlord_id_created_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND COLUMN_NAME = 'landlord_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE conversations DROP COLUMN landlord_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'uk_conversations_listing_tenant';
SET @sql := IF(@idx_exists > 0, 'SET @noop = 0',
    'ALTER TABLE conversations ADD UNIQUE INDEX uk_conversations_listing_tenant (listing_id, tenant_id)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'idx_conversations_listing_id_last_message_at';
SET @sql := IF(@idx_exists > 0, 'SET @noop = 0',
    'ALTER TABLE conversations ADD INDEX idx_conversations_listing_id_last_message_at (listing_id, last_message_at)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reviews: landlord_id is derived through reviews.listing_id -> listings.owner_id
SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_reviews_users_landlord';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE reviews DROP FOREIGN KEY fk_reviews_users_landlord');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'idx_reviews_landlord_id_status';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE reviews DROP INDEX idx_reviews_landlord_id_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'landlord_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE reviews DROP COLUMN landlord_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sentiment_results: listing_id is derived through sentiment_results.comment_id -> comments.listing_id
SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sentiment_results' AND INDEX_NAME = 'idx_sentiment_results_listing_id_label';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE sentiment_results DROP INDEX idx_sentiment_results_listing_id_label');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sentiment_results' AND COLUMN_NAME = 'listing_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE sentiment_results DROP COLUMN listing_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- prediction_histories: province_id/district_id are derived through prediction_histories.ward_id
SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_prediction_histories_provinces';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP FOREIGN KEY fk_prediction_histories_provinces');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @fk_exists FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name AND CONSTRAINT_NAME = 'fk_prediction_histories_districts';
SET @sql := IF(@fk_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP FOREIGN KEY fk_prediction_histories_districts');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'prediction_histories' AND INDEX_NAME = 'idx_prediction_histories_province_id';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP INDEX idx_prediction_histories_province_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'prediction_histories' AND INDEX_NAME = 'idx_prediction_histories_district_id';
SET @sql := IF(@idx_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP INDEX idx_prediction_histories_district_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'prediction_histories' AND COLUMN_NAME = 'province_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP COLUMN province_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'prediction_histories' AND COLUMN_NAME = 'district_id';
SET @sql := IF(@col_exists = 0, 'SET @noop = 0', 'ALTER TABLE prediction_histories DROP COLUMN district_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
