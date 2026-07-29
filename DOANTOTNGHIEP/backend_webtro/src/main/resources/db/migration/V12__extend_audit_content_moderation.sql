-- =====================================================================================
-- V12 — Mở rộng CHECK constraint audit_logs.action cho kiểm duyệt nội dung
-- =====================================================================================
-- Các endpoint kiểm duyệt bình luận & đánh giá (ẩn/bỏ ẩn bình luận, đánh dấu spam, ẩn/bỏ ẩn
-- đánh giá) sinh thêm giá trị enum mới cho AuditAction. CHECK constraint hiện tại (V11) chưa
-- liệt kê nên phải regenerate để không chặn INSERT.
--
-- Danh sách dưới đây PHẢI đồng bộ 1-1 với enum Java com.webtro.common.enums.AuditAction.
-- Nếu thêm giá trị enum mới, cập nhật đúng constraint này.
-- MySQL 8: đổi CHECK constraint = DROP rồi ADD lại.
-- =====================================================================================

ALTER TABLE audit_logs DROP CHECK ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action CHECK (action IN (
    'USER_LOCK','USER_UNLOCK','ROLE_CHANGE',
    'LANDLORD_VERIFY','LANDLORD_UNVERIFY',
    'LISTING_APPROVE','LISTING_REJECT','LISTING_LOCK','LISTING_UNLOCK',
    'LISTING_HIDE','LISTING_UNHIDE','LISTING_EDIT',
    'COMMENT_HIDE','COMMENT_UNHIDE','COMMENT_SPAM','REVIEW_HIDE','REVIEW_UNHIDE',
    'AI_CONFIG_CHANGE','PACKAGE_CHANGE','SYSTEM_CONFIG_CHANGE','PAYMENT_REFUND'));
