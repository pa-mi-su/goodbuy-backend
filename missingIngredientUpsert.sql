BEGIN;

----------------------------------------------------------
-- UPSERT INGREDIENT
----------------------------------------------------------
WITH upsert_ing AS (
    INSERT INTO ingredients (
        canonical_key,
        display_name,
        summary,
        description,
        "function",
        concerns,
        safety_score,
        rating_letter,
        references_count,
        category,
        regulation_notes,
        is_active,
        func_use,
        created_at,
        updated_at
    )
    VALUES (
        'baking-soda-sodium-bicarbonate-e500-ii',
        'Sodium Bicarbonate',
        'Mild alkaline salt used for cleaning and deodorizing.',
        'Sodium bicarbonate (baking soda) is a low-hazard cleaning booster and deodorizer.',
        'pH adjuster; deodorizer; abrasive cleaner',
        'Low hazard; mild irritation possible with direct contact.',
        95.00,
        'A',
        0,
        'cleaning',
        'E500(ii). Widely permitted for household and food use.',
        TRUE,
        'pH adjuster; cleaning booster; deodorizer',
        NOW(),
        NOW()
    )
    ON CONFLICT (canonical_key) DO UPDATE
    SET
        display_name     = EXCLUDED.display_name,
        summary          = EXCLUDED.summary,
        description      = EXCLUDED.description,
        "function"       = EXCLUDED."function",
        concerns         = EXCLUDED.concerns,
        safety_score     = EXCLUDED.safety_score,
        rating_letter    = EXCLUDED.rating_letter,
        references_count = EXCLUDED.references_count,
        category         = EXCLUDED.category,
        regulation_notes = EXCLUDED.regulation_notes,
        is_active        = EXCLUDED.is_active,
        func_use         = EXCLUDED.func_use,
        updated_at       = NOW()
    RETURNING id
)

----------------------------------------------------------
-- ALIASES
----------------------------------------------------------
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT id, 'Sodium Bicarbonate' FROM upsert_ing
ON CONFLICT DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT id, 'Baking Soda' FROM upsert_ing
ON CONFLICT DO NOTHING;

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT id, 'E500(ii)' FROM upsert_ing
ON CONFLICT DO NOTHING;

----------------------------------------------------------
-- TAGS
----------------------------------------------------------
INSERT INTO ingredient_tags (ingredient_id, name)
SELECT id, 'cleaning' FROM upsert_ing
ON CONFLICT DO NOTHING;

INSERT INTO ingredient_tags (ingredient_id, name)
SELECT id, 'low-hazard' FROM upsert_ing
ON CONFLICT DO NOTHING;

----------------------------------------------------------
-- SIGNALS  (V13 schema)
----------------------------------------------------------
INSERT INTO ingredient_signals (
    ingredient_id,
    iarc_group,
    prop65_listed,
    ewg_score,
    eu_prohibited,
    eu_restricted,
    pubchem_mutagen,
    pubchem_reproductive_toxin,
    epa_chronic_toxicity,
    skin_irritant
)
SELECT
    id,
    NULL,
    FALSE,
    1,
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    FALSE
FROM upsert_ing
ON CONFLICT (ingredient_id) DO UPDATE
SET
    ewg_score = EXCLUDED.ewg_score,
    updated_at = NOW();

----------------------------------------------------------
-- RESOLVE the MISSING INGREDIENT REPORT
----------------------------------------------------------
UPDATE ingredient_missing_report
SET
    resolved = TRUE,
    resolved_at = NOW()
WHERE ingredient_name = 'Sodium Bicarbonate'
  AND product_ean = '00033200011309';

COMMIT;