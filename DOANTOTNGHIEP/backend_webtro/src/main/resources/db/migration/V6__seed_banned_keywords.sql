-- =====================================================================================
-- V6__seed_banned_keywords.sql
-- Seed dữ liệu vận hành: từ khóa cấm cho thị trường cho thuê phòng trọ VN.
-- Bảng: banned_keywords (bảng số 32 trong V1 baseline).
-- Enum canonical: severity IN ('MILD','SEVERE'); applies_to IN ('LISTING','COMMENT','BOTH').
-- normalized_keyword: chữ thường, bỏ dấu tiếng Việt (dùng cho so khớp) — UNIQUE.
-- created_by / updated_by để NULL (seed hệ thống, cột nullable, không vi phạm FK).
-- id đặt tường minh 1..20 để bảng khác có thể tham chiếu ổn định nếu cần.
-- =====================================================================================

INSERT INTO banned_keywords
  (id, keyword, normalized_keyword, severity, applies_to, is_regex, category, note, is_active)
VALUES
  -- ----- Nhóm LỪA ĐẢO / SCAM (SEVERE) -----
  ( 1, N'lừa đảo',              'lua dao',                 'SEVERE', 'BOTH',    FALSE, 'SCAM',      N'Tố cáo/vu khống lừa đảo trong nội dung', TRUE),
  ( 2, N'chuyển khoản trước',   'chuyen khoan truoc',      'SEVERE', 'LISTING', FALSE, 'SCAM',      N'Yêu cầu chuyển tiền trước khi xem phòng',  TRUE),
  ( 3, N'cọc giữ chỗ online',   'coc giu cho online',      'SEVERE', 'LISTING', FALSE, 'SCAM',      N'Đặt cọc online khi chưa xem phòng thực tế', TRUE),
  ( 4, N'đặt cọc qua mạng',     'dat coc qua mang',        'SEVERE', 'LISTING', FALSE, 'SCAM',      N'Biến thể yêu cầu cọc từ xa',               TRUE),
  ( 5, N'chuyển khoản giữ phòng','chuyen khoan giu phong', 'SEVERE', 'LISTING', FALSE, 'SCAM',      N'Giữ phòng bằng chuyển khoản trước',        TRUE),
  ( 6, N'không cần xem phòng',   'khong can xem phong',     'SEVERE', 'LISTING', FALSE, 'SCAM',      N'Dấu hiệu tin ma, phòng không có thật',     TRUE),

  -- ----- Nhóm SPAM / QUẢNG CÁO / TUYỂN DỤNG ĐA CẤP (SEVERE/MILD) -----
  ( 7, N'việc nhẹ lương cao',    'viec nhe luong cao',      'SEVERE', 'BOTH',    FALSE, 'SPAM',      N'Dụ dỗ tuyển dụng lừa đảo trá hình',        TRUE),
  ( 8, N'tuyển cộng tác viên',   'tuyen cong tac vien',     'MILD',   'BOTH',    FALSE, 'SPAM',      N'Spam tuyển CTV không liên quan thuê trọ',  TRUE),
  ( 9, N'kiếm tiền online',      'kiem tien online',        'MILD',   'BOTH',    FALSE, 'SPAM',      N'Spam kiếm tiền tại nhà',                   TRUE),
  (10, N'vay tiền nhanh',        'vay tien nhanh',          'SEVERE', 'BOTH',    FALSE, 'SPAM',      N'Quảng cáo tín dụng đen / vay nóng',        TRUE),
  (11, N'cho vay lãi suất thấp', 'cho vay lai suat thap',   'SEVERE', 'BOTH',    FALSE, 'SPAM',      N'Quảng cáo cho vay trá hình',               TRUE),
  (12, N'đầu tư sinh lời',       'dau tu sinh loi',         'MILD',   'BOTH',    FALSE, 'SPAM',      N'Spam mời gọi đầu tư tài chính',            TRUE),

  -- ----- Nhóm CỜ BẠC / CÁ ĐỘ (SEVERE) -----
  (13, N'cá độ bóng đá',         'ca do bong da',           'SEVERE', 'BOTH',    FALSE, 'GAMBLING',  N'Quảng cáo cá độ bất hợp pháp',             TRUE),
  (14, N'nhà cái uy tín',        'nha cai uy tin',          'SEVERE', 'BOTH',    FALSE, 'GAMBLING',  N'Spam quảng cáo nhà cái cờ bạc',            TRUE),
  (15, N'lô đề online',          'lo de online',            'SEVERE', 'BOTH',    FALSE, 'GAMBLING',  N'Quảng cáo lô đề trực tuyến',               TRUE),

  -- ----- Nhóm CHẤT CẤM / HÀNG CẤM (SEVERE) -----
  (16, N'bằng cấp giả',          'bang cap gia',            'SEVERE', 'BOTH',    FALSE, 'ILLEGAL',   N'Rao làm giấy tờ, bằng cấp giả',            TRUE),
  (17, N'giấy tờ giả',           'giay to gia',             'SEVERE', 'BOTH',    FALSE, 'ILLEGAL',   N'Làm giả giấy tờ tùy thân',                 TRUE),

  -- ----- Nhóm PHẢN CẢM / MẠI DÂM TRÁ HÌNH (SEVERE) -----
  (18, N'gái gọi',              'gai goi',                 'SEVERE', 'BOTH',    FALSE, 'ADULT',     N'Nội dung mại dâm trá hình',                TRUE),
  (19, N'massage kích dục',      'massage kich duc',        'SEVERE', 'BOTH',    FALSE, 'ADULT',     N'Dịch vụ người lớn phản cảm',               TRUE),

  -- ----- Nhóm THÔNG TIN LIÊN HỆ NÉ HỆ THỐNG (MILD) -----
  (20, N'kết bạn zalo',          'ket ban zalo',            'MILD',   'COMMENT', FALSE, 'CONTACT',   N'Chèn liên hệ ngoài né kiểm duyệt bình luận', TRUE);
