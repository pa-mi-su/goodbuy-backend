-- V9__product_domain_schema.sql
-- Legacy setup: product_domain enum + product_domain_config.
-- NOTE: Codebase later converts products.domain to TEXT in V10.
-- IMPORTANT: Runtime code currently queries product_domain_config by binding a String,
-- so product_domain_config.domain MUST be TEXT (not enum) to avoid:
--   operator does not exist: product_domain = character varying

-- 1) Enum type for product domains (legacy; products.domain is enum in V9, converted to TEXT in V10)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        WHERE t.typname = 'product_domain'
    ) THEN
        CREATE TYPE product_domain AS ENUM (
            'UNKNOWN',
            'VITAMINS',
            'CLEANING',
            'BABY',
            'FOOD',
            'OTHER'
        );
    ELSE
        -- Best-effort: ensure VITAMINS exists on older DBs (idempotent-ish)
        BEGIN
            ALTER TYPE product_domain ADD VALUE IF NOT EXISTS 'VITAMINS';
        EXCEPTION
            WHEN duplicate_object THEN
                -- ignore
        END;
    END IF;
END
$$;

-- 2) Add "domain" column to products (legacy enum; converted to TEXT later in V10)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS domain product_domain NOT NULL DEFAULT 'UNKNOWN';

-- 3) Feature-flag table (runtime reads this; must be TEXT to match Java String binding)
CREATE TABLE IF NOT EXISTS product_domain_config (
    -- TEXT (lowercase) to match Java conventions: "vitamins", "cleaning", "unknown", etc.
    domain      TEXT PRIMARY KEY,
    is_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    is_rated    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Optional safety: enforce allowed values (since we're using TEXT)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_config_domain_chk'
    ) THEN
        ALTER TABLE product_domain_config DROP CONSTRAINT product_domain_config_domain_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_config
    ADD CONSTRAINT product_domain_config_domain_chk
    CHECK (domain IN ('unknown','vitamins','cleaning','baby','food','other'));

-- Seed initial config: ONLY VITAMINS enabled/rated.
-- Use DO UPDATE so dev DB reset/re-run always matches current intent.
INSERT INTO product_domain_config (domain, is_enabled, is_rated)
VALUES
    ('vitamins', TRUE, TRUE),
    ('cleaning', FALSE, FALSE),
    ('baby',     FALSE, FALSE),
    ('food',     FALSE, FALSE),
    ('other',    FALSE, FALSE),
    ('unknown',  FALSE, FALSE)
ON CONFLICT (domain) DO UPDATE
SET is_enabled = EXCLUDED.is_enabled,
    is_rated   = EXCLUDED.is_rated,
    updated_at = now();
