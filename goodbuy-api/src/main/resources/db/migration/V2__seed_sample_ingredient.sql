-- ============================================================================
-- V2__seed_sample_ingredient.sql
-- Creates ingredient_sources if missing, then seeds one ingredient + tags + sources + aliases
-- ============================================================================

-- 0) Ensure the sources table exists (V1 may not have created it)
CREATE TABLE IF NOT EXISTS ingredient_sources (
  ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  url           TEXT   NOT NULL,
  PRIMARY KEY (ingredient_id, url)
);

-- Optional CI helper index for ILIKE filters (safe to keep; no-op if already there)
CREATE INDEX IF NOT EXISTS idx_sources_lower_url
  ON ingredient_sources (lower(url));

-- 1) Ingredient
INSERT INTO ingredients (
  canonical_key, display_name, summary, description, func_use, concerns,
  safety_score, rating_letter, references_count, category, regulation_notes,
  is_active
) VALUES (
  'sodium-bicarbonate',
  'Sodium Bicarbonate',
  'Commonly known as baking soda.',
  'A mild alkaline salt used as a deodorizer and gentle abrasive.',
  'Deodorizer; mild abrasive',
  'Generally considered low risk; overuse may irritate skin.',
  95.00,
  'A',
  5,
  'base',
  'Widely permitted; check product-specific concentrations.',
  TRUE
)
ON CONFLICT (canonical_key) DO NOTHING;

-- 2) Tags
INSERT INTO ingredient_tags (ingredient_id, name)
SELECT i.id, v.name
FROM ingredients i
JOIN (VALUES ('cleaner'), ('deodorizer')) AS v(name) ON TRUE
WHERE i.canonical_key = 'sodium-bicarbonate'
ON CONFLICT (ingredient_id, name) DO NOTHING;

-- 3) Sources
INSERT INTO ingredient_sources (ingredient_id, url)
SELECT i.id, v.url
FROM ingredients i
JOIN (VALUES
  ('https://pubchem.ncbi.nlm.nih.gov/compound/Sodium-bicarbonate'),
  ('https://www.cdc.gov/niosh/npg/npgd0557.html')
) AS v(url) ON TRUE
WHERE i.canonical_key = 'sodium-bicarbonate'
ON CONFLICT (ingredient_id, url) DO NOTHING;

-- 4) Aliases
INSERT INTO ingredient_aliases (ingredient_id, alias, created_at)
SELECT i.id, v.alias, NOW()
FROM ingredients i
JOIN (VALUES
  ('baking soda'),
  ('sodium hydrogen carbonate')
) AS v(alias) ON TRUE
WHERE i.canonical_key = 'sodium-bicarbonate'
ON CONFLICT (ingredient_id, lower(alias)) DO NOTHING;
