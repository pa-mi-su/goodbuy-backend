-- V10__products_domain_to_text.sql
-- Align products.domain with Java String field.
-- Java expects values like: "vitamins", "cleaning", "unknown" (lowercase).

-- Convert enum (or whatever it is) to text.
ALTER TABLE products
    ALTER COLUMN domain TYPE text
    USING domain::text;

-- Normalize to lowercase for consistency with current Java conventions.
UPDATE products
SET domain = lower(domain)
WHERE domain IS NOT NULL;

-- Ensure default matches code expectations
ALTER TABLE products
    ALTER COLUMN domain SET DEFAULT 'unknown';

-- Ensure no NULLs / blanks
UPDATE products
SET domain = 'unknown'
WHERE domain IS NULL OR btrim(domain) = '';
