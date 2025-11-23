-- V7__extend_favorite_product_snapshot.sql
-- Extend favorite_product with snapshot fields for UI:
--   product_name, brand, rating_letter, safety_score

ALTER TABLE favorite_product
    ADD COLUMN IF NOT EXISTS product_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS brand          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rating_letter  VARCHAR(4),
    ADD COLUMN IF NOT EXISTS safety_score   DOUBLE PRECISION;
