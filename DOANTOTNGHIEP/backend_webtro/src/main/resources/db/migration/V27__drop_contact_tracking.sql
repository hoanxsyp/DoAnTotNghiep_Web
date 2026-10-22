-- The phone number is now displayed directly on listing details. Remove the former
-- reveal/contact tracking feature and its denormalized statistics.

DELETE FROM notifications WHERE type = 'NEW_CONTACT';
DELETE FROM notification_preferences WHERE notification_type = 'NEW_CONTACT';

ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
  'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
  'LISTING_LOCKED','LISTING_HIDDEN','NEW_COMMENT','NEW_REVIEW',
  'RENTAL_REQUEST_CREATED','RENTAL_REQUEST_APPROVED','RENTAL_REQUEST_REJECTED',
  'RENTAL_REQUEST_CANCELLED','RENTAL_CONFIRMED','RENTAL_CHECKED_OUT',
  'PAYMENT_SUCCESS','PAYMENT_FAILED','REPORT_THRESHOLD','AI_NEGATIVE_ALERT','ACCOUNT_LOCKED',
  'VIOLATION_WARNING','ACCOUNT_VERIFICATION','FOLLOWED_LANDLORD_NEW_LISTING','NEW_MATCHING_LISTING'));

ALTER TABLE notification_preferences
  DROP CHECK ck_notification_preferences_type,
  DROP CHECK ck_notification_preferences_mandatory;
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_type CHECK (notification_type IN (
  'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
  'LISTING_LOCKED','NEW_COMMENT','NEW_REVIEW','PAYMENT_SUCCESS','PAYMENT_FAILED',
  'REPORT_THRESHOLD','ACCOUNT_LOCKED','VIOLATION_WARNING','FOLLOWED_LANDLORD_NEW_LISTING'));
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_mandatory CHECK (
  notification_type NOT IN (
    'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_LOCKED',
    'PAYMENT_SUCCESS','PAYMENT_FAILED','ACCOUNT_LOCKED','VIOLATION_WARNING','REPORT_THRESHOLD'
  ) OR (in_app = TRUE AND email = TRUE)
);

DROP TABLE contact_logs;

ALTER TABLE listings DROP CHECK ck_listings_counters_non_negative;
ALTER TABLE listings DROP COLUMN contact_count;
ALTER TABLE listings ADD CONSTRAINT ck_listings_counters_non_negative CHECK (
  view_count >= 0 AND favorite_count >= 0 AND comment_count >= 0
  AND review_count >= 0 AND need_review_count >= 0 AND renew_count >= 0);

DELETE FROM system_configs WHERE config_key IN ('contact.dedup_minutes', 'review.require_contact');
