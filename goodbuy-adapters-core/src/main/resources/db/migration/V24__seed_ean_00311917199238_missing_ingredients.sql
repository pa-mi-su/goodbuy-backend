-- V24__seed_ean_00311917199238_missing_ingredients.sql
-- Seed / upsert GoodBuy ingredients + sources + citations + ingredient_citations + ingredient_signals
-- Product EAN: 00311917199238
--
-- Source: Slack "Missing ingredient detected" (backend-ingestion)
-- Notes:
--   - We intentionally DO NOT seed: "Contains:", "Less Of", "Soy" (label/allergen artifacts)
--   - We DO seed actual ingredient: Soy Lecithin (E322)

-- ─────────────────────────────────────────────
-- 0) SOURCES (idempotent)
-- ─────────────────────────────────────────────
INSERT INTO sources (name, base_url, notes)
VALUES
  ('PubChem', 'https://pubchem.ncbi.nlm.nih.gov/', 'NIH/NCBI chemical information database.'),
  ('FDA',     'https://www.fda.gov/',              'U.S. Food & Drug Administration.')
ON CONFLICT (name) DO UPDATE
SET
  base_url   = COALESCE(sources.base_url, EXCLUDED.base_url),
  notes      = COALESCE(sources.notes, EXCLUDED.notes),
  updated_at = now();

-- ─────────────────────────────────────────────
-- 0b) CITATIONS (idempotent; URL is unique)
-- ─────────────────────────────────────────────
INSERT INTO citations (source_id, url, title, accessed_at)
SELECT s.id, v.url, v.title, now()
FROM (
  VALUES
    ('PubChem', 'https://pubchem.ncbi.nlm.nih.gov/', 'PubChem (NIH/NCBI)'),
    ('FDA', 'https://www.fda.gov/food/color-additives-information-consumers/color-additives-foods',
            'FDA: Color Additives in Foods'),
    ('FDA', 'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCYellow6&set=ColorAdditives',
            'FDA: Regulatory Status of Color Additives (FD&C Yellow No. 6)'),
    ('FDA', 'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCRed40&set=ColorAdditives',
            'FDA: Regulatory Status of Color Additives (FD&C Red No. 40)'),
    ('FDA', 'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCBlue2&set=ColorAdditives',
            'FDA: Regulatory Status of Color Additives (FD&C Blue No. 2)')
) AS v(source_name, url, title)
JOIN sources s ON s.name = v.source_name
ON CONFLICT (url) DO UPDATE
SET
  title       = COALESCE(citations.title, EXCLUDED.title),
  source_id   = COALESCE(citations.source_id, EXCLUDED.source_id),
  accessed_at = GREATEST(citations.accessed_at, EXCLUDED.accessed_at);

-- ─────────────────────────────────────────────
-- 1) INGREDIENTS: UPSERT CANONICAL INGREDIENTS
-- ─────────────────────────────────────────────
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
    func_use
) VALUES

    ('potassium chloride (e508)',
     'Potassium Chloride',
     'Mineral salt used as a nutrient source and/or salt substitute.',
     'Potassium chloride is used in foods and supplements as a potassium source; it can also be used to replace some sodium chloride in formulations.',
     'Mineral / food additive',
     'Generally low concern at typical dietary levels; people with kidney disease or on certain medications may need to manage potassium intake.',
     88.00, 'B', 0, 'Mineral', NULL, TRUE, 'Mineral'),

    ('calcium silicate (e552)',
     'Calcium Silicate',
     'Anti-caking agent used to keep powders free-flowing.',
     'Calcium silicate is used in foods and supplements as an anti-caking agent and processing aid.',
     'Excipient (anti-caking)',
     'Generally low concern at typical use levels.',
     90.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('carnauba wax (e903)',
     'Carnauba Wax',
     'Glazing agent used for tablet coatings and shiny finishes.',
     'Carnauba wax is a plant-derived wax commonly used as a glaze/coating in foods and supplements.',
     'Excipient (coating/glazing)',
     'Generally low concern at typical use; primarily an inert coating material.',
     92.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('cupric oxide',
     'Cupric Oxide',
     'Mineral compound used as a copper source in supplements/fortification.',
     'Cupric oxide can be used to provide copper in supplements and fortified products.',
     'Trace mineral',
     'Generally low concern at trace doses; excessive copper intake is a different exposure and is not assumed here.',
     82.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('dextrin (e1400)',
     'Dextrin',
     'Carbohydrate used as a binder, carrier, or processing aid.',
     'Dextrins are starch-derived carbohydrates used in foods and supplements to aid processing and texture.',
     'Excipient (binder/carrier)',
     'Generally low concern at typical use levels.',
     88.00, 'B', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('dextrose',
     'Dextrose',
     'Simple sugar (glucose) used as a sweetener or carrier.',
     'Dextrose is glucose; used widely in foods and supplements as a sweetener and excipient.',
     'Food ingredient / excipient',
     'Generally low concern; may affect blood sugar in susceptible individuals at higher amounts.',
     85.00, 'B', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('fd&c blue no. 2 lake',
     'FD&C Blue No. 2 Lake',
     'Color additive (lake pigment) used to color tablets/coatings.',
     'FD&C Blue No. 2 is a certified color additive in the U.S.; “lake” forms are commonly used in solid dosage forms and coatings.',
     'Color additive',
     'Generally low concern for most people; color additives can rarely trigger reactions in sensitive individuals.',
     78.00, 'B', 0, 'Color additive', 'Certified color additive (U.S.); labeling/usage requirements apply.', TRUE, 'Color additive'),

    ('fd&c red no. 40 lake',
     'FD&C Red No. 40 Lake',
     'Color additive (lake pigment) used to color tablets/coatings.',
     'FD&C Red No. 40 is a certified color additive in the U.S.; “lake” forms are commonly used in solid dosage forms and coatings.',
     'Color additive',
     'Generally low concern for most people; dye sensitivities exist in a small subset of individuals.',
     75.00, 'B', 0, 'Color additive', 'Certified color additive (U.S.); labeling/usage requirements apply.', TRUE, 'Color additive'),

    ('fd&c yellow no. 6 lake',
     'FD&C Yellow No. 6 Lake',
     'Color additive (lake pigment) used to color tablets/coatings.',
     'FD&C Yellow No. 6 is a certified color additive in the U.S.; “lake” forms are commonly used in solid dosage forms and coatings.',
     'Color additive',
     'Generally low concern for most people; dye sensitivities exist in a small subset of individuals.',
     75.00, 'B', 0, 'Color additive', 'Certified color additive (U.S.); labeling/usage requirements apply.', TRUE, 'Color additive'),

    ('lutein (e161b)',
     'Lutein',
     'Carotenoid used for nutrition and coloration.',
     'Lutein is a carotenoid found in many plants; used in supplements and as a color additive in some contexts.',
     'Nutrient / colorant',
     'Generally low concern at typical dietary/supplement use levels.',
     88.00, 'B', 0, 'Vitamin / carotenoid', NULL, TRUE, 'Nutrient'),

    ('lycopene (e160d)',
     'Lycopene',
     'Carotenoid used for nutrition and coloration.',
     'Lycopene is a carotenoid pigment found in tomatoes and other fruits; used in supplements and as a color additive in some contexts.',
     'Nutrient / colorant',
     'Generally low concern at typical dietary/supplement use levels.',
     88.00, 'B', 0, 'Vitamin / carotenoid', NULL, TRUE, 'Nutrient'),

    ('magnesium stearate (e572)',
     'Magnesium Stearate',
     'Tablet/capsule lubricant used in manufacturing.',
     'Magnesium stearate is a very common excipient that helps powders flow and prevents sticking during tablet compression.',
     'Excipient (lubricant)',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('phytonadione',
     'Phytonadione (Vitamin K1)',
     'Vitamin used as a nutrient source.',
     'Phytonadione is vitamin K1, used in supplements and fortified foods.',
     'Vitamin',
     'Generally low concern at typical use; interacts with certain anticoagulant medications (dose-dependent).',
     88.00, 'B', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('potassium iodide',
     'Potassium Iodide',
     'Mineral salt used as an iodine source.',
     'Potassium iodide is used to provide iodine in supplements and iodized products.',
     'Mineral',
     'Generally low concern at typical use; excessive iodine intake is a different exposure and is not assumed here.',
     85.00, 'B', 0, 'Mineral', NULL, TRUE, 'Mineral'),

    ('sodium borate',
     'Sodium Borate',
     'Boron-containing salt used as a boron source in some formulations.',
     'Sodium borate (borax/related borates) can appear as a boron source; exact use depends on product type and jurisdiction.',
     'Supplement ingredient / mineral source',
     'Boron intake has upper limits; we do not assume high-dose exposure here. Regulatory positions vary by use and jurisdiction.',
     70.00, 'C', 0, 'Supplement ingredient', 'Use/regulatory status depends on application and jurisdiction.', TRUE, 'Mineral source'),

    ('sodium metavanadate',
     'Sodium Metavanadate',
     'Vanadium-containing compound; sometimes used as a trace element source.',
     'Vanadium compounds may appear in certain supplement/fortification contexts; dosing and form matter.',
     'Trace mineral (vanadium source)',
     'Trace element exposure is highly dose-dependent; we do not assume high-dose exposure here.',
     68.00, 'C', 0, 'Supplement ingredient', NULL, TRUE, 'Trace mineral'),

    ('sodium molybdate',
     'Sodium Molybdate',
     'Trace mineral salt used as a molybdenum source.',
     'Sodium molybdate is used to provide molybdenum in supplements and fortified products.',
     'Trace mineral',
     'Generally low concern at trace doses; excessive intake is a different exposure and is not assumed here.',
     82.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('sodium selenate',
     'Sodium Selenate',
     'Trace mineral salt used as a selenium source.',
     'Sodium selenate can be used as a selenium source in supplements/fortification; dose matters over time.',
     'Trace mineral',
     'Generally low concern at appropriate trace doses; excessive selenium intake can be harmful (not assumed here).',
     82.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('soy lecithin (e322)',
     'Soy Lecithin',
     'Emulsifier used to help ingredients mix and stabilize formulations.',
     'Soy lecithin is a common emulsifier derived from soy; used widely in foods and supplements.',
     'Food additive (emulsifier)',
     'Generally low concern; may be relevant for individuals with soy allergy (allergen context).',
     85.00, 'B', 0, 'Food ingredient', NULL, TRUE, 'Emulsifier')

ON CONFLICT (canonical_key) DO UPDATE
SET
    display_name     = COALESCE(ingredients.display_name, EXCLUDED.display_name),
    summary          = COALESCE(ingredients.summary, EXCLUDED.summary),
    description      = COALESCE(ingredients.description, EXCLUDED.description),
    "function"       = COALESCE(ingredients."function", EXCLUDED."function"),
    concerns         = COALESCE(ingredients.concerns, EXCLUDED.concerns),
    safety_score     = COALESCE(ingredients.safety_score, EXCLUDED.safety_score),
    rating_letter    = COALESCE(ingredients.rating_letter, EXCLUDED.rating_letter),
    references_count = COALESCE(ingredients.references_count, EXCLUDED.references_count),
    category         = COALESCE(ingredients.category, EXCLUDED.category),
    regulation_notes = COALESCE(ingredients.regulation_notes, EXCLUDED.regulation_notes),
    is_active        = ingredients.is_active OR EXCLUDED.is_active,
    func_use         = COALESCE(ingredients.func_use, EXCLUDED.func_use),
    updated_at       = NOW();

-- ─────────────────────────────────────────────
-- 1b) INGREDIENT ↔ CITATION LINKS
-- ─────────────────────────────────────────────
WITH map AS (
  SELECT *
  FROM (VALUES
    -- Most compounds: PubChem baseline identity source
    ('potassium chloride (e508)',     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('calcium silicate (e552)',       'https://pubchem.ncbi.nlm.nih.gov/'),
    ('carnauba wax (e903)',           'https://pubchem.ncbi.nlm.nih.gov/'),
    ('cupric oxide',                  'https://pubchem.ncbi.nlm.nih.gov/'),
    ('dextrin (e1400)',               'https://pubchem.ncbi.nlm.nih.gov/'),
    ('dextrose',                      'https://pubchem.ncbi.nlm.nih.gov/'),
    ('lutein (e161b)',                'https://pubchem.ncbi.nlm.nih.gov/'),
    ('lycopene (e160d)',              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('magnesium stearate (e572)',     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('phytonadione',                  'https://pubchem.ncbi.nlm.nih.gov/'),
    ('potassium iodide',              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('sodium borate',                 'https://pubchem.ncbi.nlm.nih.gov/'),
    ('sodium metavanadate',           'https://pubchem.ncbi.nlm.nih.gov/'),
    ('sodium molybdate',              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('sodium selenate',               'https://pubchem.ncbi.nlm.nih.gov/'),
    ('soy lecithin (e322)',           'https://pubchem.ncbi.nlm.nih.gov/'),

    -- FD&C dyes: FDA status pages are more directly relevant
    ('fd&c blue no. 2 lake',          'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCBlue2&set=ColorAdditives'),
    ('fd&c red no. 40 lake',          'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCRed40&set=ColorAdditives'),
    ('fd&c yellow no. 6 lake',        'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCYellow6&set=ColorAdditives')
  ) AS m(canonical_key, citation_url)
)
INSERT INTO ingredient_citations (ingredient_id, citation_id)
SELECT i.id, c.id
FROM map m
JOIN ingredients i ON i.canonical_key = m.canonical_key
JOIN citations  c ON c.url = m.citation_url
ON CONFLICT (ingredient_id, citation_id) DO NOTHING;

-- ─────────────────────────────────────────────
-- 2) INGREDIENT SIGNALS (conservative defaults)
-- ─────────────────────────────────────────────
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
    m.iarc_group,
    m.prop65_listed,
    m.ewg_score,
    m.eu_prohibited,
    m.eu_restricted,
    m.pubchem_mutagen,
    m.pubchem_reproductive_toxin,
    m.epa_chronic_toxicity,
    m.skin_irritant
FROM (
    VALUES
        ('potassium chloride (e508)',     NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('calcium silicate (e552)',       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('carnauba wax (e903)',           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('cupric oxide',                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('dextrin (e1400)',               NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('dextrose',                      NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        ('fd&c blue no. 2 lake',          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('fd&c red no. 40 lake',          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('fd&c yellow no. 6 lake',        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        ('lutein (e161b)',                NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('lycopene (e160d)',              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('magnesium stearate (e572)',     NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('phytonadione',                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('potassium iodide',              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('sodium borate',                 NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('sodium metavanadate',           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('sodium molybdate',              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('sodium selenate',               NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('soy lecithin (e322)',           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)
) AS m(
    canonical_key,
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
JOIN ingredients i ON i.canonical_key = m.canonical_key
ON CONFLICT (ingredient_id) DO UPDATE
SET
    iarc_group                 = COALESCE(ingredient_signals.iarc_group, EXCLUDED.iarc_group),
    prop65_listed              = ingredient_signals.prop65_listed OR EXCLUDED.prop65_listed,
    ewg_score                  = COALESCE(ingredient_signals.ewg_score, EXCLUDED.ewg_score),
    eu_prohibited              = ingredient_signals.eu_prohibited OR EXCLUDED.eu_prohibited,
    eu_restricted              = ingredient_signals.eu_restricted OR EXCLUDED.eu_restricted,
    pubchem_mutagen            = ingredient_signals.pubchem_mutagen OR EXCLUDED.pubchem_mutagen,
    pubchem_reproductive_toxin = ingredient_signals.pubchem_reproductive_toxin OR EXCLUDED.pubchem_reproductive_toxin,
    epa_chronic_toxicity       = ingredient_signals.epa_chronic_toxicity OR EXCLUDED.epa_chronic_toxicity,
    skin_irritant              = ingredient_signals.skin_irritant OR EXCLUDED.skin_irritant,
    updated_at                 = NOW();
