-- ─────────────────────────────────────────────
-- PRODUCT EVIDENCE REPORTS (global “already reported/in progress”)
--
-- reason:
--   - missing_product
--   - unclear_ingredients
--   - out_of_domain
--
-- status:
--   - REPORTED
--   - IN_PROGRESS
--   - RESOLVED
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS product_evidence_report (
    id                 BIGSERIAL PRIMARY KEY,
    ean                VARCHAR(32)  NOT NULL,
    reason             VARCHAR(64)  NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'REPORTED',

    product_name       VARCHAR(255),
    brand              VARCHAR(255),

    app_version        VARCHAR(64),
    platform           VARCHAR(32),
    notes              TEXT,

    front_image_s3_url TEXT,
    back_image_s3_url  TEXT,

    occurred_at        TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Add missing columns safely (for environments where table already exists)
ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS front_image_s3_url TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS back_image_s3_url  TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'REPORTED';

-- Backfill + harden NOT NULL on status
UPDATE product_evidence_report
SET status = 'REPORTED'
WHERE status IS NULL OR btrim(status) = '';

ALTER TABLE product_evidence_report
    ALTER COLUMN status SET DEFAULT 'REPORTED';

ALTER TABLE product_evidence_report
    ALTER COLUMN status SET NOT NULL;

-- ✅ Enforce dedupe rule: ONE ROW PER (ean, reason)
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_evidence_ean_reason
    ON product_evidence_report (ean, reason);

-- Optional but recommended: speed up status lookups
CREATE INDEX IF NOT EXISTS ix_product_evidence_ean_reason_status
    ON product_evidence_report (ean, reason, status);
