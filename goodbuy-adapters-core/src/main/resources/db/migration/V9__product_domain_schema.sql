-- 1) Enum type for product domains (what GoodBuy can rate)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        WHERE t.typname = 'product_domain'
    ) THEN
        CREATE TYPE product_domain AS ENUM (
            'UNKNOWN',
            'CLEANING',
            'BABY',
            'FOOD',
            'OTHER'
        );
    END IF;
END
$$;

-- 2) Add "domain" column to products (what domain this product belongs to)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS domain product_domain NOT NULL DEFAULT 'UNKNOWN';

-- 3) Table to control which domains are enabled / rated
--    This is where you flip CLEANING / BABY / FOOD on or off.
CREATE TABLE IF NOT EXISTS product_domain_config (
    domain      product_domain PRIMARY KEY,
    is_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    is_rated    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed initial config:
-- ONLY CLEANING is enabled + rated.
-- The others exist but are "off" for now.
INSERT INTO product_domain_config (domain, is_enabled, is_rated)
VALUES
    ('CLEANING', TRUE, TRUE),
    ('BABY',     FALSE, FALSE),
    ('FOOD',     FALSE, FALSE),
    ('OTHER',    FALSE, FALSE),
    ('UNKNOWN',  FALSE, FALSE)
ON CONFLICT (domain) DO NOTHING;
