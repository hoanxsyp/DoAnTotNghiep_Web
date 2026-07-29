-- =====================================================================================
-- V2__seed_roles_permissions.sql — Seed RBAC: roles, permissions, role_permissions
-- Source of truth: docs/00_CANONICAL_DECISIONS.md section 4.1 (roles) + 4.2 (27 permissions).
-- Explicit ids so role_permissions (and later user_roles) can reference them deterministically.
-- Charset utf8mb4, timezone UTC. Flyway runs this once.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. roles (4) — id fixed 1..4, matches display_order and ck_roles_code.
-- -------------------------------------------------------------------------------------
INSERT INTO roles (id, code, name, description, is_system, display_order) VALUES
  (1, 'ROLE_TENANT',    'Người thuê',        'Người thuê phòng, quyền mặc định khi đăng ký', TRUE, 1),
  (2, 'ROLE_LANDLORD',  'Chủ trọ',           'Chủ trọ, bao gồm người cho ở ghép',            TRUE, 2),
  (3, 'ROLE_MODERATOR', 'Kiểm duyệt viên',   'Kiểm duyệt nội dung, không quản lý cấu hình tài chính', TRUE, 3),
  (4, 'ROLE_ADMIN',     'Quản trị viên',     'Toàn quyền hệ thống',                          TRUE, 4);

-- -------------------------------------------------------------------------------------
-- 2. permissions (27) — id fixed 1..27, order theo ma trận canonical mục 4.2.
-- -------------------------------------------------------------------------------------
INSERT INTO permissions (id, code, name, module, description) VALUES
  ( 1, 'LISTING_CREATE',       'Tạo tin đăng',                    'LISTING',   'Chủ trọ tạo tin đăng mới'),
  ( 2, 'LISTING_UPDATE_OWN',   'Sửa tin đăng của mình',           'LISTING',   'Chủ trọ chỉnh sửa tin đăng do mình sở hữu'),
  ( 3, 'LISTING_UPDATE_ANY',   'Sửa mọi tin đăng',                'LISTING',   'Chỉnh sửa bất kỳ tin đăng nào'),
  ( 4, 'LISTING_MODERATE',     'Kiểm duyệt tin đăng',             'LISTING',   'Duyệt, từ chối, gắn cờ, tạm ẩn tin đăng'),
  ( 5, 'LISTING_LOCK',         'Khóa tin đăng',                   'LISTING',   'Khóa tin đăng vi phạm'),
  ( 6, 'LISTING_VIEW_ANY',     'Xem mọi tin đăng',                'LISTING',   'Xem cả tin đăng chưa công khai'),
  ( 7, 'FAVORITE_MANAGE',      'Quản lý yêu thích',               'FAVORITE',  'Thêm, xóa tin đăng yêu thích'),
  ( 8, 'CONTACT_CREATE',       'Liên hệ chủ trọ',                 'CONTACT',   'Gửi yêu cầu liên hệ tới chủ trọ'),
  ( 9, 'COMMENT_CREATE',       'Bình luận',                       'COMMENT',   'Viết bình luận trên tin đăng'),
  (10, 'COMMENT_MODERATE',     'Kiểm duyệt bình luận',            'COMMENT',   'Ẩn, xóa, duyệt bình luận'),
  (11, 'REVIEW_CREATE',        'Đánh giá',                        'REVIEW',    'Viết đánh giá chủ trọ hoặc phòng'),
  (12, 'REVIEW_MODERATE',      'Kiểm duyệt đánh giá',             'REVIEW',    'Ẩn, xóa đánh giá vi phạm'),
  (13, 'REPORT_CREATE',        'Gửi báo cáo',                     'REPORT',    'Báo cáo tin đăng, bình luận, người dùng'),
  (14, 'REPORT_RESOLVE',       'Xử lý báo cáo',                   'REPORT',    'Xem xét và giải quyết báo cáo'),
  (15, 'WARNING_SEND',         'Gửi cảnh báo',                    'WARNING',   'Gửi cảnh báo vi phạm tới người dùng'),
  (16, 'USER_MANAGE',          'Quản lý người dùng',              'USER',      'Khóa, mở khóa tài khoản người dùng'),
  (17, 'USER_ROLE_ASSIGN',     'Gán vai trò',                     'USER',      'Gán, thu hồi vai trò cho người dùng'),
  (18, 'LANDLORD_VERIFY',      'Xác minh chủ trọ',                'LANDLORD',  'Duyệt hồ sơ xác minh chủ trọ'),
  (19, 'PAYMENT_VIEW_OWN',     'Xem thanh toán của mình',         'PAYMENT',   'Chủ trọ xem lịch sử thanh toán của mình'),
  (20, 'PAYMENT_MANAGE',       'Quản lý thanh toán',              'PAYMENT',   'Quản lý, hoàn tiền mọi giao dịch'),
  (21, 'PACKAGE_MANAGE',       'Quản lý gói dịch vụ',             'PACKAGE',   'Tạo, sửa, xóa gói dịch vụ'),
  (22, 'CATALOG_MANAGE',       'Quản lý danh mục',                'CATALOG',   'Quản lý danh mục, địa giới, tiện ích'),
  (23, 'AI_CONFIG_MANAGE',     'Quản lý cấu hình AI',             'AI',        'Cấu hình các module AI'),
  (24, 'AI_LOG_VIEW',          'Xem nhật ký AI',                  'AI',        'Xem nhật ký hoạt động AI'),
  (25, 'SYSTEM_CONFIG_MANAGE', 'Quản lý cấu hình hệ thống',       'SYSTEM',    'Quản lý cấu hình toàn hệ thống'),
  (26, 'STATISTIC_VIEW',       'Xem thống kê',                    'STATISTIC', 'Xem báo cáo thống kê'),
  (27, 'AUDIT_LOG_VIEW',       'Xem nhật ký kiểm toán',           'AUDIT',     'Xem nhật ký kiểm toán hệ thống');

-- -------------------------------------------------------------------------------------
-- 3. role_permissions — ma trận canonical mục 4.2 (49 dòng).
--    TENANT(1): 5 | LANDLORD(2): 8 | MODERATOR(3): 9 | ADMIN(4): 27
-- -------------------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id) VALUES
  -- ROLE_TENANT (1)
  (1,  7), (1,  8), (1,  9), (1, 11), (1, 13),
  -- ROLE_LANDLORD (2)
  (2,  1), (2,  2), (2,  7), (2,  8), (2,  9), (2, 11), (2, 13), (2, 19),
  -- ROLE_MODERATOR (3)
  (3,  4), (3,  6), (3, 10), (3, 12), (3, 13), (3, 14), (3, 15), (3, 18), (3, 24),
  -- ROLE_ADMIN (4) — toàn bộ 27 permission
  (4,  1), (4,  2), (4,  3), (4,  4), (4,  5), (4,  6), (4,  7), (4,  8), (4,  9),
  (4, 10), (4, 11), (4, 12), (4, 13), (4, 14), (4, 15), (4, 16), (4, 17), (4, 18),
  (4, 19), (4, 20), (4, 21), (4, 22), (4, 23), (4, 24), (4, 25), (4, 26), (4, 27);
