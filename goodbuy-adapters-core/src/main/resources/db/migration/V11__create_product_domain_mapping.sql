-- V11__create_product_domain_mapping.sql
-- Table-driven rules for mapping products into high-level domains
-- (CLEANING, BABY, FOOD, OTHER, UNKNOWN).

CREATE TABLE product_domain_mapping (
    id              BIGSERIAL PRIMARY KEY,

    -- Target domain for this rule, matches ProductDomainClassifier.Domain
    domain          VARCHAR(32) NOT NULL,

    -- Which field we match against: CATEGORY, TITLE, BRAND, or ALL
    match_field     VARCHAR(32) NOT NULL,

    -- How we match: EQUALS, CONTAINS (v1), REGEX (future)
    match_type      VARCHAR(32) NOT NULL,

    -- Lowercased text pattern to match (e.g. 'laundry detergent', 'dish soap')
    pattern         TEXT NOT NULL,

    -- Lower number = higher priority. First matching rule wins.
    priority        INTEGER NOT NULL DEFAULT 100,

    -- Soft on/off switch for the rule
    active          BOOLEAN NOT NULL DEFAULT TRUE,

    -- Optional notes for human admins
    notes           TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Restrict domain values to our known enum set.
ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_domain_chk
    CHECK (domain IN ('CLEANING', 'BABY', 'FOOD', 'OTHER', 'UNKNOWN'));

-- Restrict match_field to the known fields we support.
ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_match_field_chk
    CHECK (match_field IN ('CATEGORY', 'TITLE', 'BRAND', 'ALL'));

-- Restrict match_type for now (we can expand later).
ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_match_type_chk
    CHECK (match_type IN ('EQUALS', 'CONTAINS'));

-- Basic helper index so we can quickly scan active rules by priority.
CREATE INDEX idx_product_domain_mapping_active_priority
    ON product_domain_mapping (active, priority);
