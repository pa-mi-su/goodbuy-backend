-- V3__product_cache.sql
-- Cache table for external product lookups (EAN-DB, etc.)
-- Stores the full ProductDetailDto JSON for quick reuse.

CREATE TABLE IF NOT EXISTS product_cache (
    gtin         VARCHAR(32) PRIMARY KEY,
    json_payload TEXT        NOT NULL,
    source       VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Keep updated_at in sync on update
CREATE OR REPLACE FUNCTION product_cache_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_product_cache_set_updated_at ON product_cache;

CREATE TRIGGER trg_product_cache_set_updated_at
BEFORE UPDATE ON product_cache
FOR EACH ROW
EXECUTE FUNCTION product_cache_set_updated_at();
