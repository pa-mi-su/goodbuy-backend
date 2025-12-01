-- V14__seed_initial_ingredient_aliases.sql
-- Seed initial aliases for core ingredients so alias-based matching can be tested.
-- Safe to re-run: uses ON CONFLICT (ingredient_id, alias) DO NOTHING.

BEGIN;

-- Water
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'aqua'
FROM ingredients i
WHERE lower(i.canonical_key) = 'water'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'water (aqua)'
FROM ingredients i
WHERE lower(i.canonical_key) = 'water'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Baking soda / sodium bicarbonate
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'baking soda'
FROM ingredients i
WHERE lower(i.canonical_key) = 'baking soda (sodium bicarbonate, e500-ii)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'sodium bicarbonate'
FROM ingredients i
WHERE lower(i.canonical_key) = 'baking soda (sodium bicarbonate, e500-ii)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'bicarbonate of soda'
FROM ingredients i
WHERE lower(i.canonical_key) = 'baking soda (sodium bicarbonate, e500-ii)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'e500(ii)'
FROM ingredients i
WHERE lower(i.canonical_key) = 'baking soda (sodium bicarbonate, e500-ii)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Glycerin / Glycerol (E422)
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'glycerin'
FROM ingredients i
WHERE lower(i.canonical_key) = 'glycerol (e422)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'glycerine'
FROM ingredients i
WHERE lower(i.canonical_key) = 'glycerol (e422)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'glycerol'
FROM ingredients i
WHERE lower(i.canonical_key) = 'glycerol (e422)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'e422'
FROM ingredients i
WHERE lower(i.canonical_key) = 'glycerol (e422)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Sodium chloride (Salt)
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'sodium chloride'
FROM ingredients i
WHERE lower(i.canonical_key) = 'salt'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'table salt'
FROM ingredients i
WHERE lower(i.canonical_key) = 'salt'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Sodium citrate (E331)
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'sodium citrate'
FROM ingredients i
WHERE lower(i.canonical_key) = 'sodium citrates (e331)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'trisodium citrate'
FROM ingredients i
WHERE lower(i.canonical_key) = 'sodium citrates (e331)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'e331'
FROM ingredients i
WHERE lower(i.canonical_key) = 'sodium citrates (e331)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Potassium hydroxide (E525)
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'potassium hydroxide'
FROM ingredients i
WHERE lower(i.canonical_key) = 'potassium hydroxide (e525)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'caustic potash'
FROM ingredients i
WHERE lower(i.canonical_key) = 'potassium hydroxide (e525)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'koh'
FROM ingredients i
WHERE lower(i.canonical_key) = 'potassium hydroxide (e525)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'e525'
FROM ingredients i
WHERE lower(i.canonical_key) = 'potassium hydroxide (e525)'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Fragrance (generic)
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'parfum'
FROM ingredients i
WHERE lower(i.canonical_key) = 'fragrance'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'perfume'
FROM ingredients i
WHERE lower(i.canonical_key) = 'fragrance'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'fragrance (parfum)'
FROM ingredients i
WHERE lower(i.canonical_key) = 'fragrance'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Laureth-7
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'laureth 7'
FROM ingredients i
WHERE lower(i.canonical_key) = 'laureth-7'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Lauryl Glucoside
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'lauryl glucoside'
FROM ingredients i
WHERE lower(i.canonical_key) = 'lauryl glucoside'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Methylisothiazolinone
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'mi'
FROM ingredients i
WHERE lower(i.canonical_key) = 'methylisothiazolinone'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'mit'
FROM ingredients i
WHERE lower(i.canonical_key) = 'methylisothiazolinone'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- Benzisothiazolinone
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, 'bit'
FROM ingredients i
WHERE lower(i.canonical_key) = 'benzisothiazolinone'
ON CONFLICT (ingredient_id, alias) DO NOTHING;

COMMIT;
