-- =====================================================================================
-- V10__fulltext_index.sql
-- =====================================================================================
-- Muc dich: Tao FULLTEXT index cho tim kiem toan van tren listings(title, description).
--
-- Tai sao tach rieng khoi V1 baseline:
--   FULLTEXT index co chi phi ghi/bao tri cao (moi INSERT/UPDATE title|description phai
--   cap nhat lai bang tu). Tach rieng de danh gia hieu nang doc lap va co the drop/tao
--   lai ma khong dung cham baseline schema.
--
-- Vi sao dung WITH PARSER ngram (tieng Viet):
--   Parser mac dinh cua MySQL tach tu theo khoang trang -> khong phu hop voi dac thu
--   tim kiem tieng Viet (nguoi dung go thieu dau, tim theo cum ky tu con). Parser `ngram`
--   cat chuoi thanh cac token co do dai co dinh, cho phep match theo n-gram ky tu.
--
-- Cau hinh server (BAT BUOC, da set trong docker-compose cua MySQL 8.4):
--   ngram_token_size = 2
--     -> ngram_token_size la bien read-only (chi doc luc khoi dong), KHONG the SET runtime.
--        Phai truyen qua command/my.cnf: `--ngram-token-size=2`.
--     -> Kich thuoc token = 2 ky tu la lua chon can bang cho tieng Viet: du nho de bat
--        cac cum am tiet ngan, du lon de tranh no chi muc va nhieu ket qua rac.
--     -> HE QUA: tu khoa tim kiem ngan hon 2 ky tu se khong match (innodb_ft_min_token_size
--        bi vo hieu hoa khi dung ngram parser; ngram_token_size quyet dinh do dai token).
--   Neu doi ngram_token_size sau khi index da ton tai -> PHAI DROP va tao lai FULLTEXT
--   index, neu khong ket qua tim kiem se sai.
--
-- Cot tham chieu (khop chinh xac V1__baseline_schema.sql, bang listings):
--   title       VARCHAR(150) NOT NULL
--   description  TEXT        NOT NULL
-- =====================================================================================

ALTER TABLE listings
  ADD FULLTEXT INDEX ft_listings_title_description (title, description) WITH PARSER ngram;

-- =====================================================================================
-- KHONG co INSERT / seed du lieu trong migration nay (day la migration thuan DDL index).
-- =====================================================================================
