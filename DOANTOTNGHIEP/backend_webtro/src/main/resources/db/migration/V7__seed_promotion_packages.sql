-- =====================================================================================
-- V7__seed_promotion_packages.sql — Seed promotion packages + sample coupons
-- Project "webtro" — MySQL 8.4 / Flyway. Charset utf8mb4, timezone UTC.
-- Source of truth for columns: V1__baseline_schema.sql (tables 33 promotion_packages, 34 coupons).
--
-- NOTE: V1's `promotion_packages` has NO `purpose` column (PackagePurpose is not a physical
-- column in the baseline). The push-top / highlight / combo intent is therefore encoded in the
-- `code` and `badge_label` values below (PUSH_* / HIGHLIGHT_* / VIP_* ). Only columns that exist
-- in V1 are referenced. `priority` respects promotion.max_priority = 100 (ck_..._priority 0..100).
-- Prices are real-world VND as DECIMAL(15,2).
-- =====================================================================================

-- --- promotion_packages --------------------------------------------------------------
-- Explicit ids so future migrations / other tables can reference them deterministically.
INSERT INTO promotion_packages
  (id, code, name, description, price, duration_days, priority, badge_label, badge_color, is_active, display_order)
VALUES
  (1, 'PUSH_TOP_7',   'Đẩy tin 7 ngày',
      'Đẩy tin lên đầu danh sách tìm kiếm liên tục trong 7 ngày, tiếp cận nhiều người thuê hơn.',
      20000.00,  7,  30, 'ĐẨY TIN',    '#2563EB', TRUE, 1),
  (2, 'PUSH_TOP_30',  'Đẩy tin 30 ngày',
      'Đẩy tin lên đầu danh sách tìm kiếm liên tục trong 30 ngày, tiết kiệm hơn so với gói 7 ngày.',
      70000.00,  30, 40, 'ĐẨY TIN',    '#1D4ED8', TRUE, 2),
  (3, 'HIGHLIGHT_7',  'Tin nổi bật 7 ngày',
      'Làm nổi bật tin đăng với khung viền và nhãn nổi bật trong 7 ngày để thu hút sự chú ý.',
      35000.00,  7,  50, 'NỔI BẬT',    '#F59E0B', TRUE, 3),
  (4, 'HIGHLIGHT_30', 'Tin nổi bật 30 ngày',
      'Làm nổi bật tin đăng với khung viền và nhãn nổi bật trong suốt 30 ngày.',
      120000.00, 30, 60, 'NỔI BẬT',    '#D97706', TRUE, 4),
  (5, 'VIP_COMBO_30', 'Combo VIP 30 ngày',
      'Gói VIP cao cấp: vừa đẩy tin lên đầu vừa làm nổi bật tin đăng liên tục 30 ngày, ưu tiên hiển thị cao nhất.',
      199000.00, 30, 100,'VIP',        '#DC2626', TRUE, 5);

-- --- coupons (sample: one PERCENT, one FIXED) ----------------------------------------
-- start_at / end_at are DATETIME(6) in UTC; end_at > start_at (ck_coupons_window).
INSERT INTO coupons
  (id, code, description, discount_type, discount_value, max_discount_amount, min_order_amount,
   usage_limit, used_count, per_user_limit, start_at, end_at, is_active)
VALUES
  (1, 'CHAOMUNG10', 'Giảm 10% cho đơn đẩy tin đầu tiên (tối đa 30.000đ).',
      'PERCENT', 10.00, 30000.00, 20000.00,
      1000, 0, 1, '2026-01-01 00:00:00.000000', '2026-12-31 23:59:59.999999', TRUE),
  (2, 'GIAM20K',    'Giảm ngay 20.000đ cho đơn hàng từ 50.000đ trở lên.',
      'FIXED',   20000.00, NULL, 50000.00,
      500,  0, 2, '2026-01-01 00:00:00.000000', '2026-12-31 23:59:59.999999', TRUE);
