-- V17__seed_signals_methylisothiazolinone.sql
-- Seed real hazard/regulatory signals for methylisothiazolinone.
-- Safe to re-run: ON CONFLICT (ingredient_id) DO UPDATE.

BEGIN;

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
    i.id           AS ingredient_id,
    NULL           AS iarc_group,                 -- not IARC classified
    FALSE          AS prop65_listed,              -- not on CA Prop 65
    7              AS ewg_score,                  -- EWG ~moderate hazard
    FALSE          AS eu_prohibited,              -- not totally banned
    TRUE           AS eu_restricted,              -- strong EU restrictions
    FALSE          AS pubchem_mutagen,            -- main concern = sensitization
    FALSE          AS pubchem_reproductive_toxin, -- not a known reprotoxin
    FALSE          AS epa_chronic_toxicity,       -- simplified model
    TRUE           AS skin_irritant               -- strong contact allergen
FROM ingredients i
WHERE i.canonical_key = 'methylisothiazolinone'
ON CONFLICT (ingredient_id) DO UPDATE SET
    iarc_group                = EXCLUDED.iarc_group,
    prop65_listed             = EXCLUDED.prop65_listed,
    ewg_score                 = EXCLUDED.ewg_score,
    eu_prohibited             = EXCLUDED.eu_prohibited,
    eu_restricted             = EXCLUDED.eu_restricted,
    pubchem_mutagen           = EXCLUDED.pubchem_mutagen,
    pubchem_reproductive_toxin= EXCLUDED.pubchem_reproductive_toxin,
    epa_chronic_toxicity      = EXCLUDED.epa_chronic_toxicity,
    skin_irritant             = EXCLUDED.skin_irritant;

COMMIT;
