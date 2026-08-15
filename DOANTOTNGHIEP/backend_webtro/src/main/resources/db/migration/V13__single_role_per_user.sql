-- =====================================================================================
-- V13__single_role_per_user.sql — Mỗi người dùng có ĐÚNG MỘT vai trò.
--
-- Trước V13: quan hệ user <-> role là NHIỀU-NHIỀU qua bảng nối `user_roles`
--            (UNIQUE(user_id, role_id) cho phép một user giữ nhiều dòng).
-- Từ V13:    quan hệ là NHIỀU-MỘT qua cột `users.role_id` (NOT NULL, FK -> roles).
--            Ràng buộc "1 role/user" do CHÍNH CẤU TRÚC bảng bảo đảm, không cần
--            trigger hay unique index mô phỏng.
--
-- Vì sao bỏ hẳn `user_roles` thay vì thêm UNIQUE(user_id):
--   `user_roles` dùng xoá mềm (deleted_at). Một UNIQUE(user_id) thuần sẽ vướng
--   các dòng đã xoá mềm ngay lần đổi vai trò thứ hai; muốn tránh phải thêm
--   generated column kiểu `users.email_uk` — phức tạp hơn hẳn một cột FK.
--
-- Lịch sử gán vai trò KHÔNG mất: `AdminUserServiceImpl` đã ghi `audit_logs` với
-- action ROLE_CHANGE (actor, giá trị cũ, giá trị mới, lý do) ở mỗi lần đổi.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Thêm cột role_id (tạm cho phép NULL để backfill).
-- -------------------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN role_id BIGINT UNSIGNED NULL AFTER status;

-- -------------------------------------------------------------------------------------
-- 2. Backfill: user đang giữ nhiều vai trò -> lấy vai trò CAO NHẤT.
--    V2 seed id tăng dần theo mức quyền: 1=TENANT < 2=LANDLORD < 3=MODERATOR < 4=ADMIN,
--    nên MAX(role_id) chính là vai trò cao nhất. Chỉ tính dòng còn hiệu lực vì
--    `updateRoles` cũ xoá mềm chứ không xoá cứng.
-- -------------------------------------------------------------------------------------
UPDATE users u
JOIN (SELECT user_id, MAX(role_id) AS role_id
        FROM user_roles
       WHERE deleted_at IS NULL
       GROUP BY user_id) picked ON picked.user_id = u.id
   SET u.role_id = picked.role_id;

-- Phòng hờ: tài khoản không có dòng user_roles nào còn hiệu lực -> vai trò mặc định TENANT.
UPDATE users SET role_id = 1 WHERE role_id IS NULL;

-- -------------------------------------------------------------------------------------
-- 3. Siết ràng buộc: NOT NULL + khoá ngoại + index.
--    ON DELETE RESTRICT: không cho xoá một vai trò đang có người dùng.
-- -------------------------------------------------------------------------------------
ALTER TABLE users
  MODIFY COLUMN role_id BIGINT UNSIGNED NOT NULL,
  ADD KEY idx_users_role_id (role_id),
  ADD CONSTRAINT fk_users_roles FOREIGN KEY (role_id) REFERENCES roles (id)
      ON DELETE RESTRICT ON UPDATE RESTRICT;

-- -------------------------------------------------------------------------------------
-- 4. Bỏ bảng nối. Từ đây không còn đường nào tạo ra user nhiều vai trò.
-- -------------------------------------------------------------------------------------
DROP TABLE user_roles;
