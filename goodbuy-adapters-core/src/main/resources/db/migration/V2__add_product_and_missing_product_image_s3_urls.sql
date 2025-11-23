-- Add S3 image URL columns for:
--  1) products: S3-hosted primary image (from EAN-DB or our own)
--  2) product_missing_report: S3-hosted user-submitted front/back photos

-- ─────────────────────────────────────────────
-- products
-- ─────────────────────────────────────────────
-- Keeps existing primary_image_url (whatever we stored before),
-- and adds a dedicated field for the S3 URL we will control.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS primary_image_s3_url TEXT;

-- ─────────────────────────────────────────────
-- product_missing_report
-- ─────────────────────────────────────────────
ALTER TABLE product_missing_report
    ADD COLUMN IF NOT EXISTS front_image_s3_url TEXT,
    ADD COLUMN IF NOT EXISTS back_image_s3_url  TEXT;
