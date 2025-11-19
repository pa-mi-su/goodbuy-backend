CREATE TABLE IF NOT EXISTS product_missing_report (
    id           BIGSERIAL PRIMARY KEY,
    ean          VARCHAR(32)  NOT NULL,
    product_name VARCHAR(255),
    brand        VARCHAR(255),
    app_version  VARCHAR(64),
    platform     VARCHAR(32),
    notes        TEXT,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_product_missing_report_ean
    ON product_missing_report (ean);

CREATE INDEX IF NOT EXISTS idx_product_missing_report_created_at
    ON product_missing_report (created_at);
