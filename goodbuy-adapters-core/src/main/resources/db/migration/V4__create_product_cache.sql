CREATE TABLE IF NOT EXISTS product_cache (
    gtin            VARCHAR(32) PRIMARY KEY,
    json_payload    TEXT NOT NULL,
    source          VARCHAR(64),
    stored_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
