-- =====================================================================================
-- V11 — Mở rộng CHECK constraint cho audit_logs.action và notifications.type
-- =====================================================================================
-- Các endpoint kiểm duyệt/quản trị bổ sung (kiểm duyệt tin ẩn/bỏ ẩn, xác thực/hủy xác thực
-- chủ trọ) sinh thêm giá trị enum mới cho AuditAction và NotificationType. CHECK constraint
-- gốc ở V1 chỉ liệt kê tập enum ban đầu nên phải regenerate để không chặn INSERT.
--
-- Danh sách dưới đây PHẢI đồng bộ 1-1 với enum Java:
--   com.webtro.common.enums.AuditAction
--   com.webtro.common.enums.NotificationType
-- Nếu thêm giá trị enum mới, cập nhật đúng constraint tương ứng ở đây.
-- MySQL 8: đổi CHECK constraint = DROP rồi ADD lại.
-- =====================================================================================

-- audit_logs.action ------------------------------------------------------------------
ALTER TABLE audit_logs DROP CHECK ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action CHECK (action IN (
    'USER_LOCK','USER_UNLOCK','ROLE_CHANGE',
    'LANDLORD_VERIFY','LANDLORD_UNVERIFY',
    'LISTING_APPROVE','LISTING_REJECT','LISTING_LOCK','LISTING_UNLOCK',
    'LISTING_HIDE','LISTING_UNHIDE','LISTING_EDIT',
    'AI_CONFIG_CHANGE','PACKAGE_CHANGE','SYSTEM_CONFIG_CHANGE','PAYMENT_REFUND'));

-- notifications.type -----------------------------------------------------------------
ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'ACCOUNT_REGISTERED','LISTING_APPROVED','LISTING_REJECTED','LISTING_EXPIRING','LISTING_EXPIRED',
    'LISTING_LOCKED','LISTING_HIDDEN','NEW_CONTACT','NEW_COMMENT','NEW_REVIEW',
    'PAYMENT_SUCCESS','PAYMENT_FAILED','REPORT_THRESHOLD','AI_NEGATIVE_ALERT','ACCOUNT_LOCKED',
    'VIOLATION_WARNING','ACCOUNT_VERIFICATION','FOLLOWED_LANDLORD_NEW_LISTING','NEW_MATCHING_LISTING'));
