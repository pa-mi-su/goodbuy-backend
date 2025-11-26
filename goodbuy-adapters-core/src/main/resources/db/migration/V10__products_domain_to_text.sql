-- V10__products_domain_to_text.sql
-- Align products.domain with Java String field.

-- Change the domain column from enum product_domain to plain text
ALTER TABLE products
    ALTER COLUMN domain TYPE text
    USING domain::text;

-- Make sure we always have a value; match what the code expects
ALTER TABLE products
    ALTER COLUMN domain SET DEFAULT 'unknown';

-- (Optional but wise) Ensure there are no NULLs hanging around
UPDATE products
SET domain = 'unknown'
WHERE domain IS NULL;
