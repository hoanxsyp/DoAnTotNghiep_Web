-- Remove obsolete listing edit audit table after the edit-history feature was removed.
-- V1 stays immutable for existing databases; this forward migration keeps old and fresh
-- schemas aligned.
DROP TABLE IF EXISTS listing_edit_histories;
