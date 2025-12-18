--V16__add_missing_ingredient_resolved.sql

ALTER TABLE ingredient_missing_report
ADD COLUMN IF NOT EXISTS resolved BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ingredient_missing_report
ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_imr_resolved
    ON ingredient_missing_report (resolved);

