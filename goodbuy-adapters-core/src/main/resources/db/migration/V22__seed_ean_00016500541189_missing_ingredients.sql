-- V22__seed_ean_00016500541189_missing_ingredients.sql
-- Seed / upsert GoodBuy ingredients + sources + citations + ingredient_citations + ingredient_signals
-- Product EAN: 00016500541189
--
-- Source: Slack "Missing ingredient detected" (backend-ingestion)
-- Strategy: conservative, evidence-based summaries; avoid over-claiming.
-- Trustworthy seed rules:
--   - Chemical vitamins/minerals/excipients/additives => PubChem (identity baseline)
--   - FD&C color additives => FDA color additives information / status pages
--
-- NOTE: We intentionally DO NOT seed the non-ingredient line "Less than 2% of:".

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
    ('FDA',     'https://www.fda.gov/food/color-additives-information-consumers/color-additives-foods',
                'FDA: Color Additives in Foods'),
    ('FDA',     'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCYellow5&set=ColorAdditives',
                'FDA: Regulatory Status of Color Additives (FD&C Yellow No. 5)')
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
    ('calcium carbonate (ci pigment white 18, e170-i)',
     'Calcium Carbonate',
     'Mineral compound used as a calcium source and/or white colorant.',
     'Calcium carbonate is widely used in foods and supplements as a calcium source; it is also used as a color additive in some contexts.',
     'Mineral / excipient',
     'Generally low concern at typical dietary/supplement use; excessive intake can contribute to GI upset or elevated calcium in susceptible individuals.',
     88.00, 'B', 0, 'Supplement ingredient', NULL, TRUE, 'Mineral / excipient'),

    ('magnesium oxide (e530)',
     'Magnesium Oxide',
     'Mineral compound used as a magnesium source and/or anti-caking agent.',
     'Magnesium oxide is used in supplements and as a food additive (anti-caking/processing aid) in some contexts.',
     'Mineral / excipient',
     'Generally low concern at typical use; higher supplemental intakes can cause GI effects (e.g., diarrhea).',
     88.00, 'B', 0, 'Supplement ingredient', NULL, TRUE, 'Mineral / excipient'),

    ('microcrystalline cellulose (e460-i)',
     'Microcrystalline Cellulose',
     'Purified cellulose used as a binder, bulking agent, or tablet/capsule excipient.',
     'Microcrystalline cellulose is a common excipient in tablets/capsules and is generally considered low concern as consumed.',
     'Excipient (binder/bulking)',
     'Low concern for most people; may cause GI discomfort in sensitive individuals at higher intakes.',
     92.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('ascorbic acid (e300)',
     'Ascorbic Acid (Vitamin C)',
     'Vitamin used as a nutrient and antioxidant.',
     'Ascorbic acid is vitamin C; used for nutrition and as an antioxidant.',
     'Vitamin / antioxidant',
     'Generally low concern at typical use; high supplemental doses may cause GI upset in some individuals.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('dicalcium phosphate (e341-ii)',
     'Dicalcium Phosphate',
     'Mineral salt used as a calcium/phosphate source and excipient.',
     'Dicalcium phosphate is commonly used in supplements and as an excipient (filler) in tablets.',
     'Mineral / excipient',
     'Generally low concern at typical use; excessive intake may not be appropriate for some individuals with kidney disease or specific medical conditions.',
     88.00, 'B', 0, 'Supplement ingredient', NULL, TRUE, 'Mineral / excipient'),

    ('maltodextrin',
     'Maltodextrin',
     'Carbohydrate ingredient used as a carrier, bulking agent, or processing aid.',
     'Maltodextrin is a common food/supplement excipient used to carry flavors or vitamins and improve texture.',
     'Excipient (carrier)',
     'Generally low concern at typical use; may affect blood sugar in susceptible individuals at higher amounts.',
     85.00, 'B', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('dl-aipha-tocopheryi acetate',
     'dl-Alpha-Tocopheryl Acetate (Vitamin E)',
     'Vitamin E form used as a nutrient and antioxidant.',
     'dl-Alpha-tocopheryl acetate is a common vitamin E form used in supplements and fortified foods.',
     'Vitamin / antioxidant',
     'Generally low concern at typical use; very high supplemental doses are a different exposure and are not assumed here.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('beta-carotene',
     'Beta-Carotene',
     'Provitamin A carotenoid used for nutrition and coloration.',
     'Beta-carotene is converted to vitamin A in the body and is also used as a color additive in some foods.',
     'Nutrient / colorant',
     'Generally low concern at typical dietary levels; high supplemental intakes are a different exposure and are not assumed here.',
     88.00, 'B', 0, 'Vitamin / carotenoid', NULL, TRUE, 'Nutrient'),

    ('biotin',
     'Biotin (Vitamin B7)',
     'B-vitamin used as a nutrient.',
     'Biotin is vitamin B7, commonly included in multivitamins.',
     'Vitamin',
     'Generally low concern; note that high-dose biotin supplements can interfere with some laboratory tests (dose-dependent).',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('vitamin d3 (cholecalciferol)',
     'Cholecalciferol (Vitamin D3)',
     'Vitamin used for calcium/phosphate balance and bone health.',
     'Vitamin D3 is commonly used in supplements; intake should be appropriate to avoid excessive dosing over time.',
     'Vitamin',
     'Generally low concern at typical use; excessive chronic intake can be harmful (hypercalcemia risk).',
     85.00, 'B', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('chromium chloride',
     'Chromium Chloride',
     'Trace mineral salt used as a chromium source.',
     'Chromium salts can be used in supplements to provide trace chromium.',
     'Trace mineral',
     'Generally low concern at typical trace doses; excessive supplemental intake is a different exposure and is not assumed here.',
     85.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('copper sulfate',
     'Copper Sulfate',
     'Mineral salt used as a copper source.',
     'Copper salts can be used to provide trace copper in supplements.',
     'Trace mineral',
     'Generally low concern at trace doses; higher intakes may cause GI upset and are not assumed here.',
     82.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('cross-linked sodium carboxymethyl cellulose (e468)',
     'Croscarmellose Sodium',
     'Tablet disintegrant (helps tablets break apart).',
     'Croscarmellose sodium (cross-linked cellulose derivative) is a common excipient used to aid tablet disintegration.',
     'Excipient (disintegrant)',
     'Generally low concern at typical use.',
     92.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('cyanocobalamin (vitamin b12)',
     'Cyanocobalamin (Vitamin B12)',
     'B-vitamin used as a nutrient.',
     'Cyanocobalamin is a common vitamin B12 form used in supplements and fortified foods.',
     'Vitamin',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('d-calcium pantothenate',
     'D-Calcium Pantothenate (Vitamin B5)',
     'B-vitamin salt used as a nutrient source.',
     'D-calcium pantothenate is a common vitamin B5 form used in supplements.',
     'Vitamin',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)',
     'FD&C Blue No. 1 (Brilliant Blue FCF)',
     'Synthetic color additive used to color foods and supplements.',
     'Brilliant Blue FCF is a certified color additive used in foods, drugs, and cosmetics in the U.S.',
     'Color additive',
     'Generally low concern for most people; color additives can rarely trigger reactions in sensitive individuals.',
     80.00, 'B', 0, 'Color additive', 'Regulatory status and permitted uses vary by jurisdiction.', TRUE, 'Color additive'),

    ('fd&c yellow #5 (tartrazine) aluminum lake',
     'FD&C Yellow No. 5 (Tartrazine) Aluminum Lake',
     'Color additive (lake pigment) used to color tablets and coatings.',
     'Tartrazine aluminum lake is a form of FD&C Yellow No. 5 used to color products; lakes are commonly used in tablets/coatings.',
     'Color additive',
     'Can cause reactions in a small subset of sensitive individuals (e.g., those with dye sensitivities).',
     75.00, 'B', 0, 'Color additive', 'Certified color additive (U.S.); labeling/usage requirements apply.', TRUE, 'Color additive'),

    ('folic acid',
     'Folic Acid (Vitamin B9)',
     'B-vitamin used as a nutrient.',
     'Folic acid is a common supplemental form of folate used in fortified foods and vitamins.',
     'Vitamin',
     'Generally low concern at typical use; very high intakes can mask B12 deficiency in some contexts (dose-dependent).',
     88.00, 'B', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('gelatin',
     'Gelatin',
     'Protein ingredient used to form capsules or gummies.',
     'Gelatin is derived from collagen and is widely used in foods and supplement dosage forms.',
     'Excipient (capsule/gelling)',
     'Generally low concern; not suitable for certain dietary restrictions.',
     90.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('hypromellose (e464)',
     'Hydroxypropyl Methylcellulose (Hypromellose)',
     'Cellulose derivative used for capsule shells, coatings, and controlled release.',
     'Hypromellose is a common excipient in tablets and vegetarian capsules.',
     'Excipient (coating/capsule)',
     'Generally low concern at typical use.',
     92.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('manganese sulfate',
     'Manganese Sulfate',
     'Trace mineral salt used as a manganese source.',
     'Manganese salts can be used to provide trace manganese in supplements.',
     'Trace mineral',
     'Generally low concern at typical trace doses; excessive supplemental intake is a different exposure and is not assumed here.',
     85.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('niacinamide (vitamin b3)',
     'Niacinamide (Vitamin B3)',
     'B-vitamin used as a nutrient.',
     'Niacinamide is a common form of vitamin B3 used in supplements and fortified foods.',
     'Vitamin',
     'Generally low concern at typical use; high-dose niacinamide is a different exposure and is not assumed here.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('polyethylene glycol (e1521)',
     'Polyethylene Glycol (PEG)',
     'Polymer used as a processing aid, coating, or excipient.',
     'Polyethylene glycol is a common excipient; specific safety depends on molecular weight and use level.',
     'Excipient (polymer)',
     'Generally low concern at typical excipient use; may cause GI effects at higher laxative-type doses (not assumed here).',
     85.00, 'B', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('polyvinylpolypyrrolidone (e1202)',
     'Polyvinylpolypyrrolidone (PVPP)',
     'Polymer used as a stabilizer/processing aid.',
     'PVPP is used in foods and pharmaceuticals as a stabilizer and processing aid.',
     'Excipient (polymer)',
     'Generally low concern at typical use.',
     88.00, 'B', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('pyridoxine hydrochloride (vitamin b6)',
     'Pyridoxine Hydrochloride (Vitamin B6)',
     'B-vitamin used as a nutrient.',
     'Pyridoxine HCl is a common form of vitamin B6 used in supplements.',
     'Vitamin',
     'Generally low concern at typical use; excessive chronic dosing is a different exposure and is not assumed here.',
     88.00, 'B', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('riboflavin (vitamin b2, e101)',
     'Riboflavin (Vitamin B2)',
     'B-vitamin used as a nutrient; also used as a colorant in some contexts.',
     'Riboflavin is vitamin B2 and can impart a yellow color.',
     'Vitamin',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('silicon dioxide (e551)',
     'Silicon Dioxide',
     'Anti-caking agent used to keep powders free-flowing.',
     'Silicon dioxide is commonly used as an anti-caking agent in foods and supplements.',
     'Excipient (anti-caking)',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('disodium selenite',
     'Sodium Selenite',
     'Trace mineral salt used as a selenium source.',
     'Sodium selenite is used to provide selenium in supplements and fortified foods; dose matters over time.',
     'Trace mineral',
     'Generally low concern at appropriate trace doses; excessive selenium intake can be harmful (not assumed here).',
     85.00, 'B', 0, 'Mineral', NULL, TRUE, 'Trace mineral'),

    ('stearic acid',
     'Stearic Acid',
     'Fatty acid used as a lubricant and excipient in tablets.',
     'Stearic acid is a common excipient that helps tablet manufacturing (lubricant).',
     'Excipient (lubricant)',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Excipient', NULL, TRUE, 'Excipient'),

    ('thiamine mononitrate',
     'Thiamine Mononitrate (Vitamin B1)',
     'B-vitamin used as a nutrient source.',
     'Thiamine mononitrate is a common stable form of vitamin B1 used in fortified foods and supplements.',
     'Vitamin',
     'Generally low concern at typical use.',
     90.00, 'A', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('titanium dioxide (ci pigment white 6, e171)',
     'Titanium Dioxide (Color)',
     'White pigment used for coloration and opacity.',
     'Titanium dioxide is used as a pigment; concerns and regulatory positions vary by jurisdiction and application.',
     'Color additive / pigment',
     'Regulatory and risk discussions exist for ingested uses; exposure depends on form and use level.',
     70.00, 'C', 0, 'Color additive', 'Regulatory status varies by jurisdiction and use.', TRUE, 'Color additive'),

    ('vitamin a acetate',
     'Vitamin A Acetate',
     'Vitamin A form used as a nutrient source.',
     'Vitamin A acetate is used in supplements and fortified foods as a vitamin A source.',
     'Vitamin',
     'Generally low concern at typical use; excessive chronic intake can be harmful (not assumed here).',
     85.00, 'B', 0, 'Vitamin', NULL, TRUE, 'Vitamin'),

    ('zinc oxide',
     'Zinc Oxide',
     'Mineral compound used as a zinc source.',
     'Zinc oxide is used in supplements to provide zinc.',
     'Mineral',
     'Generally low concern at typical use; excessive intake can cause GI effects and copper imbalance over time (not assumed here).',
     85.00, 'B', 0, 'Mineral', NULL, TRUE, 'Mineral')
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
-- 1b) INGREDIENT ↔ CITATION LINKS (trustworthy sources)
-- ─────────────────────────────────────────────
WITH map AS (
  SELECT *
  FROM (VALUES
    -- Most chemical ingredients -> PubChem
    ('calcium carbonate (ci pigment white 18, e170-i)', 'https://pubchem.ncbi.nlm.nih.gov/'),
    ('magnesium oxide (e530)',                         'https://pubchem.ncbi.nlm.nih.gov/'),
    ('microcrystalline cellulose (e460-i)',            'https://pubchem.ncbi.nlm.nih.gov/'),
    ('ascorbic acid (e300)',                           'https://pubchem.ncbi.nlm.nih.gov/'),
    ('dicalcium phosphate (e341-ii)',                  'https://pubchem.ncbi.nlm.nih.gov/'),
    ('maltodextrin',                                   'https://pubchem.ncbi.nlm.nih.gov/'),
    ('dl-aipha-tocopheryi acetate',                    'https://pubchem.ncbi.nlm.nih.gov/'),
    ('beta-carotene',                                  'https://pubchem.ncbi.nlm.nih.gov/'),
    ('biotin',                                         'https://pubchem.ncbi.nlm.nih.gov/'),
    ('vitamin d3 (cholecalciferol)',                   'https://pubchem.ncbi.nlm.nih.gov/'),
    ('chromium chloride',                              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('copper sulfate',                                 'https://pubchem.ncbi.nlm.nih.gov/'),
    ('cross-linked sodium carboxymethyl cellulose (e468)','https://pubchem.ncbi.nlm.nih.gov/'),
    ('cyanocobalamin (vitamin b12)',                   'https://pubchem.ncbi.nlm.nih.gov/'),
    ('d-calcium pantothenate',                         'https://pubchem.ncbi.nlm.nih.gov/'),
    ('folic acid',                                     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('gelatin',                                        'https://pubchem.ncbi.nlm.nih.gov/'),
    ('hypromellose (e464)',                            'https://pubchem.ncbi.nlm.nih.gov/'),
    ('manganese sulfate',                              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('niacinamide (vitamin b3)',                       'https://pubchem.ncbi.nlm.nih.gov/'),
    ('polyethylene glycol (e1521)',                    'https://pubchem.ncbi.nlm.nih.gov/'),
    ('polyvinylpolypyrrolidone (e1202)',               'https://pubchem.ncbi.nlm.nih.gov/'),
    ('pyridoxine hydrochloride (vitamin b6)',          'https://pubchem.ncbi.nlm.nih.gov/'),
    ('riboflavin (vitamin b2, e101)',                  'https://pubchem.ncbi.nlm.nih.gov/'),
    ('silicon dioxide (e551)',                         'https://pubchem.ncbi.nlm.nih.gov/'),
    ('disodium selenite',                              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('stearic acid',                                   'https://pubchem.ncbi.nlm.nih.gov/'),
    ('thiamine mononitrate',                           'https://pubchem.ncbi.nlm.nih.gov/'),
    ('titanium dioxide (ci pigment white 6, e171)',    'https://pubchem.ncbi.nlm.nih.gov/'),
    ('vitamin a acetate',                              'https://pubchem.ncbi.nlm.nih.gov/'),
    ('zinc oxide',                                     'https://pubchem.ncbi.nlm.nih.gov/'),

    -- Color additives -> FDA color additive pages (more directly relevant)
    ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)',
                                                     'https://www.fda.gov/food/color-additives-information-consumers/color-additives-foods'),
    ('fd&c yellow #5 (tartrazine) aluminum lake',
                                                     'https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCYellow5&set=ColorAdditives')
  ) AS m(canonical_key, citation_url)
)
INSERT INTO ingredient_citations (ingredient_id, citation_id)
SELECT i.id, c.id
FROM map m
JOIN ingredients i ON i.canonical_key = m.canonical_key
JOIN citations  c ON c.url = m.citation_url
ON CONFLICT (ingredient_id, citation_id) DO NOTHING;

-- ─────────────────────────────────────────────
-- 2) INGREDIENT SIGNALS: UPSERT FLAGS (CONSERVATIVE DEFAULTS)
--    Keep null/false unless we have explicit evidence in our sources.
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
        -- canonical_key                                            iarc   prop65  ewg   eu_proh eu_rest mut   repro  epa_chronic skin_irrit
        ('calcium carbonate (ci pigment white 18, e170-i)',          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('magnesium oxide (e530)',                                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('microcrystalline cellulose (e460-i)',                     NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('ascorbic acid (e300)',                                    NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('dicalcium phosphate (e341-ii)',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('maltodextrin',                                            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('dl-aipha-tocopheryi acetate',                             NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('beta-carotene',                                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('biotin',                                                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('vitamin d3 (cholecalciferol)',                            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('chromium chloride',                                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('copper sulfate',                                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('cross-linked sodium carboxymethyl cellulose (e468)',      NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('cyanocobalamin (vitamin b12)',                            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('d-calcium pantothenate',                                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('folic acid',                                              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('gelatin',                                                 NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('hypromellose (e464)',                                     NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('manganese sulfate',                                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('niacinamide (vitamin b3)',                                NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('polyethylene glycol (e1521)',                             NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('polyvinylpolypyrrolidone (e1202)',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('pyridoxine hydrochloride (vitamin b6)',                   NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('riboflavin (vitamin b2, e101)',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('silicon dioxide (e551)',                                  NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('disodium selenite',                                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('stearic acid',                                            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('thiamine mononitrate',                                    NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('titanium dioxide (ci pigment white 6, e171)',             NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('vitamin a acetate',                                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('zinc oxide',                                              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        -- Dyes: keep conservative; do not set restricted/prohibited flags here without explicit dataset support
        ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)',NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('fd&c yellow #5 (tartrazine) aluminum lake',               NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)
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
