-- V5__add_scan_history.sql
-- Per-user scan history in GoodBuy

CREATE TABLE IF NOT EXISTS scan_history (
    id               BIGSERIAL PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    ean              VARCHAR(32) NOT NULL,

    -- Aggregate history
    first_scanned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_scanned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    scan_count       INT NOT NULL DEFAULT 1
);

-- One row per (user, ean)
ALTER TABLE scan_history
    ADD CONSTRAINT ux_scan_history_user_ean
        UNIQUE (user_id, ean);

-- Fast “recent scans” query for a user
CREATE INDEX IF NOT EXISTS idx_scan_history_user_last_scanned
    ON scan_history (user_id, last_scanned_at DESC);
