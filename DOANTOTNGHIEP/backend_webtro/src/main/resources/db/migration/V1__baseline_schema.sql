-- =====================================================================================
-- V1__baseline_schema.sql — Baseline schema (46 tables) for project "webtro"
-- Spring Boot 3.3.5 / Java 21 / MySQL 8.4 / Flyway
-- Charset utf8mb4, collation utf8mb4_unicode_ci, engine InnoDB, timezone UTC.
-- Source of truth: docs/00_CANONICAL_DECISIONS.md + docs/02_THIET_KE_DATABASE.md (section 3).
--
-- Conventions (canonical section 2 / doc 02 section 1):
--   PK: id BIGINT UNSIGNED AUTO_INCREMENT. Money DECIMAL(15,2). Area DECIMAL(8,2).
--   Lat/lng DECIMAL(10,7). Time DATETIME(6) stored in UTC. Enums as VARCHAR + CHECK.
--   Conditional-unique via STORED generated columns (users.email_uk/phone_uk,
--   sentiment_results.latest_uk).
--
-- NOTE on creation order (see final report): tables are created in the order requested.
-- Four foreign keys are FORWARD references under that order and are added via ALTER TABLE
-- at the END of this file (same technique doc 02 mandates for the listings<->prediction cycle):
--   fk_payments_coupons,
--   fk_listings_prediction_histories.
-- No FULLTEXT indexes in V1 (deferred to a later migration per task rule 6).
-- No seed data in V1 (seed lives in V2+).
-- =====================================================================================

-- =====================================================================================
-- Group auth/user (11 tables)
-- =====================================================================================

-- 1. roles ----------------------------------------------------------------------------
CREATE TABLE roles (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code          VARCHAR(30)     NOT NULL,
  name          VARCHAR(50)     NOT NULL,
  description   VARCHAR(255)    NULL,
  is_system     BOOLEAN         NOT NULL DEFAULT TRUE,
  display_order INT             NOT NULL DEFAULT 0,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by    BIGINT UNSIGNED NULL,
  updated_by    BIGINT UNSIGNED NULL,
  deleted_at    DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code),
  KEY idx_roles_is_system (is_system),
  CONSTRAINT ck_roles_code CHECK (code IN ('ROLE_TENANT','ROLE_LANDLORD','ROLE_MODERATOR','ROLE_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. permissions ----------------------------------------------------------------------
CREATE TABLE permissions (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(40)     NOT NULL,
  name        VARCHAR(100)    NOT NULL,
  module      VARCHAR(30)     NOT NULL,
  description VARCHAR(255)    NULL,
  created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by  BIGINT UNSIGNED NULL,
  updated_by  BIGINT UNSIGNED NULL,
  deleted_at  DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permissions_code (code),
  KEY idx_permissions_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. role_permissions -----------------------------------------------------------------
CREATE TABLE role_permissions (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by    BIGINT UNSIGNED NULL,
  updated_by    BIGINT UNSIGNED NULL,
  deleted_at    DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permissions_role_permission (role_id, permission_id),
  KEY idx_role_permissions_permission_id (permission_id),
  CONSTRAINT fk_role_permissions_roles       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT fk_role_permissions_permissions FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. users ----------------------------------------------------------------------------
CREATE TABLE users (
  id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  full_name                VARCHAR(100)    NOT NULL,
  email                    VARCHAR(190)    NOT NULL,
  phone                    VARCHAR(15)     NULL,
  password_hash            VARCHAR(72)     NOT NULL,
  avatar_url               VARCHAR(500)    NULL,
  gender                   VARCHAR(10)     NOT NULL DEFAULT 'UNKNOWN',
  status                   VARCHAR(20)     NOT NULL DEFAULT 'PENDING_VERIFY',
  email_verified_at        DATETIME(6)     NULL,
  phone_verified_at        DATETIME(6)     NULL,
  last_login_at            DATETIME(6)     NULL,
  failed_login_count       INT UNSIGNED    NOT NULL DEFAULT 0,
  locked_until             DATETIME(6)     NULL,
  lock_reason              VARCHAR(500)    NULL,
  locked_by                BIGINT UNSIGNED NULL,
  locked_at                DATETIME(6)     NULL,
  comment_restricted_until DATETIME(6)     NULL,
  contact_restricted_until DATETIME(6)     NULL,
  created_at               DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at               DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by               BIGINT UNSIGNED NULL,
  updated_by               BIGINT UNSIGNED NULL,
  deleted_at               DATETIME(6)     NULL,
  email_uk VARCHAR(190) GENERATED ALWAYS AS (IF(deleted_at IS NULL AND status <> 'DELETED', LOWER(email), NULL)) STORED,
  phone_uk VARCHAR(15)  GENERATED ALWAYS AS (IF(deleted_at IS NULL AND status <> 'DELETED', phone, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email_uk),
  UNIQUE KEY uk_users_phone (phone_uk),
  KEY idx_users_status (status),
  KEY idx_users_full_name (full_name),
  KEY idx_users_created_at (created_at),
  KEY idx_users_email_lookup (email),
  KEY idx_users_phone_lookup (phone),
  CONSTRAINT ck_users_status       CHECK (status IN ('ACTIVE','PENDING_VERIFY','LOCKED','DELETED')),
  CONSTRAINT ck_users_gender       CHECK (gender IN ('MALE','FEMALE','OTHER','UNKNOWN')),
  CONSTRAINT ck_users_failed_login CHECK (failed_login_count >= 0),
  CONSTRAINT ck_users_lock_reason  CHECK (status <> 'LOCKED' OR lock_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. user_roles -----------------------------------------------------------------------
CREATE TABLE user_roles (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  role_id     BIGINT UNSIGNED NOT NULL,
  assigned_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  assigned_by BIGINT UNSIGNED NULL,
  created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by  BIGINT UNSIGNED NULL,
  updated_by  BIGINT UNSIGNED NULL,
  deleted_at  DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_roles_user_role (user_id, role_id),
  KEY idx_user_roles_role_id (role_id),
  CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_user_roles_roles FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. user_profiles --------------------------------------------------------------------
-- FKs to provinces/districts are added at the end of the file (forward references).
CREATE TABLE user_profiles (
  id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                      BIGINT UNSIGNED NOT NULL,
  date_of_birth                DATE            NULL,
  bio                          VARCHAR(500)    NULL,
  occupation                   VARCHAR(100)    NULL,
  address_detail               VARCHAR(255)    NULL,
  preferred_gender_requirement VARCHAR(15)     NULL,
  created_at                   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at                   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by                   BIGINT UNSIGNED NULL,
  updated_by                   BIGINT UNSIGNED NULL,
  deleted_at                   DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_profiles_user_id (user_id),
  CONSTRAINT fk_user_profiles_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_user_profiles_gender_req CHECK (preferred_gender_requirement IS NULL
      OR preferred_gender_requirement IN ('MALE_ONLY','FEMALE_ONLY','ANY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. landlord_profiles ----------------------------------------------------------------
CREATE TABLE landlord_profiles (
  id                          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  user_id                     BIGINT UNSIGNED   NOT NULL,
  display_name                VARCHAR(100)      NULL,
  contact_name                VARCHAR(100)      NOT NULL,
  contact_phone               VARCHAR(15)       NOT NULL,
  contact_email               VARCHAR(190)      NULL,
  company_name                VARCHAR(150)      NULL,
  address                     VARCHAR(255)      NULL,
  allow_chat                  BOOLEAN           NOT NULL DEFAULT TRUE,
  verification_status         VARCHAR(10)       NOT NULL DEFAULT 'PENDING',
  verified_at                 DATETIME(6)       NULL,
  verified_by                 BIGINT UNSIGNED   NULL,
  verification_note           VARCHAR(500)      NULL,
  trust_score                 DECIMAL(5,2)      NOT NULL DEFAULT 100.00,
  response_rate_percent       INT UNSIGNED  NULL,
  avg_response_minutes        INT UNSIGNED      NULL,
  response_conversation_count INT UNSIGNED      NOT NULL DEFAULT 0,
  average_rating              DECIMAL(3,2)      NOT NULL DEFAULT 0.00,
  review_count                INT UNSIGNED      NOT NULL DEFAULT 0,
  total_listings              INT UNSIGNED      NOT NULL DEFAULT 0,
  total_active_listings       INT UNSIGNED      NOT NULL DEFAULT 0,
  valid_report_count          INT UNSIGNED      NOT NULL DEFAULT 0,
  warning_count               INT UNSIGNED      NOT NULL DEFAULT 0,
  locked_listing_count        INT UNSIGNED      NOT NULL DEFAULT 0,
  free_renew_used_this_month  INT UNSIGNED      NOT NULL DEFAULT 0,
  free_renew_reset_at         DATE              NULL,
  posting_restricted_until    DATETIME(6)       NULL,
  restrict_reason             VARCHAR(500)      NULL,
  created_at                  DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at                  DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by                  BIGINT UNSIGNED   NULL,
  updated_by                  BIGINT UNSIGNED   NULL,
  deleted_at                  DATETIME(6)       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_landlord_profiles_user_id (user_id),
  KEY idx_landlord_profiles_verification_status (verification_status),
  KEY idx_landlord_profiles_trust_score (trust_score),
  CONSTRAINT fk_landlord_profiles_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_landlord_profiles_verif  CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED','EXPIRED')),
  CONSTRAINT ck_landlord_profiles_trust  CHECK (trust_score BETWEEN 0 AND 100),
  CONSTRAINT ck_landlord_profiles_rating CHECK (average_rating BETWEEN 0 AND 5),
  CONSTRAINT ck_landlord_profiles_response_rate CHECK (response_rate_percent IS NULL
      OR response_rate_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. verifications --------------------------------------------------------------------
CREATE TABLE verifications (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  type          VARCHAR(10)     NOT NULL,
  status        VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
  target_value  VARCHAR(190)    NULL,
  token_hash    VARCHAR(64)        NULL,
  otp_hash      VARCHAR(64)        NULL,
  attempt_count INT UNSIGNED    NOT NULL DEFAULT 0,
  evidence_url  VARCHAR(500)    NULL,
  expires_at    DATETIME(6)     NOT NULL,
  verified_at   DATETIME(6)     NULL,
  reviewed_by   BIGINT UNSIGNED NULL,
  reviewed_at   DATETIME(6)     NULL,
  reject_reason VARCHAR(500)    NULL,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by    BIGINT UNSIGNED NULL,
  updated_by    BIGINT UNSIGNED NULL,
  deleted_at    DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_verifications_token_hash (token_hash),
  KEY idx_verifications_user_type_status (user_id, type, status),
  KEY idx_verifications_status_expires_at (status, expires_at),
  CONSTRAINT fk_verifications_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_verifications_type    CHECK (type IN ('EMAIL','PHONE','LANDLORD')),
  CONSTRAINT ck_verifications_status  CHECK (status IN ('PENDING','VERIFIED','REJECTED','EXPIRED')),
  CONSTRAINT ck_verifications_attempt CHECK (attempt_count >= 0),
  CONSTRAINT ck_verifications_reject  CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. refresh_tokens -------------------------------------------------------------------
CREATE TABLE refresh_tokens (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  token_hash     VARCHAR(64)        NOT NULL,
  family_id      VARCHAR(36)        NOT NULL,
  parent_id      BIGINT UNSIGNED NULL,
  issued_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at     DATETIME(6)     NOT NULL,
  used_at        DATETIME(6)     NULL,
  revoked_at     DATETIME(6)     NULL,
  revoked_reason VARCHAR(50)     NULL,
  user_agent     VARCHAR(255)    NULL,
  ip_address     VARCHAR(45)     NULL,
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by     BIGINT UNSIGNED NULL,
  updated_by     BIGINT UNSIGNED NULL,
  deleted_at     DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_tokens_user_id (user_id),
  KEY idx_refresh_tokens_family_id (family_id),
  KEY idx_refresh_tokens_expires_at (expires_at),
  CONSTRAINT fk_refresh_tokens_users  FOREIGN KEY (user_id)   REFERENCES users (id)          ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_refresh_tokens_parent FOREIGN KEY (parent_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. password_reset_tokens -----------------------------------------------------------
CREATE TABLE password_reset_tokens (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64)        NOT NULL,
  expires_at DATETIME(6)     NOT NULL,
  used_at    DATETIME(6)     NULL,
  ip_address VARCHAR(45)     NULL,
  created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT UNSIGNED NULL,
  updated_by BIGINT UNSIGNED NULL,
  deleted_at DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_tokens_token_hash (token_hash),
  KEY idx_password_reset_tokens_user_id (user_id),
  KEY idx_password_reset_tokens_expires_at (expires_at),
  CONSTRAINT fk_password_reset_tokens_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. follows -------------------------------------------------------------------------
CREATE TABLE follows (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  follower_id        BIGINT UNSIGNED NOT NULL,
  landlord_id        BIGINT UNSIGNED NOT NULL,
  notify_new_listing BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by         BIGINT UNSIGNED NULL,
  updated_by         BIGINT UNSIGNED NULL,
  deleted_at         DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follows_follower_landlord (follower_id, landlord_id),
  KEY idx_follows_landlord_id (landlord_id),
  CONSTRAINT fk_follows_users_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT fk_follows_users_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT
  -- Bỏ CHECK ck_follows_not_self: follower_id/landlord_id là FK ON DELETE CASCADE nên MySQL cấm
  -- dùng trong CHECK (lỗi 3823). "Không tự theo dõi chính mình" được ép ở FollowService.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group catalog (5 tables) — lookup tables: only id, created_at, updated_at (+ is_active)
-- =====================================================================================

-- 12. categories ----------------------------------------------------------------------
CREATE TABLE categories (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20)     NOT NULL,
  name            VARCHAR(50)     NOT NULL,
  slug            VARCHAR(60)     NOT NULL,
  description     VARCHAR(255)    NULL,
  icon            VARCHAR(50)     NULL,
  required_fields JSON            NOT NULL,
  optional_fields JSON            NOT NULL,
  display_order   INT             NOT NULL DEFAULT 0,
  is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
  listing_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_code (code),
  UNIQUE KEY uk_categories_slug (slug),
  KEY idx_categories_is_active_display_order (is_active, display_order),
  CONSTRAINT ck_categories_code CHECK (code IN
      ('BOARDING_HOUSE','MINI_APARTMENT','APARTMENT','WHOLE_HOUSE','HOMESTAY','ROOMMATE','SMALL_PREMISES'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. provinces -----------------------------------------------------------------------
CREATE TABLE provinces (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code          VARCHAR(10)     NOT NULL,
  name          VARCHAR(100)    NOT NULL,
  short_name    VARCHAR(50)     NOT NULL,
  type          VARCHAR(20)     NOT NULL,
  slug          VARCHAR(120)    NOT NULL,
  search_name   VARCHAR(100)    NOT NULL,
  latitude      DECIMAL(10,7)   NULL,
  longitude     DECIMAL(10,7)   NULL,
  display_order INT             NOT NULL DEFAULT 0,
  is_active     BOOLEAN         NOT NULL DEFAULT TRUE,
  listing_count INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provinces_code (code),
  UNIQUE KEY uk_provinces_slug (slug),
  KEY idx_provinces_is_active_display_order (is_active, display_order),
  KEY idx_provinces_search_name (search_name),
  CONSTRAINT ck_provinces_type CHECK (type IN ('THANH_PHO_TRUNG_UONG','TINH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. districts -----------------------------------------------------------------------
CREATE TABLE districts (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  province_id   BIGINT UNSIGNED NOT NULL,
  code          VARCHAR(10)     NOT NULL,
  name          VARCHAR(100)    NOT NULL,
  type          VARCHAR(20)     NOT NULL,
  slug          VARCHAR(120)    NOT NULL,
  search_name   VARCHAR(100)    NOT NULL,
  latitude      DECIMAL(10,7)   NULL,
  longitude     DECIMAL(10,7)   NULL,
  display_order INT             NOT NULL DEFAULT 0,
  is_active     BOOLEAN         NOT NULL DEFAULT TRUE,
  listing_count INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_districts_code (code),
  UNIQUE KEY uk_districts_slug (slug),
  KEY idx_districts_province_id_is_active (province_id, is_active),
  KEY idx_districts_search_name (search_name),
  CONSTRAINT fk_districts_provinces FOREIGN KEY (province_id) REFERENCES provinces (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_districts_type CHECK (type IN ('QUAN','HUYEN','THI_XA','THANH_PHO_THUOC_TINH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. wards ---------------------------------------------------------------------------
CREATE TABLE wards (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  district_id   BIGINT UNSIGNED NOT NULL,
  code          VARCHAR(10)     NOT NULL,
  name          VARCHAR(100)    NOT NULL,
  type          VARCHAR(20)     NOT NULL,
  slug          VARCHAR(160)    NOT NULL,
  search_name   VARCHAR(100)    NOT NULL,
  latitude      DECIMAL(10,7)   NULL,
  longitude     DECIMAL(10,7)   NULL,
  is_active     BOOLEAN         NOT NULL DEFAULT TRUE,
  listing_count INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_wards_code (code),
  UNIQUE KEY uk_wards_slug (slug),
  KEY idx_wards_district_id_is_active (district_id, is_active),
  KEY idx_wards_search_name (search_name),
  CONSTRAINT fk_wards_districts FOREIGN KEY (district_id) REFERENCES districts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_wards_type CHECK (type IN ('PHUONG','XA','THI_TRAN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. amenities -----------------------------------------------------------------------
CREATE TABLE amenities (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code               VARCHAR(30)     NOT NULL,
  name               VARCHAR(60)     NOT NULL,
  group_code         VARCHAR(15)     NOT NULL,
  icon               VARCHAR(50)     NULL,
  is_filterable      BOOLEAN         NOT NULL DEFAULT TRUE,
  price_impact_ratio DECIMAL(5,4)    NOT NULL DEFAULT 0.0000,
  display_order      INT             NOT NULL DEFAULT 0,
  is_active          BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_amenities_code (code),
  KEY idx_amenities_group_display_order (group_code, is_active, display_order),
  CONSTRAINT ck_amenities_group        CHECK (group_code IN ('FURNITURE','SECURITY','UTILITY','TRANSPORT')),
  CONSTRAINT ck_amenities_price_impact CHECK (price_impact_ratio BETWEEN -1 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group listing (4 tables)
-- =====================================================================================

-- 17. listings ------------------------------------------------------------------------
-- No FULLTEXT in V1 (task rule 6). fk_listings_prediction_histories added at end of file.
CREATE TABLE listings (
  id                     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  owner_id               BIGINT UNSIGNED  NOT NULL,
  category_id            BIGINT UNSIGNED  NOT NULL,
  title                  VARCHAR(150)     NOT NULL,
  slug                   VARCHAR(180)     NOT NULL,
  description            TEXT             NOT NULL,
  price                  DECIMAL(15,2)    NOT NULL,
  area                   DECIMAL(8,2)     NOT NULL,
  deposit_amount         DECIMAL(15,2)    NULL,
  electricity_price      DECIMAL(15,2)    NULL,
  water_price            DECIMAL(15,2)    NULL,
  ward_id                BIGINT UNSIGNED  NOT NULL,
  address_detail         VARCHAR(255)     NOT NULL,
  latitude               DECIMAL(10,7)    NULL,
  longitude              DECIMAL(10,7)    NULL,
  room_count             INT UNSIGNED NULL,
  toilet_count           INT UNSIGNED NULL,
  toilet_type            VARCHAR(10)      NULL,
  max_occupants          INT UNSIGNED NULL,
  current_occupants      INT UNSIGNED NULL,
  gender_requirement     VARCHAR(15)      NOT NULL DEFAULT 'ANY',
  pet_allowed            BOOLEAN          NOT NULL DEFAULT FALSE,
  parking_available      BOOLEAN          NOT NULL DEFAULT FALSE,
  curfew_type            VARCHAR(10)      NOT NULL DEFAULT 'UNKNOWN',
  furniture_status       VARCHAR(10)      NOT NULL DEFAULT 'NONE',
  available_from         DATE             NULL,
  status                 VARCHAR(20)      NOT NULL DEFAULT 'DRAFT',
  reject_reason          VARCHAR(500)     NULL,
  lock_reason            VARCHAR(500)     NULL,
  lock_severity          VARCHAR(10)      NULL,
  auto_hidden_at         DATETIME(6)      NULL,
  auto_hide_reason       VARCHAR(500)     NULL,
  trust_score            DECIMAL(5,2)     NOT NULL DEFAULT 100.00,
  average_rating         DECIMAL(3,2)     NOT NULL DEFAULT 0.00,
  review_count           INT UNSIGNED     NOT NULL DEFAULT 0,
  view_count             INT UNSIGNED     NOT NULL DEFAULT 0,
  favorite_count         INT UNSIGNED     NOT NULL DEFAULT 0,
  contact_count          INT UNSIGNED     NOT NULL DEFAULT 0,
  comment_count          INT UNSIGNED     NOT NULL DEFAULT 0,
  negative_comment_count INT UNSIGNED     NOT NULL DEFAULT 0,
  positive_comment_count INT UNSIGNED     NOT NULL DEFAULT 0,
  need_review_count      INT UNSIGNED     NOT NULL DEFAULT 0,
  last_need_review_at    DATETIME(6)      NULL,
  price_prediction_id    BIGINT UNSIGNED  NULL,
  price_deviation_flag   BOOLEAN          NOT NULL DEFAULT FALSE,
  is_promoted            BOOLEAN          NOT NULL DEFAULT FALSE,
  promoted_until         DATETIME(6)      NULL,
  promotion_priority     INT UNSIGNED     NOT NULL DEFAULT 0,
  published_at           DATETIME(6)      NULL,
  expired_at             DATETIME(6)      NULL,
  expiry_reminder_sent_at DATETIME(6)     NULL,
  renew_count            INT UNSIGNED     NOT NULL DEFAULT 0,
  floor_count            INT UNSIGNED NULL,
  created_at             DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at             DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by             BIGINT UNSIGNED  NULL,
  updated_by             BIGINT UNSIGNED  NULL,
  deleted_at             DATETIME(6)      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_listings_slug (slug),
  KEY idx_listings_search (status, ward_id, category_id, price, area),
  KEY idx_listings_promoted_sort (status, is_promoted, promotion_priority, published_at),
  KEY idx_listings_status_published_at (status, published_at),
  KEY idx_listings_status_price (status, price),
  KEY idx_listings_status_view_count (status, view_count),
  KEY idx_listings_status_expired_at (status, expired_at),
  KEY idx_listings_owner_id_status (owner_id, status),
  KEY idx_listings_ward_category_area (ward_id, category_id, area, status),
  KEY idx_listings_status_trust_score (status, trust_score),
  KEY idx_listings_auto_hidden_at (auto_hidden_at),
  KEY idx_listings_price_deviation_flag (price_deviation_flag, status),
  KEY idx_listings_promoted_until (is_promoted, promoted_until),
  KEY idx_listings_deleted_at (deleted_at),
  KEY idx_listings_category_id (category_id),
  KEY idx_listings_price_prediction_id (price_prediction_id),
  CONSTRAINT fk_listings_users      FOREIGN KEY (owner_id)    REFERENCES users (id)      ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_listings_categories FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_listings_wards      FOREIGN KEY (ward_id)     REFERENCES wards (id)       ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_listings_status CHECK (status IN
      ('DRAFT','PENDING','ACTIVE','REJECTED','HIDDEN','EXPIRED','CLOSED','LOCKED','NEED_REVIEW','DELETED')),
  CONSTRAINT ck_listings_gender_req  CHECK (gender_requirement IN ('MALE_ONLY','FEMALE_ONLY','ANY')),
  CONSTRAINT ck_listings_curfew      CHECK (curfew_type IN ('FREE','CURFEW','UNKNOWN')),
  CONSTRAINT ck_listings_furniture   CHECK (furniture_status IN ('NONE','BASIC','FULL')),
  CONSTRAINT ck_listings_toilet_type CHECK (toilet_type IS NULL OR toilet_type IN ('PRIVATE','SHARED')),
  CONSTRAINT ck_listings_lock_severity CHECK (lock_severity IS NULL OR lock_severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_listings_price_positive CHECK (price > 0),
  CONSTRAINT ck_listings_area_positive  CHECK (area > 0),
  CONSTRAINT ck_listings_deposit        CHECK (deposit_amount IS NULL OR deposit_amount >= 0),
  CONSTRAINT ck_listings_electricity    CHECK (electricity_price IS NULL OR electricity_price >= 0),
  CONSTRAINT ck_listings_water          CHECK (water_price IS NULL OR water_price >= 0),
  CONSTRAINT ck_listings_latitude       CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_listings_longitude      CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_listings_trust_score    CHECK (trust_score BETWEEN 0 AND 100),
  CONSTRAINT ck_listings_average_rating CHECK (average_rating BETWEEN 0 AND 5),
  CONSTRAINT ck_listings_occupants      CHECK (current_occupants IS NULL OR max_occupants IS NULL
                                               OR current_occupants <= max_occupants),
  CONSTRAINT ck_listings_promotion_priority CHECK (promotion_priority BETWEEN 0 AND 100),
  CONSTRAINT ck_listings_reject_reason  CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
  CONSTRAINT ck_listings_lock_reason    CHECK (status <> 'LOCKED'
                                               OR (lock_reason IS NOT NULL AND lock_severity IS NOT NULL)),
  CONSTRAINT ck_listings_auto_hide_reason CHECK ((auto_hidden_at IS NULL) = (auto_hide_reason IS NULL)),
  CONSTRAINT ck_listings_counters_non_negative CHECK (
      view_count >= 0 AND favorite_count >= 0 AND contact_count >= 0 AND comment_count >= 0
      AND positive_comment_count >= 0 AND negative_comment_count >= 0
      AND review_count >= 0 AND need_review_count >= 0 AND renew_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. listing_images ------------------------------------------------------------------
CREATE TABLE listing_images (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id    BIGINT UNSIGNED NOT NULL,
  url           VARCHAR(500)    NOT NULL,
  thumbnail_url VARCHAR(500)    NULL,
  is_primary    BOOLEAN         NOT NULL DEFAULT FALSE,
  display_order INT             NOT NULL DEFAULT 0,
  original_name VARCHAR(255)    NULL,
  file_size     INT UNSIGNED    NOT NULL,
  content_type  VARCHAR(30)     NOT NULL,
  width         INT UNSIGNED    NULL,
  height        INT UNSIGNED    NULL,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by    BIGINT UNSIGNED NULL,
  updated_by    BIGINT UNSIGNED NULL,
  deleted_at    DATETIME(6)     NULL,
  PRIMARY KEY (id),
  KEY idx_listing_images_listing_id_display_order (listing_id, display_order),
  KEY idx_listing_images_listing_id_is_primary (listing_id, is_primary),
  CONSTRAINT fk_listing_images_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_listing_images_size CHECK (file_size > 0 AND file_size <= 5242880),
  CONSTRAINT ck_listing_images_content_type CHECK (content_type IN ('image/jpeg','image/png','image/webp'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. listing_amenities ---------------------------------------------------------------
CREATE TABLE listing_amenities (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id BIGINT UNSIGNED NOT NULL,
  amenity_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT UNSIGNED NULL,
  updated_by BIGINT UNSIGNED NULL,
  deleted_at DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_listing_amenities_listing_amenity (listing_id, amenity_id),
  KEY idx_listing_amenities_amenity_id (amenity_id),
  CONSTRAINT fk_listing_amenities_listings  FOREIGN KEY (listing_id) REFERENCES listings (id)  ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_listing_amenities_amenities FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. listing_edit_histories (append-only: id + created_at only) ----------------------
CREATE TABLE listing_edit_histories (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id          BIGINT UNSIGNED NOT NULL,
  editor_id           BIGINT UNSIGNED NULL,
  field_name          VARCHAR(50)     NOT NULL,
  old_value           TEXT            NULL,
  new_value           TEXT            NULL,
  is_sensitive_change BOOLEAN         NOT NULL DEFAULT FALSE,
  status_before       VARCHAR(20)     NULL,
  status_after        VARCHAR(20)     NULL,
  edit_batch_id       VARCHAR(36)        NOT NULL,
  created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_listing_edit_histories_listing_id_created_at (listing_id, created_at),
  KEY idx_listing_edit_histories_batch (edit_batch_id),
  KEY idx_listing_edit_histories_editor_id (editor_id),
  CONSTRAINT fk_listing_edit_histories_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_listing_edit_histories_users    FOREIGN KEY (editor_id)  REFERENCES users (id)    ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT ck_listing_edit_histories_changed CHECK (old_value IS NOT NULL OR new_value IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group interaction (8 tables)
-- =====================================================================================

-- 21. favorites -----------------------------------------------------------------------
CREATE TABLE favorites (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  listing_id BIGINT UNSIGNED NOT NULL,
  note       VARCHAR(255)    NULL,
  created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT UNSIGNED NULL,
  updated_by BIGINT UNSIGNED NULL,
  deleted_at DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorites_user_listing (user_id, listing_id),
  KEY idx_favorites_user_id_created_at (user_id, created_at),
  KEY idx_favorites_listing_id (listing_id),
  CONSTRAINT fk_favorites_users    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT fk_favorites_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 22. view_histories (append-only: id + created_at) -----------------------------------
CREATE TABLE view_histories (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NULL,
  session_id VARCHAR(36)        NULL,
  ip_address VARCHAR(45)     NULL,
  user_agent VARCHAR(255)    NULL,
  referrer   VARCHAR(500)    NULL,
  is_counted BOOLEAN         NOT NULL DEFAULT TRUE,
  viewed_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_view_histories_user_id_viewed_at (user_id, viewed_at),
  KEY idx_view_histories_listing_id_viewed_at (listing_id, viewed_at),
  KEY idx_view_histories_dedup (listing_id, user_id, viewed_at),
  KEY idx_view_histories_dedup_anon (listing_id, ip_address, viewed_at),
  CONSTRAINT fk_view_histories_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT fk_view_histories_users    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 23. search_histories (append-only: id + created_at) ---------------------------------
CREATE TABLE search_histories (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NULL,
  keyword      VARCHAR(150)    NULL,
  criteria     JSON            NOT NULL,
  result_count INT UNSIGNED    NOT NULL DEFAULT 0,
  session_id   VARCHAR(36)        NULL,
  ip_address   VARCHAR(45)     NULL,
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_search_histories_user_id_created_at (user_id, created_at),
  KEY idx_search_histories_keyword (keyword),
  KEY idx_search_histories_result_count (result_count),
  CONSTRAINT fk_search_histories_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_search_histories_result_count CHECK (result_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 24. contact_logs --------------------------------------------------------------------
CREATE TABLE contact_logs (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id       BIGINT UNSIGNED NOT NULL,
  user_id          BIGINT UNSIGNED NOT NULL,
  contact_type     VARCHAR(15)     NOT NULL,
  message          VARCHAR(1000)   NULL,
  contact_name     VARCHAR(100)    NULL,
  contact_phone    VARCHAR(15)     NULL,
  is_counted       BOOLEAN         NOT NULL DEFAULT TRUE,
  is_read_by_owner BOOLEAN         NOT NULL DEFAULT FALSE,
  ip_address       VARCHAR(45)     NULL,
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by       BIGINT UNSIGNED NULL,
  updated_by       BIGINT UNSIGNED NULL,
  deleted_at       DATETIME(6)     NULL,
  PRIMARY KEY (id),
  KEY idx_contact_logs_listing_id_created_at (listing_id, created_at),
  KEY idx_contact_logs_dedup (listing_id, user_id, created_at),
  KEY idx_contact_logs_user_id_created_at (user_id, created_at),
  CONSTRAINT fk_contact_logs_listings    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_contact_logs_users       FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_contact_logs_type     CHECK (contact_type IN ('VIEW_PHONE','FORM','CHAT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 25. conversations -------------------------------------------------------------------
CREATE TABLE conversations (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id            BIGINT UNSIGNED NOT NULL,
  tenant_id             BIGINT UNSIGNED NOT NULL,
  status                VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
  first_response_at     DATETIME(6)     NULL,
  last_message_at       DATETIME(6)     NULL,
  last_message_preview  VARCHAR(200)    NULL,
  tenant_unread_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  landlord_unread_count INT UNSIGNED    NOT NULL DEFAULT 0,
  message_count         INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by            BIGINT UNSIGNED NULL,
  updated_by            BIGINT UNSIGNED NULL,
  deleted_at            DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conversations_listing_tenant (listing_id, tenant_id),
  KEY idx_conversations_tenant_id_last_message_at (tenant_id, last_message_at),
  KEY idx_conversations_listing_id (listing_id),
  KEY idx_conversations_listing_id_last_message_at (listing_id, last_message_at),
  CONSTRAINT fk_conversations_listings        FOREIGN KEY (listing_id)  REFERENCES listings (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_conversations_users_tenant    FOREIGN KEY (tenant_id)   REFERENCES users (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_conversations_status    CHECK (status IN ('ACTIVE','ARCHIVED','BLOCKED')),
  CONSTRAINT ck_conversations_unread    CHECK (tenant_unread_count >= 0 AND landlord_unread_count >= 0),
  CONSTRAINT ck_conversations_first_response CHECK (first_response_at IS NULL OR first_response_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 26. messages ------------------------------------------------------------------------
CREATE TABLE messages (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT UNSIGNED NOT NULL,
  sender_id       BIGINT UNSIGNED NOT NULL,
  content         VARCHAR(2000)   NOT NULL,
  is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
  read_at         DATETIME(6)     NULL,
  created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by      BIGINT UNSIGNED NULL,
  updated_by      BIGINT UNSIGNED NULL,
  deleted_at      DATETIME(6)     NULL,
  PRIMARY KEY (id),
  KEY idx_messages_conversation_id_created_at (conversation_id, created_at),
  KEY idx_messages_sender_id (sender_id),
  CONSTRAINT fk_messages_conversations FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_messages_users         FOREIGN KEY (sender_id)       REFERENCES users (id)         ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_messages_read CHECK (is_read = FALSE OR read_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 27. comments ------------------------------------------------------------------------
CREATE TABLE comments (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id              BIGINT UNSIGNED NOT NULL,
  user_id                 BIGINT UNSIGNED NOT NULL,
  parent_id               BIGINT UNSIGNED NULL,
  content                 VARCHAR(1000)   NOT NULL,
  status                  VARCHAR(10)     NOT NULL DEFAULT 'VISIBLE',
  sentiment_label         VARCHAR(20)     NOT NULL DEFAULT 'PENDING_ANALYSIS',
  sentiment_score         DECIMAL(4,3)    NULL,
  sentiment_confidence    DECIMAL(4,3)    NULL,
  sentiment_weight        DECIMAL(3,2)    NOT NULL DEFAULT 1.00,
  is_risk_comment         BOOLEAN         NOT NULL DEFAULT FALSE,
  is_spam                 BOOLEAN         NOT NULL DEFAULT FALSE,
  is_owner_reply          BOOLEAN         NOT NULL DEFAULT FALSE,
  reply_count             INT UNSIGNED    NOT NULL DEFAULT 0,
  contains_banned_keyword BOOLEAN         NOT NULL DEFAULT FALSE,
  hidden_reason           VARCHAR(500)    NULL,
  hidden_by               BIGINT UNSIGNED NULL,
  hidden_at               DATETIME(6)     NULL,
  edited_at               DATETIME(6)     NULL,
  created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by              BIGINT UNSIGNED NULL,
  updated_by              BIGINT UNSIGNED NULL,
  deleted_at              DATETIME(6)     NULL,
  PRIMARY KEY (id),
  KEY idx_comments_listing_id_status_created_at (listing_id, status, created_at),
  KEY idx_comments_parent_id (parent_id),
  KEY idx_comments_user_id_created_at (user_id, created_at),
  KEY idx_comments_sentiment_label_status (sentiment_label, status),
  KEY idx_comments_listing_id_sentiment (listing_id, sentiment_label, is_spam),
  KEY idx_comments_is_risk_comment (is_risk_comment, status),
  CONSTRAINT fk_comments_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_comments_users    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_comments_parent   FOREIGN KEY (parent_id)  REFERENCES comments (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT ck_comments_status CHECK (status IN ('VISIBLE','PENDING','HIDDEN','DELETED')),
  CONSTRAINT ck_comments_sentiment_label CHECK (sentiment_label IN
      ('POSITIVE','NEUTRAL','NEGATIVE','MIXED','PENDING_ANALYSIS')),
  CONSTRAINT ck_comments_sentiment_score CHECK (sentiment_score IS NULL OR sentiment_score BETWEEN -1 AND 1),
  CONSTRAINT ck_comments_confidence CHECK (sentiment_confidence IS NULL OR sentiment_confidence BETWEEN 0 AND 1),
  CONSTRAINT ck_comments_weight CHECK (sentiment_weight BETWEEN 0 AND 1),
  CONSTRAINT ck_comments_content_length CHECK (CHAR_LENGTH(content) BETWEEN 3 AND 1000),
  CONSTRAINT ck_comments_hidden_reason CHECK (status <> 'HIDDEN' OR hidden_reason IS NOT NULL)
  -- Không đặt CHECK (parent_id <> id): MySQL cấm CHECK tham chiếu cột AUTO_INCREMENT (lỗi 3818).
  -- Ràng buộc "bình luận không tự làm cha của chính nó" được ép ở tầng application (CommentService).
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 28. reviews -------------------------------------------------------------------------
CREATE TABLE reviews (
  id                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  listing_id          BIGINT UNSIGNED  NOT NULL,
  user_id             BIGINT UNSIGNED  NOT NULL,
  rating              INT UNSIGNED NOT NULL,
  content             VARCHAR(1000)    NULL,
  status              VARCHAR(10)      NOT NULL DEFAULT 'VISIBLE',
  is_verified_contact BOOLEAN          NOT NULL DEFAULT FALSE,
  hidden_reason       VARCHAR(500)     NULL,
  hidden_by           BIGINT UNSIGNED  NULL,
  hidden_at           DATETIME(6)      NULL,
  edited_at           DATETIME(6)      NULL,
  created_at          DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at          DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by          BIGINT UNSIGNED  NULL,
  updated_by          BIGINT UNSIGNED  NULL,
  deleted_at          DATETIME(6)      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reviews_user_listing (user_id, listing_id),
  KEY idx_reviews_listing_id_status_created_at (listing_id, status, created_at),
  KEY idx_reviews_rating_status (rating, status),
  KEY idx_reviews_user_id (user_id),
  CONSTRAINT fk_reviews_listings       FOREIGN KEY (listing_id)  REFERENCES listings (id) ON DELETE CASCADE  ON UPDATE RESTRICT,
  CONSTRAINT fk_reviews_users          FOREIGN KEY (user_id)     REFERENCES users (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
  CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT ck_reviews_content_required CHECK (rating > 2 OR (content IS NOT NULL AND CHAR_LENGTH(content) >= 3)),
  CONSTRAINT ck_reviews_hidden_reason CHECK (status <> 'HIDDEN' OR hidden_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group moderation (4 tables)
-- =====================================================================================

-- 29. reports -------------------------------------------------------------------------
CREATE TABLE reports (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reporter_id        BIGINT UNSIGNED NOT NULL,
  target_type        VARCHAR(10)     NOT NULL,
  target_id          BIGINT UNSIGNED NOT NULL,
  listing_id         BIGINT UNSIGNED NULL,
  reason             VARCHAR(20)     NOT NULL,
  description        VARCHAR(1000)   NULL,
  evidence_image_url VARCHAR(500)    NULL,
  status             VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
  severity           VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
  is_valid           BOOLEAN         NULL,
  resolution_note    VARCHAR(1000)   NULL,
  resolved_by        BIGINT UNSIGNED NULL,
  resolved_at        DATETIME(6)     NULL,
  dedup_key          VARCHAR(80)     NOT NULL,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by         BIGINT UNSIGNED NULL,
  updated_by         BIGINT UNSIGNED NULL,
  deleted_at         DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reports_reporter_dedup (reporter_id, dedup_key),
  KEY idx_reports_status_severity_created_at (status, severity, created_at),
  KEY idx_reports_target (target_type, target_id, created_at),
  KEY idx_reports_listing_id_created_at (listing_id, created_at),
  KEY idx_reports_reporter_id_created_at (reporter_id, created_at),
  KEY idx_reports_is_valid (is_valid, reporter_id),
  KEY idx_reports_resolved_by (resolved_by),
  CONSTRAINT fk_reports_users_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_reports_listings       FOREIGN KEY (listing_id)  REFERENCES listings (id) ON DELETE SET NULL  ON UPDATE RESTRICT,
  CONSTRAINT fk_reports_users_resolver FOREIGN KEY (resolved_by) REFERENCES users (id)    ON DELETE SET NULL  ON UPDATE RESTRICT,
  CONSTRAINT ck_reports_target_type CHECK (target_type IN ('LISTING','COMMENT','USER','REVIEW')),
  CONSTRAINT ck_reports_reason CHECK (reason IN
      ('WRONG_INFO','ALREADY_RENTED','SCAM','FAKE_IMAGE','WRONG_PRICE','OFFENSIVE','SPAM','OTHER')),
  CONSTRAINT ck_reports_status CHECK (status IN ('PENDING','REVIEWING','RESOLVED','REJECTED')),
  CONSTRAINT ck_reports_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_reports_description_other CHECK (reason <> 'OTHER' OR description IS NOT NULL)
  -- Bỏ CHECK ck_reports_resolved: MySQL cấm cột resolved_by (FK ON DELETE SET NULL) dùng trong
  -- CHECK (lỗi 3823). Ràng buộc "khi RESOLVED/REJECTED phải có resolved_at/resolved_by/is_valid"
  -- được ép ở ReportService khi xử lý báo cáo.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 30. moderation_actions (append-only: id + created_at) -------------------------------
CREATE TABLE moderation_actions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  moderator_id    BIGINT UNSIGNED NULL,
  is_system       BOOLEAN         NOT NULL DEFAULT FALSE,
  report_id       BIGINT UNSIGNED NULL,
  target_type     VARCHAR(10)     NOT NULL,
  target_id       BIGINT UNSIGNED NOT NULL,
  listing_id      BIGINT UNSIGNED NULL,
  action_type     VARCHAR(20)     NOT NULL,
  result          VARCHAR(15)     NULL,
  reason          VARCHAR(500)    NOT NULL,
  note            VARCHAR(1000)   NULL,
  previous_status VARCHAR(20)     NULL,
  new_status      VARCHAR(20)     NULL,
  created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_moderation_actions_listing_id_action_created (listing_id, action_type, created_at),
  KEY idx_moderation_actions_report_id (report_id),
  KEY idx_moderation_actions_moderator_id_created_at (moderator_id, created_at),
  KEY idx_moderation_actions_target (target_type, target_id, created_at),
  CONSTRAINT fk_moderation_actions_users    FOREIGN KEY (moderator_id) REFERENCES users (id)    ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_moderation_actions_reports  FOREIGN KEY (report_id)    REFERENCES reports (id)  ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_moderation_actions_listings FOREIGN KEY (listing_id)   REFERENCES listings (id) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT ck_moderation_actions_type CHECK (action_type IN
      ('APPROVE','REJECT','HIDE','UNHIDE','LOCK','UNLOCK','WARN','REQUEST_EDIT','FLAG_NEED_REVIEW','DISMISS')),
  CONSTRAINT ck_moderation_actions_result CHECK (result IS NULL OR result IN
      ('NO_VIOLATION','MINOR_WARN','MEDIUM_HIDE','SEVERE_LOCK')),
  CONSTRAINT ck_moderation_actions_target_type CHECK (target_type IN ('LISTING','COMMENT','USER','REVIEW'))
  -- Bỏ CHECK ck_moderation_actions_system: moderator_id là FK ON DELETE SET NULL nên MySQL cấm
  -- dùng trong CHECK (lỗi 3823). "Hành động không phải hệ thống thì phải có moderator" ép ở service.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 31. violation_warnings (append-only: id + created_at) -------------------------------
CREATE TABLE violation_warnings (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id              BIGINT UNSIGNED NOT NULL,
  listing_id           BIGINT UNSIGNED NULL,
  report_id            BIGINT UNSIGNED NULL,
  moderation_action_id BIGINT UNSIGNED NULL,
  severity             VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
  reason               VARCHAR(200)    NOT NULL,
  content              VARCHAR(1000)   NOT NULL,
  issued_by            BIGINT UNSIGNED NULL,
  is_system            BOOLEAN         NOT NULL DEFAULT FALSE,
  acknowledged_at      DATETIME(6)     NULL,
  created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_violation_warnings_user_id_created_at (user_id, created_at),
  KEY idx_violation_warnings_listing_id (listing_id),
  KEY idx_violation_warnings_report_id (report_id),
  KEY idx_violation_warnings_issued_by (issued_by),
  KEY idx_violation_warnings_moderation_action_id (moderation_action_id),
  CONSTRAINT fk_violation_warnings_users              FOREIGN KEY (user_id)              REFERENCES users (id)              ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_violation_warnings_listings           FOREIGN KEY (listing_id)           REFERENCES listings (id)           ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_violation_warnings_reports            FOREIGN KEY (report_id)            REFERENCES reports (id)            ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_violation_warnings_moderation_actions FOREIGN KEY (moderation_action_id) REFERENCES moderation_actions (id) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_violation_warnings_users_issuer       FOREIGN KEY (issued_by)            REFERENCES users (id)              ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT ck_violation_warnings_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
  -- Bỏ CHECK ck_violation_warnings_system: issued_by là FK ON DELETE SET NULL nên MySQL cấm dùng
  -- trong CHECK (lỗi 3823). "Cảnh báo không phải hệ thống thì phải có người ban hành" ép ở service.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 32. banned_keywords -----------------------------------------------------------------
CREATE TABLE banned_keywords (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  keyword            VARCHAR(100)    NOT NULL,
  normalized_keyword VARCHAR(100)    NOT NULL,
  severity           VARCHAR(10)     NOT NULL DEFAULT 'MILD',
  applies_to         VARCHAR(10)     NOT NULL DEFAULT 'BOTH',
  is_regex           BOOLEAN         NOT NULL DEFAULT FALSE,
  category           VARCHAR(30)     NULL,
  note               VARCHAR(255)    NULL,
  is_active          BOOLEAN         NOT NULL DEFAULT TRUE,
  hit_count          INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by         BIGINT UNSIGNED NULL,
  updated_by         BIGINT UNSIGNED NULL,
  deleted_at         DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_banned_keywords_normalized_keyword (normalized_keyword),
  KEY idx_banned_keywords_active_scope (is_active, applies_to),
  KEY idx_banned_keywords_severity (severity),
  CONSTRAINT ck_banned_keywords_severity CHECK (severity IN ('MILD','SEVERE')),
  CONSTRAINT ck_banned_keywords_scope    CHECK (applies_to IN ('LISTING','COMMENT','BOTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group payment (4 tables) — coupons created BEFORE payments (payments references it)
-- =====================================================================================

-- 33. promotion_packages --------------------------------------------------------------
CREATE TABLE promotion_packages (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code           VARCHAR(30)     NOT NULL,
  name           VARCHAR(100)    NOT NULL,
  description    VARCHAR(500)    NULL,
  price          DECIMAL(15,2)   NOT NULL,
  duration_days  INT UNSIGNED    NOT NULL,
  priority       INT UNSIGNED    NOT NULL DEFAULT 0,
  badge_label    VARCHAR(30)     NULL,
  badge_color    VARCHAR(20)     NULL,
  is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
  display_order  INT             NOT NULL DEFAULT 0,
  purchase_count INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by     BIGINT UNSIGNED NULL,
  updated_by     BIGINT UNSIGNED NULL,
  deleted_at     DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_packages_code (code),
  KEY idx_promotion_packages_is_active_display_order (is_active, display_order),
  KEY idx_promotion_packages_priority (priority),
  CONSTRAINT ck_promotion_packages_price          CHECK (price >= 0),
  CONSTRAINT ck_promotion_packages_duration       CHECK (duration_days > 0),
  CONSTRAINT ck_promotion_packages_priority       CHECK (priority BETWEEN 0 AND 100),
  CONSTRAINT ck_promotion_packages_purchase_count CHECK (purchase_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 34. coupons (moved before payments; see report — resolves forward-FK from payments) --
CREATE TABLE coupons (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code                VARCHAR(30)     NOT NULL,
  description         VARCHAR(255)    NULL,
  discount_type       VARCHAR(10)     NOT NULL,
  discount_value      DECIMAL(15,2)   NOT NULL,
  max_discount_amount DECIMAL(15,2)   NULL,
  min_order_amount    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
  usage_limit         INT UNSIGNED    NULL,
  used_count          INT UNSIGNED    NOT NULL DEFAULT 0,
  per_user_limit      INT UNSIGNED    NOT NULL DEFAULT 1,
  start_at            DATETIME(6)     NOT NULL,
  end_at              DATETIME(6)     NOT NULL,
  is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by          BIGINT UNSIGNED NULL,
  updated_by          BIGINT UNSIGNED NULL,
  deleted_at          DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupons_code (code),
  KEY idx_coupons_active_window (is_active, start_at, end_at),
  CONSTRAINT ck_coupons_discount_type CHECK (discount_type IN ('PERCENT','FIXED')),
  CONSTRAINT ck_coupons_discount_value CHECK (
      discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)),
  CONSTRAINT ck_coupons_window    CHECK (end_at > start_at),
  CONSTRAINT ck_coupons_usage     CHECK (usage_limit IS NULL OR used_count <= usage_limit),
  CONSTRAINT ck_coupons_min_order CHECK (min_order_amount >= 0),
  CONSTRAINT ck_coupons_per_user  CHECK (per_user_limit >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 35. payments ------------------------------------------------------------------------
CREATE TABLE payments (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NOT NULL,
  listing_id       BIGINT UNSIGNED NULL,
  package_id       BIGINT UNSIGNED NULL,
  coupon_id        BIGINT UNSIGNED NULL,
  amount           DECIMAL(15,2)   NOT NULL,
  discount_amount  DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
  final_amount     DECIMAL(15,2)   NOT NULL,
  currency         VARCHAR(3)         NOT NULL DEFAULT 'VND',
  payment_method   VARCHAR(20)     NOT NULL,
  transaction_code VARCHAR(50)     NOT NULL,
  gateway_txn_ref  VARCHAR(100)    NULL,
  gateway_response JSON            NULL,
  status           VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
  failure_reason   VARCHAR(500)    NULL,
  paid_at          DATETIME(6)     NULL,
  expires_at       DATETIME(6)     NOT NULL,
  refunded_at      DATETIME(6)     NULL,
  refund_amount    DECIMAL(15,2)   NULL,
  refund_note      VARCHAR(500)    NULL,
  refunded_by      BIGINT UNSIGNED NULL,
  client_ip        VARCHAR(45)     NULL,
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by       BIGINT UNSIGNED NULL,
  updated_by       BIGINT UNSIGNED NULL,
  deleted_at       DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_transaction_code (transaction_code),
  KEY idx_payments_user_id_created_at (user_id, created_at),
  KEY idx_payments_status_created_at (status, created_at),
  KEY idx_payments_status_expires_at (status, expires_at),
  KEY idx_payments_paid_at (paid_at),
  KEY idx_payments_gateway_txn_ref (gateway_txn_ref),
  KEY idx_payments_listing_id (listing_id),
  KEY idx_payments_package_id (package_id),
  KEY idx_payments_coupon_id (coupon_id),
  CONSTRAINT fk_payments_users              FOREIGN KEY (user_id)    REFERENCES users (id)              ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payments_listings           FOREIGN KEY (listing_id) REFERENCES listings (id)           ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payments_promotion_packages FOREIGN KEY (package_id) REFERENCES promotion_packages (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED')),
  CONSTRAINT ck_payments_method CHECK (payment_method IN ('SANDBOX','VNPAY','MOMO','BANK_TRANSFER')),
  CONSTRAINT ck_payments_amount CHECK (amount >= 0 AND discount_amount >= 0 AND final_amount >= 0),
  CONSTRAINT ck_payments_final_amount CHECK (final_amount = amount - discount_amount),
  CONSTRAINT ck_payments_discount_le_amount CHECK (discount_amount <= amount),
  CONSTRAINT ck_payments_paid CHECK (status <> 'SUCCESS' OR paid_at IS NOT NULL),
  CONSTRAINT ck_payments_refund CHECK (status <> 'REFUNDED'
      OR (refunded_at IS NOT NULL AND refund_amount IS NOT NULL AND refund_amount <= final_amount)),
  CONSTRAINT ck_payments_target CHECK (listing_id IS NOT NULL OR package_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 36. promotion_subscriptions ---------------------------------------------------------
CREATE TABLE promotion_subscriptions (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_id        BIGINT UNSIGNED NOT NULL,
  listing_id        BIGINT UNSIGNED NOT NULL,
  package_id        BIGINT UNSIGNED NOT NULL,
  user_id           BIGINT UNSIGNED NOT NULL,
  priority          INT UNSIGNED    NOT NULL DEFAULT 0,
  status            VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
  start_at          DATETIME(6)     NOT NULL,
  end_at            DATETIME(6)     NOT NULL,
  cancelled_reason  VARCHAR(500)    NULL,
  created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by        BIGINT UNSIGNED NULL,
  updated_by        BIGINT UNSIGNED NULL,
  deleted_at        DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_subscriptions_payment_id (payment_id),
  KEY idx_promotion_subscriptions_status_end_at (status, end_at),
  KEY idx_promotion_subscriptions_listing_id_status (listing_id, status),
  KEY idx_promotion_subscriptions_user_id (user_id),
  KEY idx_promotion_subscriptions_package_id (package_id),
  CONSTRAINT fk_promotion_subscriptions_payments           FOREIGN KEY (payment_id) REFERENCES payments (id)            ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_promotion_subscriptions_listings           FOREIGN KEY (listing_id) REFERENCES listings (id)            ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_promotion_subscriptions_promotion_packages FOREIGN KEY (package_id) REFERENCES promotion_packages (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_promotion_subscriptions_users              FOREIGN KEY (user_id)    REFERENCES users (id)               ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_promotion_subscriptions_status   CHECK (status IN ('PENDING','ACTIVE','EXPIRED','CANCELLED')),
  CONSTRAINT ck_promotion_subscriptions_window   CHECK (end_at > start_at),
  CONSTRAINT ck_promotion_subscriptions_priority CHECK (priority BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group notification (2 tables)
-- =====================================================================================

-- 37. notifications -------------------------------------------------------------------
CREATE TABLE notifications (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  type           VARCHAR(40)     NOT NULL,
  channel        VARCHAR(10)     NOT NULL DEFAULT 'IN_APP',
  title          VARCHAR(200)    NOT NULL,
  content        VARCHAR(1000)   NOT NULL,
  link           VARCHAR(500)    NULL,
  ref_type       VARCHAR(20)     NULL,
  ref_id         BIGINT UNSIGNED NULL,
  is_read        BOOLEAN         NOT NULL DEFAULT FALSE,
  read_at        DATETIME(6)     NULL,
  email_sent_at  DATETIME(6)     NULL,
  email_error    VARCHAR(500)    NULL,
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by     BIGINT UNSIGNED NULL,
  updated_by     BIGINT UNSIGNED NULL,
  deleted_at     DATETIME(6)     NULL,
  PRIMARY KEY (id),
  KEY idx_notifications_user_id_is_read_created_at (user_id, is_read, created_at),
  KEY idx_notifications_channel_email_sent_at (channel, email_sent_at),
  KEY idx_notifications_type_created_at (type, created_at),
  KEY idx_notifications_ref (ref_type, ref_id),
  KEY idx_notifications_created_at (created_at),
  CONSTRAINT fk_notifications_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_notifications_type CHECK (type IN (
      'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
      'LISTING_LOCKED','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW','PAYMENT_SUCCESS','PAYMENT_FAILED',
      'REPORT_THRESHOLD','AI_NEGATIVE_ALERT','ACCOUNT_LOCKED','VIOLATION_WARNING',
      'FOLLOWED_LANDLORD_NEW_LISTING','NEW_MATCHING_LISTING')),
  CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP','EMAIL')),
  CONSTRAINT ck_notifications_read CHECK (is_read = FALSE OR read_at IS NOT NULL),
  CONSTRAINT ck_notifications_ref CHECK ((ref_type IS NULL) = (ref_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 38. notification_preferences --------------------------------------------------------
CREATE TABLE notification_preferences (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  notification_type VARCHAR(40)     NOT NULL,
  in_app            BOOLEAN         NOT NULL DEFAULT TRUE,
  email             BOOLEAN         NOT NULL DEFAULT TRUE,
  created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by        BIGINT UNSIGNED NULL,
  updated_by        BIGINT UNSIGNED NULL,
  deleted_at        DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_preferences_user_type (user_id, notification_type),
  KEY idx_notification_preferences_user_id (user_id),
  CONSTRAINT fk_notification_preferences_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_notification_preferences_type CHECK (notification_type IN (
      'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
      'LISTING_LOCKED','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW','PAYMENT_SUCCESS','PAYMENT_FAILED',
      'REPORT_THRESHOLD','AI_NEGATIVE_ALERT','ACCOUNT_LOCKED','VIOLATION_WARNING',
      'FOLLOWED_LANDLORD_NEW_LISTING')),
  CONSTRAINT ck_notification_preferences_mandatory CHECK (
      notification_type NOT IN (
          'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_LOCKED',
          'PAYMENT_SUCCESS','PAYMENT_FAILED','ACCOUNT_LOCKED','VIOLATION_WARNING',
          'REPORT_THRESHOLD','AI_NEGATIVE_ALERT')
      OR (in_app = TRUE AND email = TRUE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group ai (5 tables)
-- =====================================================================================

-- 39. sentiment_results (append-only: id + created_at) --------------------------------
CREATE TABLE sentiment_results (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  comment_id             BIGINT UNSIGNED NOT NULL,
  label                  VARCHAR(20)     NOT NULL,
  score                  DECIMAL(4,3)    NULL,
  confidence             DECIMAL(4,3)    NULL,
  is_risk_comment        BOOLEAN         NOT NULL DEFAULT FALSE,
  suggested_action       VARCHAR(15)     NOT NULL DEFAULT 'NONE',
  weight                 DECIMAL(3,2)    NOT NULL DEFAULT 1.00,
  matched_positive_terms JSON            NULL,
  matched_negative_terms JSON            NULL,
  negation_applied       BOOLEAN         NOT NULL DEFAULT FALSE,
  analyzer_version       VARCHAR(20)     NOT NULL,
  processing_ms          INT UNSIGNED    NULL,
  error_message          VARCHAR(500)    NULL,
  retry_count            INT UNSIGNED    NOT NULL DEFAULT 0,
  is_latest              BOOLEAN         NOT NULL DEFAULT TRUE,
  analyzed_at            DATETIME(6)      NULL,
  created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  latest_uk BIGINT UNSIGNED GENERATED ALWAYS AS (IF(is_latest, comment_id, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sentiment_results_latest (latest_uk),
  KEY idx_sentiment_results_comment_id_created_at (comment_id, created_at),
  KEY idx_sentiment_results_suggested_action (suggested_action, is_latest),
  KEY idx_sentiment_results_analyzer_version (analyzer_version, created_at),
  KEY idx_sentiment_results_label_confidence (label, confidence),
  -- ON DELETE RESTRICT (không CASCADE): comment_id là cột nền của cột sinh STORED latest_uk, và
  -- MySQL cấm đặt referential action lên cột nền của generated column (lỗi 1215). Hệ thống xóa mềm
  -- bình luận (không xóa cứng) nên RESTRICT không bao giờ bị kích hoạt; dọn sentiment cũ do job.
  CONSTRAINT fk_sentiment_results_comments FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_sentiment_results_label CHECK (label IN
      ('POSITIVE','NEUTRAL','NEGATIVE','MIXED','PENDING_ANALYSIS')),
  CONSTRAINT ck_sentiment_results_action CHECK (suggested_action IN ('NONE','WATCH','NEED_REVIEW')),
  CONSTRAINT ck_sentiment_results_score CHECK (score IS NULL OR score BETWEEN -1 AND 1),
  CONSTRAINT ck_sentiment_results_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CONSTRAINT ck_sentiment_results_weight CHECK (weight BETWEEN 0 AND 1),
  CONSTRAINT ck_sentiment_results_pending CHECK (label <> 'PENDING_ANALYSIS' OR analyzed_at IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 40. recommendation_logs (append-only: id + created_at) ------------------------------
CREATE TABLE recommendation_logs (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NULL,
  session_id       VARCHAR(36)        NULL,
  listing_id       BIGINT UNSIGNED NOT NULL,
  source           VARCHAR(20)     NOT NULL,
  batch_id         VARCHAR(36)        NOT NULL,
  score            DECIMAL(6,4)    NOT NULL,
  rank_position    INT UNSIGNED    NOT NULL,
  area_score       DECIMAL(5,4)    NULL,
  price_score      DECIMAL(5,4)    NULL,
  category_score   DECIMAL(5,4)    NULL,
  amenity_score    DECIMAL(5,4)    NULL,
  trust_score_norm DECIMAL(5,4)    NULL,
  freshness_score  DECIMAL(5,4)    NULL,
  promoted_boost   DECIMAL(4,3)    NOT NULL DEFAULT 1.000,
  is_cold_start    BOOLEAN         NOT NULL DEFAULT FALSE,
  context          JSON            NULL,
  clicked_at       DATETIME(6)     NULL,
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_recommendation_logs_user_id_created_at (user_id, created_at),
  KEY idx_recommendation_logs_batch_id (batch_id),
  KEY idx_recommendation_logs_listing_id (listing_id),
  KEY idx_recommendation_logs_source_created_at (source, created_at),
  KEY idx_recommendation_logs_clicked_at (clicked_at),
  CONSTRAINT fk_recommendation_logs_users    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT fk_recommendation_logs_listings FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_recommendation_logs_boost CHECK (promoted_boost BETWEEN 1 AND 1.15),
  CONSTRAINT ck_recommendation_logs_rank  CHECK (rank_position >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 41. prediction_histories (append-only: id + created_at) -----------------------------
CREATE TABLE prediction_histories (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  listing_id        BIGINT UNSIGNED  NULL,
  user_id           BIGINT UNSIGNED  NOT NULL,
  category_id       BIGINT UNSIGNED  NOT NULL,
  ward_id           BIGINT UNSIGNED  NOT NULL,
  area              DECIMAL(8,2)     NOT NULL,
  room_count        INT UNSIGNED NULL,
  toilet_count      INT UNSIGNED NULL,
  furniture_status  VARCHAR(10)      NULL,
  amenity_ids       JSON             NULL,
  suggested_price   DECIMAL(15,2)    NULL,
  price_low         DECIMAL(15,2)    NULL,
  price_median      DECIMAL(15,2)    NULL,
  price_high        DECIMAL(15,2)    NULL,
  price_per_sqm     DECIMAL(15,2)    NULL,
  sample_size       INT UNSIGNED     NOT NULL DEFAULT 0,
  scope_used        VARCHAR(10)      NOT NULL,
  confidence        VARCHAR(20)      NOT NULL,
  dispersion_ratio  DECIMAL(6,4)     NULL,
  adjustment_detail JSON             NULL,
  explanation       VARCHAR(500)     NULL,
  input_price       DECIMAL(15,2)    NULL,
  deviation_ratio   DECIMAL(6,4)     NULL,
  is_flagged        BOOLEAN          NOT NULL DEFAULT FALSE,
  is_applied        BOOLEAN          NOT NULL DEFAULT FALSE,
  estimator_version VARCHAR(20)      NOT NULL,
  created_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_prediction_histories_listing_id_created_at (listing_id, created_at),
  KEY idx_prediction_histories_user_id_created_at (user_id, created_at),
  KEY idx_prediction_histories_is_flagged (is_flagged, created_at),
  KEY idx_prediction_histories_confidence (confidence),
  KEY idx_prediction_histories_category_id (category_id),
  KEY idx_prediction_histories_ward_id (ward_id),
  KEY idx_prediction_histories_sample_size (sample_size),
  CONSTRAINT fk_prediction_histories_listings  FOREIGN KEY (listing_id)  REFERENCES listings (id)   ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT fk_prediction_histories_users      FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_prediction_histories_categories FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_prediction_histories_wards      FOREIGN KEY (ward_id)     REFERENCES wards (id)      ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_prediction_histories_confidence CHECK (confidence IN
      ('HIGH','MEDIUM','LOW','INSUFFICIENT_DATA')),
  CONSTRAINT ck_prediction_histories_scope CHECK (scope_used IN ('WARD','DISTRICT','PROVINCE')),
  CONSTRAINT ck_prediction_histories_range CHECK (
      price_low IS NULL OR price_high IS NULL OR price_low <= price_high),
  CONSTRAINT ck_prediction_histories_insufficient CHECK (
      confidence <> 'INSUFFICIENT_DATA' OR suggested_price IS NULL),
  CONSTRAINT ck_prediction_histories_area CHECK (area > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 42. chatbot_conversations -----------------------------------------------------------
CREATE TABLE chatbot_conversations (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT UNSIGNED NULL,
  session_id         VARCHAR(36)        NOT NULL,
  status             VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
  last_intent        VARCHAR(15)     NULL,
  collected_filters  JSON            NOT NULL,
  clarify_turn_count INT UNSIGNED    NOT NULL DEFAULT 0,
  message_count      INT UNSIGNED    NOT NULL DEFAULT 0,
  started_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_message_at    DATETIME(6)     NULL,
  ended_at           DATETIME(6)     NULL,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by         BIGINT UNSIGNED NULL,
  updated_by         BIGINT UNSIGNED NULL,
  deleted_at         DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chatbot_conversations_session_id (session_id),
  KEY idx_chatbot_conversations_user_id_started_at (user_id, started_at),
  KEY idx_chatbot_conversations_status_last_message_at (status, last_message_at),
  KEY idx_chatbot_conversations_last_intent (last_intent),
  CONSTRAINT fk_chatbot_conversations_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_chatbot_conversations_status CHECK (status IN ('ACTIVE','COMPLETED','ABANDONED')),
  CONSTRAINT ck_chatbot_conversations_intent CHECK (last_intent IS NULL OR last_intent IN
      ('FIND_ROOM','HOW_TO_POST','GLOSSARY','FAQ','GREETING','OUT_OF_SCOPE','SENSITIVE','UNKNOWN')),
  CONSTRAINT ck_chatbot_conversations_clarify CHECK (clarify_turn_count BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 43. chatbot_messages (append-only: id + created_at) ---------------------------------
CREATE TABLE chatbot_messages (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  conversation_id    BIGINT UNSIGNED NOT NULL,
  sender             VARCHAR(10)     NOT NULL,
  content            VARCHAR(2000)   NOT NULL,
  intent             VARCHAR(15)     NULL,
  intent_confidence  DECIMAL(4,3)    NULL,
  extracted_slots    JSON            NULL,
  result_listing_ids JSON            NULL,
  result_count       INT UNSIGNED    NULL,
  is_fallback        BOOLEAN         NOT NULL DEFAULT FALSE,
  response_ms        INT UNSIGNED    NULL,
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_chatbot_messages_conversation_id_created_at (conversation_id, created_at),
  KEY idx_chatbot_messages_intent_created_at (intent, created_at),
  KEY idx_chatbot_messages_is_fallback (is_fallback, created_at),
  CONSTRAINT fk_chatbot_messages_chatbot_conversations FOREIGN KEY (conversation_id)
      REFERENCES chatbot_conversations (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_chatbot_messages_sender CHECK (sender IN ('USER','BOT')),
  CONSTRAINT ck_chatbot_messages_intent CHECK (intent IS NULL OR intent IN
      ('FIND_ROOM','HOW_TO_POST','GLOSSARY','FAQ','GREETING','OUT_OF_SCOPE','SENSITIVE','UNKNOWN')),
  CONSTRAINT ck_chatbot_messages_bot_intent CHECK (sender = 'USER' OR intent IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Group admin (3 tables)
-- =====================================================================================

-- 44. audit_logs (append-only: id + created_at) --------------------------------------
CREATE TABLE audit_logs (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_id     BIGINT UNSIGNED NULL,
  actor_email  VARCHAR(190)    NULL,
  action       VARCHAR(25)     NOT NULL,
  target_type  VARCHAR(20)     NOT NULL,
  target_id    BIGINT UNSIGNED NULL,
  target_label VARCHAR(200)    NULL,
  old_value    JSON            NULL,
  new_value    JSON            NULL,
  reason       VARCHAR(500)    NULL,
  ip_address   VARCHAR(45)     NULL,
  user_agent   VARCHAR(255)    NULL,
  request_id   VARCHAR(36)        NULL,
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor_id_created_at (actor_id, created_at),
  KEY idx_audit_logs_target (target_type, target_id, created_at),
  KEY idx_audit_logs_action_created_at (action, created_at),
  KEY idx_audit_logs_created_at (created_at),
  KEY idx_audit_logs_request_id (request_id),
  CONSTRAINT fk_audit_logs_users FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT ck_audit_logs_action CHECK (action IN
      ('USER_LOCK','USER_UNLOCK','ROLE_CHANGE','LISTING_APPROVE','LISTING_REJECT','LISTING_LOCK',
       'LISTING_UNLOCK','LISTING_EDIT','AI_CONFIG_CHANGE','PACKAGE_CHANGE','SYSTEM_CONFIG_CHANGE','PAYMENT_REFUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 45. system_configs ------------------------------------------------------------------
CREATE TABLE system_configs (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key    VARCHAR(80)     NOT NULL,
  config_value  TEXT            NOT NULL,
  default_value TEXT            NOT NULL,
  value_type    VARCHAR(10)     NOT NULL,
  group_name    VARCHAR(30)     NOT NULL,
  label         VARCHAR(150)    NOT NULL,
  description   VARCHAR(500)    NULL,
  min_value     DECIMAL(15,4)   NULL,
  max_value     DECIMAL(15,4)   NULL,
  is_editable   BOOLEAN         NOT NULL DEFAULT TRUE,
  display_order INT             NOT NULL DEFAULT 0,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by    BIGINT UNSIGNED NULL,
  updated_by    BIGINT UNSIGNED NULL,
  deleted_at    DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_configs_config_key (config_key),
  KEY idx_system_configs_group_name_display_order (group_name, display_order),
  CONSTRAINT ck_system_configs_value_type CHECK (value_type IN ('STRING','INT','DECIMAL','BOOLEAN','JSON')),
  CONSTRAINT ck_system_configs_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 46. ai_configs ----------------------------------------------------------------------
CREATE TABLE ai_configs (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  module       VARCHAR(15)     NOT NULL,
  config_key   VARCHAR(60)     NOT NULL,
  config_value JSON            NOT NULL,
  value_schema VARCHAR(30)     NOT NULL,
  description  VARCHAR(500)    NULL,
  is_enabled   BOOLEAN         NOT NULL DEFAULT TRUE,
  version      INT UNSIGNED    NOT NULL DEFAULT 1,
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by   BIGINT UNSIGNED NULL,
  updated_by   BIGINT UNSIGNED NULL,
  deleted_at   DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_configs_module_config_key (module, config_key),
  KEY idx_ai_configs_module_enabled (module, is_enabled),
  CONSTRAINT ck_ai_configs_module  CHECK (module IN ('SENTIMENT','RECOMMENDATION','CHATBOT','PRICE')),
  CONSTRAINT ck_ai_configs_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================================
-- Deferred foreign keys (forward / circular references) — added after all tables exist
-- =====================================================================================

-- payments -> coupons
ALTER TABLE payments
  ADD CONSTRAINT fk_payments_coupons FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- listings -> prediction_histories (circular with fk_prediction_histories_listings; both cols nullable)
ALTER TABLE listings
  ADD CONSTRAINT fk_listings_prediction_histories FOREIGN KEY (price_prediction_id) REFERENCES prediction_histories (id) ON DELETE SET NULL ON UPDATE RESTRICT;
