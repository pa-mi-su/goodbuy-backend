ALTER TABLE ingredients
    DROP COLUMN IF EXISTS "function";

ALTER TABLE scan_history
    DROP COLUMN IF EXISTS first_scanned_at,
    DROP COLUMN IF EXISTS last_scanned_at,
    DROP COLUMN IF EXISTS scan_count;
