-- ─────────────────────────────────────────────
-- PRODUCT EVIDENCE REPORTS (global “already reported”)
-- reasons:
--   - missing_product
--   - unclear_ingredients
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS product_evidence_report (
    id           BIGSERIAL PRIMARY KEY,
    ean          VARCHAR(32) NOT NULL,
    reason       VARCHAR(64) NOT NULL,
    product_name VARCHAR(255),
    brand        VARCHAR(255),
    app_version  VARCHAR(64),
    platform     VARCHAR(32),
    notes        TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One global report per EAN+reason
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_evidence_ean_reason
    ON product_evidence_report (ean, reason);

CREATE INDEX IF NOT EXISTS idx_product_evidence_report_ean
    ON product_evidence_report (ean);

CREATE INDEX IF NOT EXISTS idx_product_evidence_report_created_at
    ON product_evidence_report (created_at);

-- ─────────────────────────────────────────────
-- PRODUCT EVIDENCE REPORTS (global “already reported”)
-- reasons:
--   - missing_product
--   - unclear_ingredients
-- ─────────────────────────────────────────────

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS front_image_s3_url TEXT,
    ADD COLUMN IF NOT EXISTS back_image_s3_url  TEXT;
