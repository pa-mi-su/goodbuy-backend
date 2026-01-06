-- V21__seed_ean_00658010113663_missing_ingredients.sql
-- Seed / upsert GoodBuy ingredients + sources + citations + ingredient_citations + ingredient_signals
-- Product EAN: 00658010113663
--
-- Source: Slack "Missing ingredient detected" (backend-ingestion)
-- Strategy: conservative, evidence-based summaries; avoid over-claiming.
-- Trustworthy seed rules:
--   - Whole foods => USDA FoodData Central (identity / common food use)
--   - Enzymes / supplement-like compounds => PubChem (identity)
--   - Probiotics / cultures => EFSA QPS (safety baseline framework)
--
-- NOTE: We intentionally DO NOT seed the non-ingredient line "Less than 2% of:".

-- ─────────────────────────────────────────────
-- 0) SOURCES (idempotent)
-- ─────────────────────────────────────────────
INSERT INTO sources (name, base_url, notes)
VALUES
  ('USDA FoodData Central', 'https://fdc.nal.usda.gov/', 'USDA food composition database.'),
  ('PubChem',               'https://pubchem.ncbi.nlm.nih.gov/', 'NIH/NCBI chemical information database.'),
  ('EFSA',                  'https://www.efsa.europa.eu/', 'European Food Safety Authority (QPS framework, etc.).')
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
    ('USDA FoodData Central', 'https://fdc.nal.usda.gov/', 'USDA FoodData Central'),
    ('PubChem',               'https://pubchem.ncbi.nlm.nih.gov/', 'PubChem (NIH/NCBI)'),
    ('EFSA',                  'https://www.efsa.europa.eu/en/topics/topic/qualified-presumption-safety-qps',
                              'EFSA: Qualified presumption of safety (QPS)')
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
    -- Whole foods (low concern)
    ('apples',
     'Organic Apple',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Apple is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; may trigger reactions in individuals with specific fruit/pollen-related sensitivities.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('beet',
     'Organic Beet',
     'Whole vegetable ingredient used for flavor, color, and nutrition.',
     'Beet is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; may cause harmless urine/stool color changes (“beeturia”) in some individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('broccoli',
     'Organic Broccoli',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Broccoli is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; cruciferous vegetables can cause GI discomfort in sensitive individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('carrots',
     'Organic Carrot',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Carrot is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; may trigger reactions in individuals with specific pollen/food sensitivities.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('spinach',
     'Organic Spinach',
     'Leafy green ingredient used for flavor and nutrition.',
     'Spinach is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; high-oxalate foods may be a concern for some people prone to kidney stones.',
     93.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('tomatoes',
     'Organic Tomato',
     'Whole fruit/vegetable ingredient used for flavor and nutrition.',
     'Tomato is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; may aggravate reflux/heartburn in some individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('strawberry',
     'Organic Strawberry',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Strawberry is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; strawberries can be allergenic for some individuals.',
     92.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('blackberry',
     'Organic Blackberry',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Blackberry is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; berries can be allergenic for some individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('blueberry',
     'Organic Blueberry',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Blueberry is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; rare allergy possible.',
     96.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('raspberry',
     'Organic Raspberry',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Raspberry is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; rare allergy possible.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('organic tart cherry',
     'Organic Tart Cherry',
     'Whole fruit ingredient used for flavor and nutrition.',
     'Tart cherry is a food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; rare fruit allergy possible.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('bell peppers',
     'Organic Green Bell Pepper',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Bell pepper is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; nightshade sensitivity is uncommon but possible.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('brussels sprouts',
     'Organic Brussels Sprout',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Brussels sprouts are a common food ingredient. In typical food use they are generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; may cause GI discomfort in sensitive individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('ginger',
     'Organic Ginger',
     'Spice/root ingredient used for flavor.',
     'Ginger is widely used in foods. In typical dietary amounts it is generally considered low concern.',
     'Food ingredient',
     'Low concern at food levels; higher supplemental intakes are a different exposure and are not assumed here.',
     92.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('garlic',
     'Organic Garlic',
     'Flavoring ingredient used in foods.',
     'Garlic is widely used in foods. In typical dietary amounts it is generally considered low concern.',
     'Food ingredient',
     'Low concern at food levels; can cause GI irritation in some individuals; allergy is possible.',
     92.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('green onion',
     'Organic Green Onion',
     'Allium vegetable used for flavor.',
     'Green onion is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; alliums can cause GI discomfort in some individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('parsley',
     'Organic Parsley',
     'Herb used for flavor.',
     'Parsley is a common culinary herb. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern at food levels; concentrated extracts are a different exposure and are not assumed here.',
     96.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('cauliflower',
     'Organic Cauliflower',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Cauliflower is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; cruciferous vegetables can cause GI discomfort in sensitive individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('red cabbage',
     'Organic Red Cabbage',
     'Whole vegetable ingredient used for flavor, color, and nutrition.',
     'Red cabbage is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; cruciferous vegetables can cause GI discomfort in sensitive individuals.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('kale',
     'Organic Kale',
     'Leafy green ingredient used for nutrition.',
     'Kale is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; high-oxalate foods may be a concern for some people prone to kidney stones.',
     93.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('pepper',
     'Organic Red Pepper',
     'Whole vegetable ingredient used for flavor and nutrition.',
     'Red pepper is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; nightshade sensitivity is uncommon but possible.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('celery',
     'Organic Celery',
     'Vegetable ingredient used for flavor and nutrition.',
     'Celery is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Celery can be allergenic for some individuals (more common in certain populations).',
     90.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('asparagus',
     'Organic Asparagus',
     'Vegetable ingredient used for flavor and nutrition.',
     'Asparagus is a common food ingredient. In typical food use it is generally considered low concern.',
     'Food ingredient',
     'Low concern for most people; can cause harmless urine odor changes; rare allergy possible.',
     95.00, 'A', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    ('organic sea kelp',
     'Organic Sea Kelp',
     'Edible seaweed ingredient; iodine content can vary.',
     'Sea kelp is used as a food ingredient. Nutrient content (especially iodine) can vary by species and harvest location.',
     'Food ingredient',
     'Low concern at typical food amounts; individuals with thyroid conditions may be more sensitive to high iodine exposure.',
     85.00, 'B', 0, 'Food ingredient', NULL, TRUE, 'Food ingredient'),

    -- Enzymes (identity via PubChem; keep cautions conservative)
    ('lipase',
     'Lipase',
     'Enzyme that breaks down fats (lipids).',
     'Lipase is used in food processing and supplements to help break down fats.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; occupational exposure is higher-risk than consumer dietary exposure.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('protease (e1101-i)',
     'Protease',
     'Enzyme that breaks down proteins.',
     'Protease enzymes are used in food processing and supplements.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; occupational exposure is higher-risk than consumer dietary exposure.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('acid protease',
     'Acid Protease',
     'Protease enzyme active in acidic conditions.',
     'Acid proteases are used in food processing and enzyme preparations.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; occupational exposure is higher-risk than consumer dietary exposure.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('beta-glucanase',
     'Beta-Glucanase',
     'Enzyme that breaks down beta-glucans (polysaccharides).',
     'Beta-glucanase is used in food and beverage processing (e.g., improving filtration/processing).',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('cellulase',
     'Cellulase',
     'Enzyme that breaks down cellulose.',
     'Cellulase is used as a food-processing enzyme and in enzyme preparations.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('bromelain (e1101-iii)',
     'Bromelain',
     'Proteolytic enzyme preparation commonly derived from pineapple.',
     'Bromelain is used in food processing and supplements; it is a protease mixture.',
     'Enzyme',
     'May cause allergy in sensitive individuals (especially those with pineapple/latex-related allergies).',
     80.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('phytase',
     'Phytase',
     'Enzyme that breaks down phytic acid (phytate).',
     'Phytase is used in food/feed processing to reduce phytate and improve mineral bioavailability.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('lactase',
     'Lactase',
     'Enzyme that breaks down lactose.',
     'Lactase is used in lactose-free food processing and in supplements to help digest lactose.',
     'Enzyme',
     'Generally low concern for most consumers; allergy/sensitization possible in susceptible individuals.',
     90.00, 'A', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('papain (e1101-ii)',
     'Papain',
     'Proteolytic enzyme commonly derived from papaya.',
     'Papain is used in food processing and supplements; it is a protease enzyme preparation.',
     'Enzyme',
     'May cause allergy in sensitive individuals (papaya/latex cross-reactivity is possible).',
     80.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('peptidase',
     'Peptidase',
     'Enzyme class that breaks down peptides/proteins.',
     'Peptidases are enzymes used in food processing and enzyme preparations.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('pectinase',
     'Pectinase',
     'Enzyme that breaks down pectin.',
     'Pectinase is used in food processing (commonly juices/wine) to clarify and improve extraction.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('hemicellulase',
     'Hemicellulase',
     'Enzyme that breaks down hemicellulose.',
     'Hemicellulase enzymes are used in food and beverage processing and enzyme preparations.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    ('xylanase',
     'Xylanase',
     'Enzyme that breaks down xylans (hemicellulose components).',
     'Xylanase is used in food processing (e.g., baking) and enzyme preparations.',
     'Enzyme',
     'Primary realistic concern is sensitization/allergy in susceptible individuals; typical dietary exposure is low.',
     85.00, 'B', 0, 'Food enzyme', NULL, TRUE, 'Enzyme'),

    -- Probiotics / cultures (baseline framework: EFSA QPS)
    ('lactobacillus bulgaricus',
     'Lactobacillus bulgaricus',
     'Lactic-acid bacterium commonly used as a yogurt starter culture.',
     'Lactobacillus delbrueckii subsp. bulgaricus is widely used in fermented foods. This entry assumes typical food exposure.',
     'Microbial culture',
     'Generally low concern for healthy individuals; immunocompromised individuals should be more cautious with live-culture supplements.',
     90.00, 'A', 0, 'Probiotic / culture', NULL, TRUE, 'Microbial culture'),

    ('lactiplantibacillus plantarum',
     'Lactiplantibacillus plantarum',
     'Lactic-acid bacterium used in fermented foods and some probiotic products.',
     'Lactiplantibacillus plantarum (formerly Lactobacillus plantarum) is found in many fermented foods. This entry assumes typical food exposure.',
     'Microbial culture',
     'Generally low concern for healthy individuals; immunocompromised individuals should be more cautious with live-culture supplements.',
     90.00, 'A', 0, 'Probiotic / culture', NULL, TRUE, 'Microbial culture'),

    -- Supplement-like ingredient (identity via PubChem)
    ('coq10',
     'CoQ10',
     'Coenzyme Q10 (ubiquinone), a nutrient-like compound used in dietary supplements.',
     'CoQ10 is involved in cellular energy processes and is commonly used as a supplement ingredient.',
     'Supplement ingredient',
     'Generally well tolerated; GI upset and interactions (e.g., with anticoagulants) have been reported in some users.',
     85.00, 'B', 0, 'Supplement ingredient', NULL, TRUE, 'Supplement ingredient')
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
    -- Whole foods -> USDA FoodData Central
    ('apples',                        'https://fdc.nal.usda.gov/'),
    ('beet',                          'https://fdc.nal.usda.gov/'),
    ('broccoli',                      'https://fdc.nal.usda.gov/'),
    ('carrots',                       'https://fdc.nal.usda.gov/'),
    ('spinach',                       'https://fdc.nal.usda.gov/'),
    ('tomatoes',                      'https://fdc.nal.usda.gov/'),
    ('strawberry',                    'https://fdc.nal.usda.gov/'),
    ('blackberry',                    'https://fdc.nal.usda.gov/'),
    ('blueberry',                     'https://fdc.nal.usda.gov/'),
    ('raspberry',                     'https://fdc.nal.usda.gov/'),
    ('organic tart cherry',           'https://fdc.nal.usda.gov/'),
    ('bell peppers',                  'https://fdc.nal.usda.gov/'),
    ('brussels sprouts',              'https://fdc.nal.usda.gov/'),
    ('ginger',                        'https://fdc.nal.usda.gov/'),
    ('garlic',                        'https://fdc.nal.usda.gov/'),
    ('green onion',                   'https://fdc.nal.usda.gov/'),
    ('parsley',                       'https://fdc.nal.usda.gov/'),
    ('cauliflower',                   'https://fdc.nal.usda.gov/'),
    ('red cabbage',                   'https://fdc.nal.usda.gov/'),
    ('kale',                          'https://fdc.nal.usda.gov/'),
    ('pepper',                        'https://fdc.nal.usda.gov/'),
    ('celery',                        'https://fdc.nal.usda.gov/'),
    ('asparagus',                     'https://fdc.nal.usda.gov/'),
    ('organic sea kelp',              'https://fdc.nal.usda.gov/'),

    -- Enzymes + CoQ10 -> PubChem (identity baseline)
    ('lipase',                        'https://pubchem.ncbi.nlm.nih.gov/'),
    ('protease (e1101-i)',            'https://pubchem.ncbi.nlm.nih.gov/'),
    ('acid protease',                 'https://pubchem.ncbi.nlm.nih.gov/'),
    ('beta-glucanase',                'https://pubchem.ncbi.nlm.nih.gov/'),
    ('cellulase',                     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('bromelain (e1101-iii)',         'https://pubchem.ncbi.nlm.nih.gov/'),
    ('phytase',                       'https://pubchem.ncbi.nlm.nih.gov/'),
    ('lactase',                       'https://pubchem.ncbi.nlm.nih.gov/'),
    ('papain (e1101-ii)',             'https://pubchem.ncbi.nlm.nih.gov/'),
    ('peptidase',                     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('pectinase',                     'https://pubchem.ncbi.nlm.nih.gov/'),
    ('hemicellulase',                 'https://pubchem.ncbi.nlm.nih.gov/'),
    ('xylanase',                      'https://pubchem.ncbi.nlm.nih.gov/'),
    ('coq10',                         'https://pubchem.ncbi.nlm.nih.gov/'),

    -- Probiotics / cultures -> EFSA QPS framework page
    ('lactobacillus bulgaricus',      'https://www.efsa.europa.eu/en/topics/topic/qualified-presumption-safety-qps'),
    ('lactiplantibacillus plantarum', 'https://www.efsa.europa.eu/en/topics/topic/qualified-presumption-safety-qps')
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
        -- canonical_key                     iarc   prop65  ewg   eu_proh eu_rest mut   repro  epa_chronic skin_irrit
        ('apples',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('beet',                             NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('broccoli',                         NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('carrots',                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('spinach',                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('tomatoes',                         NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('strawberry',                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('blackberry',                       NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('blueberry',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('raspberry',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('organic tart cherry',              NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('bell peppers',                     NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('brussels sprouts',                 NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('ginger',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('garlic',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('green onion',                      NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('parsley',                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('cauliflower',                      NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('red cabbage',                      NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('kale',                             NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('pepper',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('celery',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('asparagus',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('organic sea kelp',                 NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        ('lipase',                           NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('protease (e1101-i)',               NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('acid protease',                    NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('beta-glucanase',                   NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('cellulase',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('bromelain (e1101-iii)',            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('phytase',                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('lactase',                          NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('papain (e1101-ii)',                NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('peptidase',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('pectinase',                        NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('hemicellulase',                    NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('xylanase',                         NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        ('lactobacillus bulgaricus',         NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
        ('lactiplantibacillus plantarum',    NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),

        ('coq10',                            NULL::SMALLINT, FALSE, NULL::SMALLINT, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)
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
