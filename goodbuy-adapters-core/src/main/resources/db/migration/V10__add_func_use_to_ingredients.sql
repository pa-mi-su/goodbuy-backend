-- V10__add_func_use_to_ingredients.sql

-- Align ingredients table with JPA entity: add func_use
ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS func_use TEXT;

-- If you already had data in "function", copy it over once:
UPDATE ingredients
SET func_use = function
WHERE func_use IS NULL
  AND function IS NOT NULL;
