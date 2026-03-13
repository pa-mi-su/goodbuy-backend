-- VXX__create_sources_citations.sql
-- Adds: sources, citations, ingredient_citations
-- Goal: enable “trustworthy seed” inserts (source rows + citation rows + join rows)
-- Safe: idempotent (CREATE IF NOT EXISTS / ADD IF NOT EXISTS) and does not change existing tables.

-- ─────────────────────────────────────────────
-- 1) SOURCES (publisher / authority registry)
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sources (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,   -- e.g., 'PubChem', 'USDA FoodData Central', 'EFSA'
    base_url    TEXT,                           -- optional
    notes       TEXT,                           -- optional
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sources_name
    ON sources (name);

-- ─────────────────────────────────────────────
-- 2) CITATIONS (specific URLs and titles we cite)
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS citations (
    id          BIGSERIAL PRIMARY KEY,
    source_id   BIGINT REFERENCES sources(id) ON DELETE SET NULL,
    url         TEXT NOT NULL UNIQUE,
    title       TEXT,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_citations_source_id
    ON citations (source_id);

-- ─────────────────────────────────────────────
-- 3) INGREDIENT ↔ CITATION JOIN
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ingredient_citations (
    id           BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    citation_id   BIGINT NOT NULL REFERENCES citations(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One citation only once per ingredient
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ux_ingredient_citation_pair'
    ) THEN
        ALTER TABLE ingredient_citations
            ADD CONSTRAINT ux_ingredient_citation_pair UNIQUE (ingredient_id, citation_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_ingredient_citations_ingredient_id
    ON ingredient_citations (ingredient_id);

CREATE INDEX IF NOT EXISTS idx_ingredient_citations_citation_id
    ON ingredient_citations (citation_id);

-- ─────────────────────────────────────────────
-- 4) OPTIONAL: bump updated_at automatically (simple pattern)
--    (No triggers to keep migrations minimal / predictable)
-- ─────────────────────────────────────────────
