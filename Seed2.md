/********************************************************************************************
 UPDATE EXISTING INGREDIENT IN GOODBUY DB
 Example: Benzisothiazolinone

 HOW TO USE:
   1. Replace every "benzisothiazolinone" (lowercase) with your ingredient’s canonical_key.
   2. Replace every "Benzisothiazolinone" (proper case) with the display name.
   3. Edit summary / description / function / concerns / tags / rating / category as needed.
   4. Run this whole block at once.
********************************************************************************************/

-----------------------------------------
-- STEP 1) UPDATE INGREDIENT MASTER ROW
-----------------------------------------
WITH upd AS (
    UPDATE ingredients
    SET
        display_name     = 'Benzisothiazolinone',
        summary          = 'A synthetic preservative used to prevent microbial growth in cleaners and other products.',
        description      = 'Benzisothiazolinone (BIT) is an isothiazolinone-class preservative used in a variety of household and industrial products to control bacteria and fungi.',
        "function"       = 'Preservative',
        concerns         = 'Can cause skin sensitization and allergic reactions; generally used at low levels. Avoid leave-on skin exposure.',
        safety_score     = 2.0,        -- 0–10 scale, lower = more concern in your system
        rating_letter    = 'F',        -- A+..F depending on your rubric
        references_count = 10,         -- rough count of sources
        category         = 'Preservatives',
        regulation_notes = 'Subject to concentration limits in some regions; commonly grouped with other isothiazolinones.',
        is_active        = TRUE,
        func_use         = 'preservative'
    WHERE canonical_key = 'benzisothiazolinone'
    RETURNING id
)
SELECT id FROM upd
UNION
SELECT id FROM ingredients WHERE canonical_key = 'benzisothiazolinone';


-----------------------------------------
-- STEP 2) UPSERT ALIASES FOR MATCHING
-----------------------------------------
INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, a.alias
FROM ingredients i
JOIN (
    VALUES
        ('Benzisothiazolinone'),
        ('benzisothiazolinone'),
        ('BIT')
) AS a(alias)
    ON 1 = 1
WHERE i.canonical_key = 'benzisothiazolinone'
ON CONFLICT DO NOTHING;


-----------------------------------------
-- STEP 3) UPSERT SEARCH TAGS (optional)
-----------------------------------------
INSERT INTO ingredient_tags (ingredient_id, name)
SELECT i.id, t.name
FROM ingredients i
JOIN (
    VALUES
        ('preservative'),
        ('isothiazolinone'),
        ('biocide'),
        ('allergen')
) AS t(name)
    ON 1 = 1
WHERE i.canonical_key = 'benzisothiazolinone'
ON CONFLICT DO NOTHING;