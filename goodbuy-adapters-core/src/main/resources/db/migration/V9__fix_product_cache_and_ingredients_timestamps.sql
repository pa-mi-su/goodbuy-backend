-- V9__fix_product_cache_and_ingredients_timestamps.sql

-- 1) Align product_cache with JPA entity: add created_at/updated_at
ALTER TABLE product_cache
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2) Align ingredients with JPA entity: add created_at
ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
