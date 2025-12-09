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
    i.id,
    NULL,        -- no IARC group
    FALSE,       -- not Prop 65
    5,           -- example EWG moderate
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    TRUE         -- skin/eye irritant at high conc
FROM ingredients i
WHERE i.canonical_key = 'laureth-7'
ON CONFLICT (ingredient_id) DO UPDATE SET
    iarc_group                  = EXCLUDED.iarc_group,
    prop65_listed               = EXCLUDED.prop65_listed,
    ewg_score                   = EXCLUDED.ewg_score,
    eu_prohibited               = EXCLUDED.eu_prohibited,
    eu_restricted               = EXCLUDED.eu_restricted,
    pubchem_mutagen             = EXCLUDED.pubchem_mutagen,
    pubchem_reproductive_toxin  = EXCLUDED.pubchem_reproductive_toxin,
    epa_chronic_toxicity        = EXCLUDED.epa_chronic_toxicity,
    skin_irritant               = EXCLUDED.skin_irritant;

COMMIT;
