ALTER TABLE ingredient_missing_report
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'OPEN';

ALTER TABLE ingredient_missing_report
    ADD COLUMN IF NOT EXISTS resolved_canonical_key VARCHAR(255);

ALTER TABLE ingredient_missing_report
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
