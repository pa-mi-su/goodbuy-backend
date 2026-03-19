-- V23__seed_missing_ingredient_aliases.sql
-- Adds ingredient_alias rows (idempotent) for the ingredients introduced in:
--   - V21__seed_ean_00658010113663_missing_ingredients.sql
--   - V22__seed_ean_00016500541189_missing_ingredients.sql
--
-- Goal:
--   - Ensure lookup works even when external catalogs / labels / Slack use different spellings,
--     punctuation, capitalization, E-numbers, CI names, or common synonyms.
--   - DO NOT change any existing schema or records beyond inserting missing aliases.
--
-- Notes:
--   - ingredient_alias has unique constraint (ingredient_id, alias) -> we use ON CONFLICT DO NOTHING
--   - We include a small, conservative set of aliases (no guessing about claims; only identity/synonym variants)

-- ─────────────────────────────────────────────
-- 1) Convenience: insert helper aliases (by canonical_key)
-- ─────────────────────────────────────────────

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, v.alias
FROM (
  VALUES
  -- ───────────────────────────────────────────
  -- V21 (Whole foods) — conservative identity variants
  -- ───────────────────────────────────────────
  ('apples',              'Organic Apple'),
  ('apples',              'Apple'),
  ('apples',              'Apples'),

  ('beet',                'Organic Beet'),
  ('beet',                'Beet'),
  ('beet',                'Beets'),

  ('broccoli',            'Organic Broccoli'),
  ('broccoli',            'Broccoli'),

  ('carrots',             'Organic Carrot'),
  ('carrots',             'Carrot'),
  ('carrots',             'Carrots'),

  ('spinach',             'Organic Spinach'),
  ('spinach',             'Spinach'),

  ('tomatoes',            'Organic Tomato'),
  ('tomatoes',            'Tomato'),
  ('tomatoes',            'Tomatoes'),

  ('strawberry',          'Organic Strawberry'),
  ('strawberry',          'Strawberry'),
  ('strawberry',          'Strawberries'),

  ('blackberry',          'Organic Blackberry'),
  ('blackberry',          'Blackberry'),
  ('blackberry',          'Blackberries'),

  ('blueberry',           'Organic Blueberry'),
  ('blueberry',           'Blueberry'),
  ('blueberry',           'Blueberries'),

  ('raspberry',           'Organic Raspberry'),
  ('raspberry',           'Raspberry'),
  ('raspberry',           'Raspberries'),

  ('organic tart cherry', 'Organic Tart Cherry'),
  ('organic tart cherry', 'Tart Cherry'),
  ('organic tart cherry', 'Tart cherries'),

  ('bell peppers',        'Organic Green Bell Pepper'),
  ('bell peppers',        'Green Bell Pepper'),
  ('bell peppers',        'Bell Pepper'),
  ('bell peppers',        'Bell peppers'),
  ('bell peppers',        'Green bell pepper'),

  ('brussels sprouts',    'Organic Brussels Sprout'),
  ('brussels sprouts',    'Brussels Sprout'),
  ('brussels sprouts',    'Brussels sprouts'),
  ('brussels sprouts',    'Brussel sprout'),

  ('ginger',              'Organic Ginger'),
  ('ginger',              'Ginger'),

  ('garlic',              'Organic Garlic'),
  ('garlic',              'Garlic'),

  ('green onion',         'Organic Green Onion'),
  ('green onion',         'Green Onion'),
  ('green onion',         'Scallion'),
  ('green onion',         'Scallions'),

  ('parsley',             'Organic Parsley'),
  ('parsley',             'Parsley'),

  ('cauliflower',         'Organic Cauliflower'),
  ('cauliflower',         'Cauliflower'),

  ('red cabbage',         'Organic Red Cabbage'),
  ('red cabbage',         'Red Cabbage'),
  ('red cabbage',         'Purple Cabbage'),

  ('kale',                'Organic Kale'),
  ('kale',                'Kale'),

  ('pepper',              'Organic Red Pepper'),
  ('pepper',              'Red Pepper'),
  ('pepper',              'Red pepper'),

  ('celery',              'Organic Celery'),
  ('celery',              'Celery'),

  ('asparagus',           'Organic Asparagus'),
  ('asparagus',           'Asparagus'),

  ('organic sea kelp',    'Organic Sea Kelp'),
  ('organic sea kelp',    'Sea Kelp'),
  ('organic sea kelp',    'Kelp'),
  ('organic sea kelp',    'Seaweed'),

  -- ───────────────────────────────────────────
  -- V21 (Enzymes) — common naming variants / E-numbers where supplied
  -- ───────────────────────────────────────────
  ('lipase',                    'Lipase'),

  ('protease (e1101-i)',        'Protease'),
  ('protease (e1101-i)',        'Protease (E1101(i))'),
  ('protease (e1101-i)',        'Protease E1101(i)'),
  ('protease (e1101-i)',        'E1101(i)'),

  ('acid protease',             'Acid Protease'),
  ('acid protease',             'Acid protease'),

  ('beta-glucanase',            'Beta-Glucanase'),
  ('beta-glucanase',            'β-Glucanase'),
  ('beta-glucanase',            'Beta glucanase'),

  ('cellulase',                 'Cellulase'),

  ('bromelain (e1101-iii)',     'Bromelain'),
  ('bromelain (e1101-iii)',     'Bromelain (E1101(iii))'),
  ('bromelain (e1101-iii)',     'Bromelain E1101(iii)'),
  ('bromelain (e1101-iii)',     'E1101(iii)'),

  ('phytase',                   'Phytase'),

  ('lactase',                   'Lactase'),

  ('papain (e1101-ii)',         'Papain'),
  ('papain (e1101-ii)',         'Papain (E1101(ii))'),
  ('papain (e1101-ii)',         'Papain E1101(ii)'),
  ('papain (e1101-ii)',         'E1101(ii)'),

  ('peptidase',                 'Peptidase'),
  ('peptidase',                 'Peptidases'),

  ('pectinase',                 'Pectinase'),

  ('hemicellulase',             'Hemicellulase'),
  ('hemicellulase',             'Hemi-cellulase'),

  ('xylanase',                  'Xylanase'),

  -- ───────────────────────────────────────────
  -- V21 (Probiotics / cultures) — taxonomy variants
  -- ───────────────────────────────────────────
  ('lactobacillus bulgaricus',      'L. bulgaricus'),
  ('lactobacillus bulgaricus',      'Lactobacillus bulgaricus'),
  ('lactobacillus bulgaricus',      'Lactobacillus delbrueckii subsp. bulgaricus'),
  ('lactobacillus bulgaricus',      'L. delbrueckii subsp. bulgaricus'),

  ('lactiplantibacillus plantarum', 'L. plantarum'),
  ('lactiplantibacillus plantarum', 'Lactiplantibacillus plantarum'),
  ('lactiplantibacillus plantarum', 'Lactobacillus plantarum'),
  ('lactiplantibacillus plantarum', 'Lactobacillus (L.) plantarum'),

  -- ───────────────────────────────────────────
  -- V21 (Supplement-like)
  -- ───────────────────────────────────────────
  ('coq10', 'CoQ10'),
  ('coq10', 'Coenzyme Q10'),
  ('coq10', 'Co-Enzyme Q10'),
  ('coq10', 'Ubiquinone'),

  -- ───────────────────────────────────────────
  -- V22 (Vitamins / minerals / excipients) — label + E-number + common synonym variants
  -- ───────────────────────────────────────────
  ('calcium carbonate (ci pigment white 18, e170-i)', 'Calcium Carbonate'),
  ('calcium carbonate (ci pigment white 18, e170-i)', 'E170(i)'),
  ('calcium carbonate (ci pigment white 18, e170-i)', 'E170'),
  ('calcium carbonate (ci pigment white 18, e170-i)', 'CI Pigment White 18'),
  ('calcium carbonate (ci pigment white 18, e170-i)', 'Calcium carbonate (E170(i))'),
  ('calcium carbonate (ci pigment white 18, e170-i)', 'Calcium carbonate (E170)'),

  ('magnesium oxide (e530)', 'Magnesium Oxide'),
  ('magnesium oxide (e530)', 'E530'),
  ('magnesium oxide (e530)', 'Magnesium oxide (E530)'),

  ('microcrystalline cellulose (e460-i)', 'Microcrystalline Cellulose'),
  ('microcrystalline cellulose (e460-i)', 'MCC'),
  ('microcrystalline cellulose (e460-i)', 'E460(i)'),
  ('microcrystalline cellulose (e460-i)', 'E460'),
  ('microcrystalline cellulose (e460-i)', 'Microcrystalline cellulose (E460(i))'),

  ('ascorbic acid (e300)', 'Ascorbic Acid'),
  ('ascorbic acid (e300)', 'Vitamin C'),
  ('ascorbic acid (e300)', 'E300'),
  ('ascorbic acid (e300)', 'Ascorbic acid (E300)'),

  ('dicalcium phosphate (e341-ii)', 'Dicalcium Phosphate'),
  ('dicalcium phosphate (e341-ii)', 'E341(ii)'),
  ('dicalcium phosphate (e341-ii)', 'E341'),
  ('dicalcium phosphate (e341-ii)', 'Dibasic calcium phosphate'),
  ('dicalcium phosphate (e341-ii)', 'Calcium phosphate dibasic'),

  ('maltodextrin', 'Maltodextrin'),

  -- Keep the external catalog typo exactly as an alias so lookups still work:
  ('dl-aipha-tocopheryi acetate', 'dl-AIpha-TocopheryI Acetate'),
  ('dl-aipha-tocopheryi acetate', 'dl-Alpha-Tocopheryl Acetate'),
  ('dl-aipha-tocopheryi acetate', 'Vitamin E acetate'),
  ('dl-aipha-tocopheryi acetate', 'dl-alpha tocopheryl acetate'),

  ('beta-carotene', 'Beta-Carotene'),
  ('beta-carotene', 'Beta carotene'),
  ('beta-carotene', 'β-Carotene'),

  ('biotin', 'Biotin'),
  ('biotin', 'Vitamin B7'),

  ('vitamin d3 (cholecalciferol)', 'Cholecalciferol'),
  ('vitamin d3 (cholecalciferol)', 'Vitamin D3'),
  ('vitamin d3 (cholecalciferol)', 'Vitamin D'),
  ('vitamin d3 (cholecalciferol)', 'D3'),

  ('chromium chloride', 'Chromium Chloride'),
  ('chromium chloride', 'Chromic chloride'),

  ('copper sulfate', 'Copper Sulfate'),
  ('copper sulfate', 'Cupric sulfate'),

  ('cross-linked sodium carboxymethyl cellulose (e468)', 'Croscarmellose Sodium'),
  ('cross-linked sodium carboxymethyl cellulose (e468)', 'Croscarmellose sodium'),
  ('cross-linked sodium carboxymethyl cellulose (e468)', 'Cross-linked sodium carboxymethyl cellulose'),
  ('cross-linked sodium carboxymethyl cellulose (e468)', 'Crosslinked sodium carboxymethylcellulose'),
  ('cross-linked sodium carboxymethyl cellulose (e468)', 'E468'),

  ('cyanocobalamin (vitamin b12)', 'Cyanocobalamin'),
  ('cyanocobalamin (vitamin b12)', 'Vitamin B12'),
  ('cyanocobalamin (vitamin b12)', 'B12'),

  ('d-calcium pantothenate', 'D-Calcium Pantothenate'),
  ('d-calcium pantothenate', 'Calcium pantothenate'),
  ('d-calcium pantothenate', 'Vitamin B5'),

  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'FD&C Blue #1 Aluminum Lake'),
  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'FD&C Blue No. 1'),
  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'Blue 1'),
  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'Brilliant Blue FCF'),
  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'E133'),
  ('brilliant blue fcf (fd&c blue no 1, ci food blue 2, e133)', 'CI Food Blue 2'),

  ('fd&c yellow #5 (tartrazine) aluminum lake', 'FD&C Yellow #5 (tartrazine) Aluminum Lake'),
  ('fd&c yellow #5 (tartrazine) aluminum lake', 'FD&C Yellow No. 5'),
  ('fd&c yellow #5 (tartrazine) aluminum lake', 'Yellow 5'),
  ('fd&c yellow #5 (tartrazine) aluminum lake', 'Tartrazine'),
  ('fd&c yellow #5 (tartrazine) aluminum lake', 'E102'),

  ('folic acid', 'Folic Acid'),
  ('folic acid', 'Vitamin B9'),
  ('folic acid', 'Folate'),

  ('gelatin', 'Gelatin'),

  ('hypromellose (e464)', 'Hydroxypropyl Methylcellulose'),
  ('hypromellose (e464)', 'Hypromellose'),
  ('hypromellose (e464)', 'HPMC'),
  ('hypromellose (e464)', 'E464'),

  ('manganese sulfate', 'Manganese Sulfate'),
  ('manganese sulfate', 'Manganous sulfate'),

  ('niacinamide (vitamin b3)', 'Niacinamide'),
  ('niacinamide (vitamin b3)', 'Vitamin B3'),
  ('niacinamide (vitamin b3)', 'Nicotinamide'),

  ('polyethylene glycol (e1521)', 'Polyethylene Glycol'),
  ('polyethylene glycol (e1521)', 'PEG'),
  ('polyethylene glycol (e1521)', 'Macrogol'),
  ('polyethylene glycol (e1521)', 'E1521'),

  ('polyvinylpolypyrrolidone (e1202)', 'Polyvinylpolypyrrolidone'),
  ('polyvinylpolypyrrolidone (e1202)', 'PVPP'),
  ('polyvinylpolypyrrolidone (e1202)', 'E1202'),

  ('pyridoxine hydrochloride (vitamin b6)', 'Pyridoxine Hydrochloride'),
  ('pyridoxine hydrochloride (vitamin b6)', 'Vitamin B6'),
  ('pyridoxine hydrochloride (vitamin b6)', 'Pyridoxine HCl'),

  ('riboflavin (vitamin b2, e101)', 'Riboflavin'),
  ('riboflavin (vitamin b2, e101)', 'Vitamin B2'),
  ('riboflavin (vitamin b2, e101)', 'E101'),

  ('silicon dioxide (e551)', 'Silicon Dioxide'),
  ('silicon dioxide (e551)', 'Silica'),
  ('silicon dioxide (e551)', 'E551'),

  ('disodium selenite', 'Sodium Selenite'),
  ('disodium selenite', 'Selenite'),
  ('disodium selenite', 'Sodium hydrogen selenite'),

  ('stearic acid', 'Stearic Acid'),

  ('thiamine mononitrate', 'Thiamine Mononitrate'),
  ('thiamine mononitrate', 'Vitamin B1'),
  ('thiamine mononitrate', 'Thiamine'),

  ('titanium dioxide (ci pigment white 6, e171)', 'Titanium Dioxide (color)'),
  ('titanium dioxide (ci pigment white 6, e171)', 'Titanium Dioxide'),
  ('titanium dioxide (ci pigment white 6, e171)', 'E171'),
  ('titanium dioxide (ci pigment white 6, e171)', 'CI Pigment White 6'),

  ('vitamin a acetate', 'Vitamin A Acetate'),
  ('vitamin a acetate', 'Retinyl acetate'),
  ('vitamin a acetate', 'Vitamin A'),

  ('zinc oxide', 'Zinc Oxide')
) AS v(canonical_key, alias)
JOIN ingredients i ON i.canonical_key = v.canonical_key
ON CONFLICT (ingredient_id, alias) DO NOTHING;

-- ─────────────────────────────────────────────
-- 2) Hard block: DO NOT seed non-ingredients
--    ("Less than 2% of:" should never be an ingredient)
-- ─────────────────────────────────────────────
-- No-op by design.
