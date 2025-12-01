-- V13__seed_initial_ingredients.sql
-- Seed initial ingredient records based on known canonical keys + display names.
-- Safe to re-run: uses ON CONFLICT (canonical_key) DO UPDATE.

BEGIN;

-- ─────────────────────────────────────────────
-- Example: fully filled-out ingredient (Water)
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
) VALUES (
    'water',
    'Water',
    'Water is the base solvent used to dissolve and carry other ingredients in cleaning and personal care products.',
    'Water (H₂O) is a neutral, inorganic solvent that forms the bulk of many household cleaners and personal care products. '
    'In formulations it primarily acts as a carrier and diluent, controlling viscosity and helping other ingredients mix and spread. '
    'From a consumer-safety perspective, the ingredient itself is considered very low risk; any concerns usually relate to '
    'contaminants (e.g., microbes, heavy metals, hard water ions) rather than the water molecule.',
    'Solvent; diluent; carrier',
    'Generally recognized as safe and non-irritating for normal use. Main issues come from contamination, extreme temperatures, '
    'or very hard/untreated water, not from water itself.',
    10.00,
    'A',
    3,
    'solvent / base',
    'Must meet applicable purity standards (e.g., potable water, purified water for cosmetics). Some jurisdictions specify '
    'microbial and mineral limits for water used in consumer products.',
    TRUE,
    'Solvent / carrier'
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

-- ─────────────────────────────────────────────
-- All other known ingredients with TODO fields
-- (you fill these in as you research)
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
) VALUES (
    'baking soda (sodium bicarbonate, e500-ii)',
    'Sodium Bicarbonate',
    'TODO: Short plain-language summary for Sodium Bicarbonate.',
    'TODO: Longer description explaining what Sodium Bicarbonate is, why it is used in cleaners, and key safety context.',
    'TODO: e.g. mild abrasive; pH adjuster; deodorizer',
    'TODO: e.g. generally low concern; watch for dust inhalation at high exposure.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any key regulatory notes (e.g. food additive E500(ii)).',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'laureth-7',
    'Laureth-7',
    'TODO: Short summary for Laureth-7 (ethoxylated nonionic surfactant used in cleaners).',
    'TODO: Longer description: what Laureth-7 is, how it works as a surfactant, typical uses, and safety context.',
    'TODO: primary function, e.g. surfactant / cleanser / emulsifier',
    'TODO: e.g. potential irritation at higher levels; ethoxylation byproduct concerns (1,4-dioxane) depending on purification.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any limits or guidance from cosmetic/cleaning regulators.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'lauryl glucoside',
    'Lauryl Glucoside',
    'TODO: Short summary for Lauryl Glucoside (mild, plant-derived nonionic surfactant).',
    'TODO: Longer description: sugar-based surfactant, often used in milder or more natural formulations.',
    'TODO: e.g. surfactant / cleanser / foaming agent',
    'TODO: generally low concern; occasional irritation in sensitive skin at higher use levels.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any relevant cosmetic/cleaning regulations or typical usage limits.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'sodium methyl2-sulfolaurate',
    'Sodium Methyl2-Sulfolaurate',
    'TODO: Short summary for Sodium Methyl 2-Sulfolaurate (anionic surfactant used in cleaners).',
    'TODO: Longer description: what this surfactant is, typical uses in detergents and cleaners, and safety profile.',
    'TODO: surfactant / detergent',
    'TODO: e.g. possible skin/eye irritation at high concentrations; review data.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any regulatory comments if found.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'fatty acids (e570)',
    'Fatty Acid',
    'TODO: Short summary for E570 Fatty Acids (mixture used as a processing aid/emulsifier).',
    'TODO: Longer description: what E570 is, typical uses in food/cleaning, and safety context.',
    'TODO: e.g. surfactant; processing aid; emulsifier',
    'TODO: usually low concern; safety depends on specific fatty acid profile and impurities.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note E-number status and any usage limits.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'c8-c18 and c18 unsatd',
    'C8-C18 and C18 unsatd',
    'TODO: Short summary for C8–C18 and C18 unsaturated fatty components (mixture, often from plant oils).',
    'TODO: Longer description: describe that this is a mixture of medium/long-chain fatty components, typical uses in detergents or surfactant backbones.',
    'TODO: e.g. surfactant backbone; fatty acid mixture',
    'TODO: safety depends on specific mixture; usually low concern but check impurities / oxidation products.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: add any regulatory views once researched.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'fragrance',
    'Fragrance',
    'TODO: Short summary for generic Fragrance (mixture of aromatic compounds used for scent).',
    'TODO: Longer description: fragrance as a mixture category; potential sensitizers; transparency concerns.',
    'TODO: fragrance; masking agent',
    'TODO: highlight allergy/sensitization potential and lack of full disclosure.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: reference IFRA guidelines / local labeling rules.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'sodium citrates (e331)',
    'Sodium Citrate',
    'TODO: Short summary for Sodium Citrate (buffering agent/water softener).',
    'TODO: Longer description: citrate salt used to chelate metals, buffer pH, and soften water.',
    'TODO: chelating agent; pH buffer; water softener',
    'TODO: generally low concern; GI upset at high oral doses; minimal risk at cleaner use levels.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note E331 status and typical regulatory limits, if any.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'subtilisin (protese) enzyme blend',
    'Subtilisin (Protese) Enzyme Blend',
    'TODO: Short summary for Subtilisin protease enzyme blend (stain-removal enzyme).',
    'TODO: Longer description: protease enzymes used to break down protein stains; encapsulation and inhalation concerns.',
    'TODO: enzyme; stain remover',
    'TODO: note inhalation sensitization risk in occupational settings; consumer risk lower but still worth flagging.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: mention any enzyme handling / labeling guidance.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'amylase enzyme blend',
    'Amylase Enzyme Blend',
    'TODO: Short summary for Amylase enzyme blend (starch-breaking enzyme for stain removal).',
    'TODO: Longer description: role in breaking down starch stains; safety profile and sensitization data.',
    'TODO: enzyme; stain remover',
    'TODO: note inhalation/allergy concerns from occupational exposure; lower risk for consumers.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any enzyme-related regulatory notes.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'lipase enzyme blend',
    'Lipase Enzyme Blend',
    'TODO: Short summary for Lipase enzyme blend (fat/oil-breaking enzyme).',
    'TODO: Longer description: role in breaking down fats/oils in stains; typical use ranges and safety info.',
    'TODO: enzyme; stain remover',
    'TODO: similar sensitization/inhalation concerns as other enzymes at high airborne levels.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: enzyme-handling and labeling considerations.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'mannanase enzyme blend',
    'Mannanase Enzyme Blend',
    'TODO: Short summary for Mannanase enzyme blend (breaks down gum/plant polysaccharides in stains).',
    'TODO: Longer description: specific role in detergents; data on irritation and sensitization.',
    'TODO: enzyme; stain remover',
    'TODO: similar occupational sensitization concerns as other detergent enzymes.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: regulatory/labeling notes once researched.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'tetrasodium glutamate diacetate',
    'Tetrasodium Glutamate Diacetate',
    'TODO: Short summary for Tetrasodium Glutamate Diacetate (biodegradable chelating agent).',
    'TODO: Longer description: role as EDTA alternative, metal chelation, and safety overview.',
    'TODO: chelating agent; water softener',
    'TODO: generally favorable safety profile; verify dermal and environmental data.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: regulatory stance compared with EDTA where available.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'glycerol (e422)',
    'Glycerin',
    'TODO: Short summary for Glycerin (humectant/solvent used across many products).',
    'TODO: Longer description: what glycerin is, sources (vegetable/animal/synthetic), and safety profile.',
    'TODO: humectant; solvent',
    'TODO: generally low concern; possible irritation at high concentrations.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: mention E422 status and any usage norms.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'citrus aurantium bergamia (bergamot) fruit oil',
    'Citrus Aurantium Bergamia (Bergamot) Fruit Oil',
    'TODO: Short summary for Bergamot fruit oil (fragrance/essential oil).',
    'TODO: Longer description: composition, phototoxicity concerns (bergapten), and usage contexts.',
    'TODO: fragrance; masking agent',
    'TODO: note potential phototoxicity if not FCF (furocoumarin-free) and allergy concerns.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: IFRA/other guidance on leave-on vs rinse-off use.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'juniperus virginiana (cedarwood) oil',
    'Juniperus Virginiana (Cedarwood) Oil',
    'TODO: Short summary for Cedarwood oil (fragrance/essential oil).',
    'TODO: Longer description: key constituents, typical uses in fragrance, and safety considerations.',
    'TODO: fragrance; masking agent',
    'TODO: note allergy/irritation potential for sensitive individuals; environmental considerations if any.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: any IFRA or regulatory notes once researched.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'calcium chloride (e509)',
    'Calcium Chloride',
    'TODO: Short summary for Calcium Chloride (salt used for moisture control, de-icing, etc.).',
    'TODO: Longer description: roles in cleaning and other products; irritation and ingestion data.',
    'TODO: stabilizer; moisture control; functional salt',
    'TODO: can be irritating to skin and eyes at higher concentrations; watch ingestion exposure.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note E509 status and any limits.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'salt',
    'Sodium Chloride',
    'TODO: Short summary for Sodium Chloride (common salt, used as builder, thickener, etc.).',
    'TODO: Longer description: NaCl roles in cleaners (viscosity control, builder), and safety context.',
    'TODO: builder; viscosity modifier; functional salt',
    'TODO: generally low concern; high levels can irritate eyes/skin or be an issue if ingested in large amounts.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note general food/cosmetic safety status.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'sodium sulfate',
    'Sodium Sulfate',
    'TODO: Short summary for Sodium Sulfate (inorganic salt used as filler/builder).',
    'TODO: Longer description: common uses in detergents, and safety overview.',
    'TODO: builder; filler; processing aid',
    'TODO: generally low toxicological concern; watch dust inhalation in occupational settings.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: regulatory notes if any.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'potassium hydroxide (e525)',
    'Potassium Hydroxide',
    'TODO: Short summary for Potassium Hydroxide (strong base used for pH adjustment and saponification).',
    'TODO: Longer description: role in making soaps and adjusting pH; strong caustic agent with clear safety boundaries.',
    'TODO: pH adjuster; saponification agent',
    'TODO: highly caustic at concentrated levels; serious eye/skin burn risk; low risk in well-neutralized finished products.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note corrosive classification and typical finished-product limits.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'methylisothiazolinone',
    'Methylisothiazolinone',
    'TODO: Short summary for Methylisothiazolinone (preservative with allergy concerns).',
    'TODO: Longer description: strong, broad-spectrum preservative; significant history of contact allergy and regulatory restrictions.',
    'TODO: preservative; biocide',
    'TODO: well-known contact allergen; restricted or banned in some leave-on uses. Likely lower rating.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: capture EU/other regulatory restrictions (e.g. leave-on vs rinse-off).',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

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
    'benzisothiazolinone',
    'Benzisothiazolinone',
    'TODO: Short summary for Benzisothiazolinone (preservative with sensitization concerns).',
    'TODO: Longer description: biocidal preservative used in cleaners; associated with skin sensitization; regulatory context.',
    'TODO: preservative; biocide',
    'TODO: known contact allergen; watch for regulatory limits and patch-test data.',
    NULL,
    NULL,
    0,
    NULL,
    'TODO: note any regional restrictions and guidance.',
    TRUE,
    NULL
)
ON CONFLICT (canonical_key) DO UPDATE SET
    display_name     = EXCLUDED.display_name,
    summary          = EXCLUDED.summary,
    description      = EXCLUDED.description,
    "function"       = EXCLUDED."function",
    concerns         = EXCLUDED.concerns,
    safety_score     = EXCLUDED.safety_score,
    rating_letter    = EXCLUDED.rating_letter,
    references_count = EXCLUDED.references_count,
    category         = EXCLUDED.category,
    regulation_notes = EXCLUDED.regulation_notes,
    is_active        = EXCLUDED.is_active,
    func_use         = EXCLUDED.func_use;

COMMIT;
