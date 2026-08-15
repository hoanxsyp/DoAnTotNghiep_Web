-- =====================================================================================
-- V15__drop_permission_tables.sql
-- Chuyển từ RBAC Role -> Permission sang phân quyền trực tiếp theo Role.
--
-- Lý do: các tài khoản cùng role phải có đúng cùng bộ chức năng, hệ thống không còn
-- bảng permission hay bảng nối role_permissions. Backend kiểm tra bằng hasRole/hasAnyRole.
-- =====================================================================================

DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
