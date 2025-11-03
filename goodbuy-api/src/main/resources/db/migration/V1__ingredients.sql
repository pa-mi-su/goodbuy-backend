-- V1__ingredients.sql
-- Minimal Ingredient Knowledge Base schema (PostgreSQL)
-- Flyway will apply this when a DataSource is present and spring.flyway.enabled=true

-- ─────────────────────────────────────────────────────────────────────────────
-- Ingredients (canonical records)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredients (
    id           BIGSERIAL PRIMARY KEY,
    -- canonical machine key, slug-like (e.g., "sodium-benzoate")
    canonical_key TEXT        NOT NULL UNIQUE,
    -- human display name (e.g., "Sodium Benzoate")
    display_name  TEXT        NOT NULL,
    -- short description / what-is-it
    description   TEXT,
    -- typical function (e.g., "preservative", "surfactant", "fragrance")
    func          TEXT,
    -- short concerns summary (e.g., "possible sensitizer")
    concerns      TEXT,
    -- arbitrary extra data (links, scores, references)
    meta          JSONB       NOT NULL DEFAULT '{}'::jsonb,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Helpful index for case-insensitive lookups by canonical key
CREATE INDEX IF NOT EXISTS idx_ingredients_key_ci
    ON ingredients ((lower(canonical_key)));

-- ─────────────────────────────────────────────────────────────────────────────
-- Aliases (name variants, languages, misspellings)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_aliases (
    id            BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT      NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    alias         TEXT        NOT NULL,
    locale        VARCHAR(8)  NOT NULL DEFAULT 'en',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- prevent duplicates per-ingredient irrespective of case
    CONSTRAINT uq_alias_per_ing UNIQUE (ingredient_id, alias)
);

-- Case-insensitive search accelerator on alias
CREATE INDEX IF NOT EXISTS idx_alias_ci
    ON ingredient_aliases ((lower(alias)));

-- ─────────────────────────────────────────────────────────────────────────────
-- Tags (lightweight labels) and mapping
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_tags (
    id    BIGSERIAL PRIMARY KEY,
    code  TEXT NOT NULL UNIQUE,  -- e.g., "preservative", "fragrance", "sulfate"
    label TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ingredient_tag_map (
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    tag_id        BIGINT NOT NULL REFERENCES ingredient_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (ingredient_id, tag_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Trivial seed (safe to keep; idempotent via ON CONFLICT)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ingredient_tags (code, label) VALUES
    ('fragrance',   'Fragrance'),
    ('preservative','Preservative'),
    ('surfactant',  'Surfactant'),
    ('acid',        'Acid')
ON CONFLICT (code) DO NOTHING;
