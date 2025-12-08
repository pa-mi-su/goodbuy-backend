/********************************************************************************************
 UNIVERSAL SEED SCRIPT FOR ANY NEW INGREDIENT
 Copy → Paste → Replace the placeholders → Run

 REQUIRED FROM SLACK:
   • Name  → becomes DISPLAY_NAME + aliases
   • GTIN-14 → paste as EAN_GTIN14 (never use raw EAN)
---------------------------------------------------------------------------------------------
********************************************************************************************/

-----------------------------------------
-- STEP 1) INSERT INGREDIENT MASTER ROW
-----------------------------------------
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
        '<<<CANONICAL_KEY>>>',                -- lowercase ONLY (match key)
        '<<<DISPLAY_NAME>>>',                 -- pretty display name
        '<<<SHORT_SUMMARY>>>',
        '<<<LONG_DESCRIPTION>>>',
        '<<<FUNCTION_LABEL>>>',               -- e.g. Surfactant, Solvent, Preservative
        '<<<CONCERNS>>>',
        8.0,                                  -- safety_score (0–10)
        'A',                                  -- rating_letter (A–F)
        5,                                    -- references_count
        'General',                            -- category
        '<<<REGULATION_NOTES>>>',
        TRUE,
        '<<<FUNC_USE_KEY>>>'                  -- taxonomy key, e.g. “surfactant”
    )
    ON CONFLICT (canonical_key) DO NOTHING
    RETURNING id
)
SELECT id FROM ins
UNION
SELECT id FROM ingredients WHERE canonical_key = '<<<CANONICAL_KEY>>>';


-----------------------------------------
-- STEP 2) INSERT ALIASES (MATCHING KEYS)
-----------------------------------------
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, a.alias
FROM ingredients i
JOIN (
    VALUES
        ('<<<DISPLAY_NAME>>>'),
        ('<<<CANONICAL_KEY>>>')
) AS a(alias)
  ON 1 = 1
WHERE i.canonical_key = '<<<CANONICAL_KEY>>>'
ON CONFLICT DO NOTHING;


-----------------------------------------
-- STEP 3) INSERT SEARCH TAGS (optional)
-----------------------------------------
INSERT INTO ingredient_tags (ingredient_id, name)
SELECT i.id, t.name
FROM ingredients i
JOIN (
    VALUES
        ('ingredient'),
        ('cleaning agent')
) AS t(name)
    ON 1 = 1
WHERE i.canonical_key = '<<<CANONICAL_KEY>>>'
ON CONFLICT DO NOTHING;


-----------------------------------------
-- STEP 4) LINK INGREDIENT → PRODUCT
-----------------------------------------
INSERT INTO product_ingredients (product_id, ingredient_id, display_name)
SELECT p.id, i.id, '<<<DISPLAY_NAME>>>'
FROM products p
JOIN ingredients i
  ON i.canonical_key = '<<<CANONICAL_KEY>>>'
WHERE p.ean = '<<<EAN_GTIN14>>>'     -- 14-digit GTIN from Slack
ON CONFLICT (product_id, ingredient_id) DO NOTHING;



🔥 How to use (fast)

When Slack reports this:

• Name: Decyl Glucoside
• EAN (GTIN-14): 00817939000052

You plug into template:

<<<CANONICAL_KEY>>>    → decyl glucoside
<<<DISPLAY_NAME>>>     → Decyl Glucoside
<<<EAN_GTIN14>>>       → 00817939000052

Run it → ingredient instantly exists → next scan shows colored leaf.

