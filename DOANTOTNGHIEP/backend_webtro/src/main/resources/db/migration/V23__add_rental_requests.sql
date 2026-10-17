-- Rental request lifecycle and hard concurrency guards for active rentals.

ALTER TABLE listings DROP CHECK ck_listings_status;
ALTER TABLE listings ADD CONSTRAINT ck_listings_status CHECK (status IN
  ('DRAFT','PENDING','ACTIVE','REJECTED','HIDDEN','EXPIRED','CLOSED','RENTED','LOCKED','NEED_REVIEW','DELETED'));

CREATE TABLE rental_requests (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  listing_id            BIGINT UNSIGNED NOT NULL,
  tenant_id             BIGINT UNSIGNED NOT NULL,
  desired_viewing_date  DATE            NOT NULL,
  status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
  landlord_note         VARCHAR(500)    NULL,
  approved_at           DATETIME(6)     NULL,
  rejected_at           DATETIME(6)     NULL,
  cancelled_at          DATETIME(6)     NULL,
  rented_at             DATETIME(6)     NULL,
  checked_out_at        DATETIME(6)     NULL,
  created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by            BIGINT UNSIGNED NULL,
  updated_by            BIGINT UNSIGNED NULL,
  deleted_at            DATETIME(6)     NULL,

  open_listing_id BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status IN ('PENDING','APPROVED','RENTED') THEN listing_id ELSE NULL END
  ) STORED,
  open_tenant_id BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status IN ('PENDING','APPROVED','RENTED') THEN tenant_id ELSE NULL END
  ) STORED,
  active_listing_id BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status = 'RENTED' THEN listing_id ELSE NULL END
  ) STORED,
  active_tenant_id BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status = 'RENTED' THEN tenant_id ELSE NULL END
  ) STORED,

  PRIMARY KEY (id),
  UNIQUE KEY uk_rental_requests_open_pair (open_listing_id, open_tenant_id),
  UNIQUE KEY uk_rental_requests_active_listing (active_listing_id),
  UNIQUE KEY uk_rental_requests_active_tenant (active_tenant_id),
  KEY idx_rental_requests_tenant_status_created (tenant_id, status, created_at),
  KEY idx_rental_requests_listing_status_created (listing_id, status, created_at),
  CONSTRAINT fk_rental_requests_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_rental_requests_tenant FOREIGN KEY (tenant_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_rental_requests_status CHECK (status IN
    ('PENDING','APPROVED','REJECTED','CANCELLED','RENTED','CHECKED_OUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
  'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
  'LISTING_LOCKED','LISTING_HIDDEN','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW',
  'RENTAL_REQUEST_CREATED','RENTAL_REQUEST_APPROVED','RENTAL_REQUEST_REJECTED',
  'RENTAL_CONFIRMED','RENTAL_CHECKED_OUT',
  'PAYMENT_SUCCESS','PAYMENT_FAILED','REPORT_THRESHOLD','AI_NEGATIVE_ALERT','ACCOUNT_LOCKED',
  'VIOLATION_WARNING','ACCOUNT_VERIFICATION','FOLLOWED_LANDLORD_NEW_LISTING','NEW_MATCHING_LISTING'));
