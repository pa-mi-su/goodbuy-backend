CREATE TABLE IF NOT EXISTS products (
    id              BIGSERIAL PRIMARY KEY,
    ean             VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(255),
    brand           VARCHAR(255),
    category        VARCHAR(255),
    description     TEXT,
    primary_image_url TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_ean ON products(ean);
