-- =====================================================================================
-- V14__drop_per_user_capability_configs.sql
--
-- Gỡ hai cấu hình khiến HAI NGƯỜI DÙNG CÙNG VAI TRÒ lại có bộ chức năng khác nhau.
-- Code đọc hai key này đã bị bỏ; để lại dòng trong `system_configs` thì màn Cấu hình hệ
-- thống vẫn hiện ra và Admin sửa được một thứ không còn tác dụng gì.
--
--   1. listing.auto_approve.trusted_landlord
--      Tự động duyệt tin của chủ trọ "đã xác minh + điểm uy tín cao". Hai chủ trọ cùng vai
--      trò đi hai luồng khác nhau (một người đăng là hiện ngay, một người phải chờ duyệt).
--      Nay mọi tin đều vào hàng chờ kiểm duyệt.
--
--   2. spam.listing.new_account_daily
--      Hạn mức đăng tin riêng cho tài khoản dưới 7 ngày tuổi. Nay mọi người dùng chung một
--      hạn mức `spam.listing.daily`.
-- =====================================================================================

DELETE FROM system_configs
 WHERE config_key IN ('listing.auto_approve.trusted_landlord',
                      'spam.listing.new_account_daily');
