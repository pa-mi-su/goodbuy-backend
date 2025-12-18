-- V14__add_product_scores.sql

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS safety_score   NUMERIC(4,2),
    ADD COLUMN IF NOT EXISTS rating_letter VARCHAR(4);

