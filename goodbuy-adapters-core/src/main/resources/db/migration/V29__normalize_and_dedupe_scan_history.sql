ALTER TABLE scan_history
    DROP CONSTRAINT IF EXISTS ux_scan_history_user_ean;

ALTER TABLE scan_history
    ADD COLUMN IF NOT EXISTS normalized_ean VARCHAR(32);

UPDATE scan_history
SET normalized_ean = CASE
    WHEN regexp_replace(coalesce(ean, ''), '\D+', '', 'g') ~ '^\d{14}$'
        THEN regexp_replace(ean, '\D+', '', 'g')
    WHEN regexp_replace(coalesce(ean, ''), '\D+', '', 'g') ~ '^\d{13}$'
        THEN '0' || regexp_replace(ean, '\D+', '', 'g')
    WHEN regexp_replace(coalesce(ean, ''), '\D+', '', 'g') ~ '^\d{12}$'
        THEN '00' || regexp_replace(ean, '\D+', '', 'g')
    ELSE ean
END;

DELETE FROM scan_history newer
USING scan_history older
WHERE newer.user_id = older.user_id
  AND newer.normalized_ean = older.normalized_ean
  AND (
        newer.scanned_at > older.scanned_at
        OR (newer.scanned_at = older.scanned_at AND newer.id > older.id)
      );

UPDATE scan_history
SET ean = normalized_ean
WHERE normalized_ean IS NOT NULL;

ALTER TABLE scan_history
    DROP COLUMN IF EXISTS normalized_ean;

ALTER TABLE scan_history
    ADD CONSTRAINT ux_scan_history_user_ean
    UNIQUE (user_id, ean);
