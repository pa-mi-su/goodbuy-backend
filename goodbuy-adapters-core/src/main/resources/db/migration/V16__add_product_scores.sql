-- V16__add_product_scores.sql
-- Add safety_score and rating_letter to products so GoodBuy
-- can persist real product-level scores (derived from ingredients).

BEGIN;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS safety_score   NUMERIC(4,2),
    ADD COLUMN IF NOT EXISTS rating_letter VARCHAR(4);

COMMIT;
