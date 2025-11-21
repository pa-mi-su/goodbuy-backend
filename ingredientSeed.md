/***********************************************************************
 INGREDIENT MASTER CREATION SCRIPT (GoodBuy v1 Ingredient DB)

 STEP 1 — Insert ingredient
 STEP 2 — Insert aliases
 STEP 3 — Insert tags
 STEP 4 — Link ingredient → product(s)

 Fill every placeholder marked with <<< >>>.
************************************************************************/

------------------------------
-- STEP 1 — Insert ingredient
------------------------------
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
    '<<<CANONICAL_KEY>>>',          -- lowercase key, e.g. 'potassium citrate'
    '<<<DISPLAY_NAME>>>',           -- pretty name, e.g. 'Potassium Citrate'
    '<<<SHORT_SUMMARY>>>',          -- 1–2 sentence summary
    '<<<LONG_DESCRIPTION>>>',       -- full writeup
    '<<<FUNCTION_LABEL>>>',         -- e.g. 'pH Buffer'
    '<<<CONCERNS>>>',               -- safety notes
    <<<SAFETY_SCORE>>>,             -- numeric, e.g. 9.50
    '<<<RATING_LETTER>>>',          -- A–F
    <<<REFERENCES_COUNT>>>,         -- integer
    '<<<CATEGORY_LABEL>>>',         -- e.g. 'pH Adjusters'
    '<<<REGULATION_NOTES>>>',       -- free text
    TRUE,                           -- is_active
    '<<<FUNC_USE_KEY>>>'            -- must match taxonomy key
)
RETURNING id;


/***********************************************************************
 STEP 2 — Add aliases

 Replace <<<INGREDIENT_ID>>> with the ID returned above.
************************************************************************/
INSERT INTO ingredient_alias (ingredient_id, alias)
VALUES
    (<<<INGREDIENT_ID>>>, '<<<ALIAS_1>>>'),
    (<<<INGREDIENT_ID>>>, '<<<ALIAS_2>>>'),
    (<<<INGREDIENT_ID>>>, '<<<ALIAS_3>>>')
ON CONFLICT DO NOTHING;


/***********************************************************************
 STEP 3 — Add tags
************************************************************************/
INSERT INTO ingredient_tags (ingredient_id, name)
VALUES
    (<<<INGREDIENT_ID>>>, '<<<TAG_1>>>'),
    (<<<INGREDIENT_ID>>>, '<<<TAG_2>>>')
ON CONFLICT DO NOTHING;


/***********************************************************************
 STEP 4 — Link ingredient to products

 A product may have multiple ingredients.
************************************************************************/
INSERT INTO product_ingredients (product_id, ingredient_id, display_name)
SELECT p.id, <<<INGREDIENT_ID>>>, '<<<DISPLAY_NAME>>>'
FROM products p
WHERE p.ean = '<<<EAN_CODE>>>'
ON CONFLICT (product_id, ingredient_id) DO NOTHING;

✅ FULL POPULATED VERSION — POTASSIUM CITRATE
👉 STEP 1 — Insert Ingredient
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
    'potassium citrate',
    'Potassium Citrate',
    'A buffering agent and pH regulator used to control acidity.',
    'Full description goes here (mechanism, sources, typical use in cleaners, toxicity, etc.).',
    'pH Buffer',
    'Generally recognized as safe; may irritate skin or eyes at high concentrations.',
    9.50,
    'A',
    12,
    'pH Adjusters',
    'Permitted for use in many household cleaning and detergent products when used as directed.',
    TRUE,
    'buffering_agent'
)
RETURNING id;

→ Suppose the returned id is 1.

👉 STEP 2 — Aliases

INSERT INTO ingredient_alias (ingredient_id, alias)
VALUES
    (1, 'Potassium Citrate'),    -- canonical alias
    (1, 'E332'),
    (1, 'Tripotassium citrate')
ON CONFLICT DO NOTHING;

👉 STEP 3 — Tags

INSERT INTO ingredient_tags (ingredient_id, name)
VALUES
    (1, 'buffering agent')
ON CONFLICT DO NOTHING;

👉 STEP 4 — Link to product

INSERT INTO product_ingredients (product_id, ingredient_id, display_name)
SELECT p.id, 1, 'Potassium Citrate'
FROM products p
WHERE p.ean = '00817939000052'
ON CONFLICT (product_id, ingredient_id) DO NOTHING;



🧪 GoodBuy Ingredient Field Reference

A concise but complete guide for hand-crafting accurate ingredient records.

⸻

🔑 canonical_key

Type: VARCHAR(255) UNIQUE
This is the stable internal key for the ingredient.
    •   Lowercase
    •   No special symbols
    •   Hyphens allowed
    •   Must never change once created
    •   Used in joins, cache keys, and linking products

Examples:
    •   potassium citrate
    •   citric acid
    •   sodium benzoate

⸻

🏷️ display_name

Type: VARCHAR(255)
The human-friendly name displayed in the app.
    •   Capitalized
    •   Simple
    •   No E-codes unless part of standard name
    •   Keep this readable and consumer-friendly

Examples:
    •   Potassium Citrate
    •   Citric Acid
    •   Sodium Benzoate

⸻

📘 summary

Type: TEXT
A short one-sentence explanation of the ingredient.
This shows in quick views, summaries, lists, etc.

Examples:
    •   “A buffering agent used to control acidity in many cleaners and food products.”
    •   “A common preservative that prevents mold and extends shelf life.”

⸻

📚 description

Type: TEXT
The long-form detailed write-up.
This is what users see when they tap into the full ingredient detail screen.

Should include:
    •   what the ingredient is
    •   what it does
    •   where it comes from
    •   issues, controversies, benefits
    •   safety context
    •   regulatory notes
    •   exposure considerations

This is your main authoritative content block.

⸻

⚙️ "function"

Type: TEXT
(Column name is quoted because function is a SQL keyword.)

This is the ingredient’s functional category, e.g.:
    •   Chelating Agent
    •   Surfactant
    •   Preservative
    •   pH Buffer
    •   Solvent

Used for sorting, filtering, and contextual labeling.

⸻

⚠️ concerns

Type: TEXT
This is the list of potential concerns, written clearly for consumers.

This could include:
    •   links to irritation
    •   environmental persistence
    •   endocrine disruption concerns
    •   regulatory warnings
    •   allergy responses

Guidelines:
Keep factual, non-alarmist, but honest.

⸻

🛡️ safety_score

Type: NUMERIC(4,2)
A numeric 0–10 internal score (your system can define scale).
    •   10 = very safe
    •   0 = high concern
    •   NULL = not yet evaluated

Used to generate the ingredient’s grade.

⸻

🎓 rating_letter

Type: VARCHAR(4)
Letter grade derived from safety_score.

Common pattern:
    •   A
    •   B
    •   C
    •   D
    •   F

Can also store:
    •   A-
    •   B+
    •   NR (not rated)

⸻

📖 references_count

Type: INT
Count of total references/resources you’ve linked to this ingredient.
    •   Studies
    •   Safety documents
    •   Regulatory papers
    •   PubChem entries
    •   ECHA dossiers

This is used for ranking and internal analytics.

⸻

🏷️ category

Type: VARCHAR(255)
High-level grouping/category for the ingredient.

Examples:
    •   Surfactant
    •   Chelating Agent
    •   Preservative
    •   Fragrance
    •   Colorant
    •   Solvent

This is different from "function" — category is more general.

⸻

📜 regulation_notes

Type: TEXT
Everything about regulatory status, such as:
    •   banned in EU for certain uses
    •   concentration limits
    •   FDA position
    •   EPA classification
    •   IFRA restrictions
    •   CARB VOC notes
    •   Prop 65 information

This is very important for credibility.

⸻

✅ is_active

Type: BOOLEAN
Whether the ingredient is active in the GoodBuy catalog.

Values:
    •   TRUE → normal
    •   FALSE → hidden / deprecated / merged / flagged

You will rarely set this manually.

⸻

🧰 func_use

Type: TEXT
This mirrors "function" but is specifically used by JPA mapping.
Export pipelines and enrichment engines use this field.

Keep "function" and func_use aligned unless you intentionally store internal-only text here.

⸻

✔️ Example filled-out ingredient (Potassium Citrate)

Just to visualize it:

canonical_key: potassium citrate
display_name: Potassium Citrate
summary: A buffering agent used to regulate acidity.
description: (long detailed writeup)
function: pH Buffer
concerns: Generally recognized as safe; may irritate skin in high concentrations.
safety_score: 9.5
rating_letter: A
references_count: 12
category: Buffering Agent
regulation_notes: Approved for use in EPA-registered cleaning products.
is_active: TRUE
func_use: Buffering agent


1) function (public-facing / descriptive)

What it is:

A human-friendly description of what the ingredient does in the product.

Think of it like:

What you would show to the user on the ingredient detail screen.

Examples:
    •   “Preservative”
    •   “Surfactant”
    •   “Foaming Agent”
    •   “pH Buffer”
    •   “Chelating Agent”

This is the “beautified” terminology for consumers.

⸻

2) func_use (internal / normalized / machine-friendly)

What it is:

A normalized internal label that your system uses for search, grouping, filters, analytics, and future features.

This stays consistent, even if the ingredient’s “function” wording changes later.

Think of it like:

A tag the algorithm can rely on.

Examples:
    •   buffering_agent
    •   surfactant
    •   solvent
    •   preservative
    •   chelating_agent

These values are predictable, stable, and never include spaces, marketing terms, or fancy wording.


