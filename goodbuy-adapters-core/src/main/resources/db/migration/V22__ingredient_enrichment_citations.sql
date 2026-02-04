-- V22__ingredient_enrichment_citations.sql
-- Introduce normalized citation storage for ingredient enrichment.
-- Tables:
--   sources               (provider registry)
--   citations             (URL + optional title, belongs to a source)
--   ingredient_citations  (join ingredients <-> citations)

CREATE TABLE IF NOT EXISTS sources (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT NOT NULL UNIQUE,
    base_url     TEXT NULL,
    notes        TEXT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS citations (
    id           BIGSERIAL PRIMARY KEY,
    source_id    BIGINT NOT NULL REFERENCES sources(id) ON DELETE RESTRICT,
    url          TEXT NOT NULL UNIQUE,
    title        TEXT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_citations_source_id ON citations(source_id);

CREATE TABLE IF NOT EXISTS ingredient_citations (
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    citation_id   BIGINT NOT NULL REFERENCES citations(id)   ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ingredient_id, citation_id)
);

CREATE INDEX IF NOT EXISTS idx_ingredient_citations_ingredient_id ON ingredient_citations(ingredient_id);
CREATE INDEX IF NOT EXISTS idx_ingredient_citations_citation_id   ON ingredient_citations(citation_id);
