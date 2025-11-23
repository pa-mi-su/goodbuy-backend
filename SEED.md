/********************************************************************************************
 SEEDING A NEW INGREDIENT INTO GOODBUY DB
 Example: Decyl Glucoside
 From Slack:
   • Name: Decyl Glucoside
   • EAN (GTIN-14): 00817939000052
---------------------------------------------------------------------------------------------

 HOW TO USE THIS TEMPLATE NEXT TIME:
   1. Replace every occurrence of "decyl glucoside" with the NEW ingredient canonical_key.
   2. Replace every "Decyl Glucoside" with the display name.
   3. Update summary / description / function / concerns / tags as needed.
   4. Replace the EAN at the very bottom with the product's GTIN-14.
   5. Run EVERYTHING as one block. Done.
********************************************************************************************/


-----------------------------------------
-- STEP 1) INSERT INGREDIENT MASTER ROW
-----------------------------------------
-- canonical_key MUST be lowercase. This is the key used for matching.
WITH ins AS (
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
    ) VALUES (
        'decyl glucoside',                    -- <<< canonical_key
        'Decyl Glucoside',                    -- <<< display_name
        'A mild, plant-derived non-ionic surfactant used for gentle cleansing.',
        'Decyl Glucoside is a non-ionic surfactant sourced from plant sugars. It is common in eco-friendly cleaners and valued for its mildness and biodegradability.',
        'Surfactant',
        'Generally considered low-irritation; may irritate in high concentrations.',
        8.5,                                  -- safety score (0–10)
        'A',                                  -- rating letter
        8,                                    -- references count (rough)
        'Surfactants',
        'Common in eco-labeled products.',
        TRUE,
        'surfactant'
    )
    ON CONFLICT (canonical_key) DO NOTHING
    RETURNING id
)
SELECT id FROM ins
UNION
SELECT id FROM ingredients WHERE canonical_key = 'decyl glucoside';


-----------------------------------------
-- STEP 2) INSERT ALIASES FOR MATCHING
-----------------------------------------
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, a.alias
FROM ingredients i
JOIN (
    VALUES
        ('Decyl Glucoside'),
        ('decyl glucoside'),
        ('Decyl D-glucoside')
) AS a(alias)
    ON 1 = 1
WHERE i.canonical_key = 'decyl glucoside'
ON CONFLICT DO NOTHING;


-----------------------------------------
-- STEP 3) INSERT SEARCH TAGS (optional)
-----------------------------------------
INSERT INTO ingredient_tags (ingredient_id, name)
SELECT i.id, t.name
FROM ingredients i
JOIN (
    VALUES
        ('non-ionic surfactant'),
        ('plant-derived'),
        ('biodegradable'),
        ('mild cleanser')
) AS t(name)
    ON 1 = 1
WHERE i.canonical_key = 'decyl glucoside'
ON CONFLICT DO NOTHING;


-----------------------------------------
-- STEP 4) LINK INGREDIENT → PRODUCT
-- This ensures existing product snapshots pick it up.
-----------------------------------------
INSERT INTO product_ingredients (product_id, ingredient_id, display_name)
SELECT p.id, i.id, 'Decyl Glucoside'
FROM products p
JOIN ingredients i
  ON i.canonical_key = 'decyl glucoside'
WHERE p.ean = '00817939000052'     -- <<< GTIN-14 from Slack
ON CONFLICT (product_id, ingredient_id) DO NOTHING;