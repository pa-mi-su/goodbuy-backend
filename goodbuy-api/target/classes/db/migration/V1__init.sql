-- ============================================================================
-- V1 -- Minimal baseline schema for GoodBuy Ingredients (PostgreSQL)
-- Tables: ingredients, ingredient_tags (element collection), ingredient_aliases
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- ingredients
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredients (
  id                BIGSERIAL PRIMARY KEY,
  canonical_key     TEXT        NOT NULL UNIQUE,
  display_name      TEXT        NOT NULL,

  -- Core fields we agreed on
  summary           TEXT,
  description       TEXT,
  func_use          TEXT,
  concerns          TEXT,
  safety_score      NUMERIC(5,2),
  rating_letter     CHAR(1),
  references_count  INTEGER,
  category          TEXT,
  regulation_notes  TEXT,
  is_active         BOOLEAN     NOT NULL DEFAULT TRUE,

  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Helpful case-insensitive lookup indexes
CREATE INDEX IF NOT EXISTS idx_ingredients_lcanon
  ON ingredients (lower(canonical_key));
CREATE INDEX IF NOT EXISTS idx_ingredients_ldisplay
  ON ingredients (lower(display_name));

-- ─────────────────────────────────────────────────────────────────────────────
-- ingredient_tags  (ElementCollection<String> tags)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_tags (
  ingredient_id  BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  name           TEXT   NOT NULL,
  PRIMARY KEY (ingredient_id, name)
);

-- Optional helper index for case-insensitive tag filters
CREATE INDEX IF NOT EXISTS idx_tags_lower_name
  ON ingredient_tags (lower(name));

-- ─────────────────────────────────────────────────────────────────────────────
-- ingredient_aliases  (Entity table; matches IngredientAlias with locale)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_aliases (
  id             BIGSERIAL PRIMARY KEY,
  ingredient_id  BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  alias          TEXT   NOT NULL,
  locale         TEXT   NOT NULL DEFAULT 'en',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Prevent duplicate aliases per ingredient (case-insensitive) via unique index
CREATE UNIQUE INDEX IF NOT EXISTS ux_alias_per_ing_ci
  ON ingredient_aliases (ingredient_id, lower(alias));

-- Fast case-insensitive search by alias
CREATE INDEX IF NOT EXISTS idx_aliases_lower_alias
  ON ingredient_aliases (lower(alias));
