CREATE TABLE IF NOT EXISTS ingredient_missing_report (
    id              BIGSERIAL PRIMARY KEY,
    ingredient_name VARCHAR(255) NOT NULL,
    product_ean     VARCHAR(32),
    app_version     VARCHAR(64),
    platform        VARCHAR(32), -- iOS / Android
    notes           TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_imr_ingredient_name
    ON ingredient_missing_report(ingredient_name);

CREATE INDEX IF NOT EXISTS idx_imr_product_ean_created_at
    ON ingredient_missing_report(product_ean, created_at DESC);
