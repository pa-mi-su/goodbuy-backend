-- V11__create_product_domain_mapping.sql
-- Table-driven rules for mapping products into high-level domains
-- (VITAMINS, CLEANING, BABY, FOOD, OTHER, UNKNOWN).

CREATE TABLE IF NOT EXISTS product_domain_mapping (
    id              BIGSERIAL PRIMARY KEY,

    -- Target domain for this rule. Must be one of the known enum names below.
    domain          VARCHAR(32) NOT NULL,

    -- Which field we match against: CATEGORY, TITLE, BRAND, or ALL
    match_field     VARCHAR(32) NOT NULL,

    -- How we match: EQUALS, CONTAINS (v1)
    match_type      VARCHAR(32) NOT NULL,

    -- Lowercased text pattern to match (we normalize inputs in resolver)
    pattern         TEXT NOT NULL,

    -- Lower number = higher priority. First matching rule wins.
    priority        INTEGER NOT NULL DEFAULT 100,

    -- Soft on/off switch for the rule
    active          BOOLEAN NOT NULL DEFAULT TRUE,

    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Domain constraint (idempotent drop/re-add)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_mapping_domain_chk'
    ) THEN
        ALTER TABLE product_domain_mapping DROP CONSTRAINT product_domain_mapping_domain_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_domain_chk
    CHECK (domain IN ('VITAMINS', 'CLEANING', 'BABY', 'FOOD', 'OTHER', 'UNKNOWN'));

-- match_field constraint (idempotent drop/re-add)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_mapping_match_field_chk'
    ) THEN
        ALTER TABLE product_domain_mapping DROP CONSTRAINT product_domain_mapping_match_field_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_match_field_chk
    CHECK (match_field IN ('CATEGORY', 'TITLE', 'BRAND', 'ALL'));

-- match_type constraint (idempotent drop/re-add)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_mapping_match_type_chk'
    ) THEN
        ALTER TABLE product_domain_mapping DROP CONSTRAINT product_domain_mapping_match_type_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_match_type_chk
    CHECK (match_type IN ('EQUALS', 'CONTAINS'));

-- Helper index
CREATE INDEX IF NOT EXISTS idx_product_domain_mapping_active_priority
    ON product_domain_mapping (active, priority);
