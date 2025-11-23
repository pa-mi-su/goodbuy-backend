-- V8__extend_scan_history_snapshot.sql
-- Make scan_history match ScanHistoryEntity snapshot fields

ALTER TABLE scan_history
    ADD COLUMN IF NOT EXISTS product_id   BIGINT REFERENCES products(id),
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS brand        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scanned_at   TIMESTAMPTZ NOT NULL DEFAULT now();

-- Optional: index for "recent history" query
CREATE INDEX IF NOT EXISTS idx_scan_history_user_scanned_at
    ON scan_history (user_id, scanned_at DESC);
