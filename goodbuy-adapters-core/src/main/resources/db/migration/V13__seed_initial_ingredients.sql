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
    'Neutral base solvent used to dissolve and carry other ingredients in cleaning and personal care products.',
    'Water (H₂O) is an inorganic, largely inert solvent that makes up the bulk of many household cleaners and personal care products. '
    'In formulations it mainly acts as a carrier and diluent, helping other ingredients mix, disperse and spread across surfaces. '
    'For typical consumer use the toxicological concern is extremely low; issues usually relate to microbial or mineral contamination, not water itself.',
    'Solvent; diluent; carrier',
    'Generally recognized as safe at typical product purity. Key concerns are microbiological contamination or improper storage, not the water molecule itself.',
    96.00,
    'A',
    3,
    'solvent / base',
    'Must meet applicable drinking or purified water specifications where required. Cosmetic and household product guidance typically includes microbiological and mineral limits.',
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
-- Sodium Bicarbonate (baking soda, E500(ii))
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
    'Mild alkaline powder used as a gentle abrasive, deodorizer and pH adjuster in cleaners.',
    'Sodium bicarbonate (NaHCO₃), commonly known as baking soda, is a weak alkaline salt used in many household cleaners and laundry boosters. '
    'It can help lift soils, neutralize acids, control odors and provide mild scrubbing action without scratching most surfaces. '
    'Toxicological profiles and food-additive approvals point to very low systemic toxicity when used as directed.',
    'Mild abrasive; deodorizer; pH adjuster',
    'Generally low concern. At very high dust levels it may cause transient eye or respiratory irritation; ingestion of large amounts can upset electrolyte balance.',
    94.00,
    'A',
    4,
    'builder / mild abrasive',
    'Approved as food additive E500(ii) with defined purity. Widely accepted in household and personal care uses with no specific bans at typical concentrations.',
    TRUE,
    'Mild abrasive / deodorizer / pH control'
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
-- Laureth-7 (ethoxylated nonionic surfactant)
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
    'laureth-7',
    'Laureth-7',
    'Ethoxylated nonionic surfactant used to dissolve soils and help oils mix with water.',
    'Laureth-7 is a polyethoxylated fatty alcohol surfactant widely used in household cleaners and some personal care products. '
    'It lowers surface tension so water can wet surfaces and remove oily or greasy soils, and it helps keep soils dispersed in the wash water. '
    'Toxicology data show low acute toxicity but it can cause skin or eye irritation at higher concentrations. '
    'Because it is produced by ethoxylation, residual 1,4-dioxane and ethylene oxide impurities are a concern if manufacturing purification is inadequate.',
    'Surfactant; emulsifier; solubilizer',
    'Main concerns are irritation at higher in-use levels and the potential presence of ethoxylation byproducts (like 1,4-dioxane) if the raw material is poorly purified. '
    'Environmental data also point to aquatic toxicity if released untreated.',
    75.00,
    'C',
    5,
    'surfactant',
    'Regulators and eco-labels often focus on minimizing 1,4-dioxane and ethylene oxide residues from ethoxylated surfactants and on meeting biodegradability criteria. [oai_citation:0‡EWG](https://www.ewg.org/skindeep/ingredients/703425-LAURETH7/?utm_source=chatgpt.com)',
    TRUE,
    'Nonionic surfactant / emulsifier'
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
-- Lauryl Glucoside (mild sugar-based surfactant)
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
    'lauryl glucoside',
    'Lauryl Glucoside',
    'Mild, plant-derived nonionic surfactant made from fatty alcohols and glucose.',
    'Lauryl glucoside is an alkyl polyglucoside surfactant typically derived from plant oils and glucose. '
    'It is used in “gentler” cleaners and personal care products because it provides effective cleansing and foaming with a relatively mild irritation profile. '
    'Studies and assessments generally classify it as low toxicity and readily biodegradable, although some individuals may still experience irritation with concentrated products.',
    'Surfactant; cleanser; foaming agent',
    'Overall low concern at typical use levels. Can cause mild eye or skin irritation in some users at higher concentrations but lacks strong systemic toxicity or major regulatory flags.',
    90.00,
    'A',
    4,
    'surfactant',
    'Frequently appears on lists of milder surfactants in cosmetic and detergent assessments and is generally accepted by eco-label schemes when biodegradable. [oai_citation:1‡EWG](https://www.ewg.org/cleaners/substances/5852-SUBTILISIN/?utm_source=chatgpt.com)',
    TRUE,
    'Mild nonionic surfactant'
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
-- Sodium Methyl 2-Sulfolaurate (anionic surfactant)
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
    'sodium methyl2-sulfolaurate',
    'Sodium Methyl 2-Sulfolaurate',
    'Biodegradable anionic surfactant used in detergents and hard-surface cleaners.',
    'Sodium methyl 2-sulfolaurate is an anionic surfactant used in laundry and dish formulations as well as some hard-surface cleaners. '
    'It helps lift soils, provides foam and works in combination with other surfactants to improve overall detergency. '
    'Available data describe relatively low acute toxicity and good biodegradability compared with some older anionics.',
    'Surfactant; detergent',
    'Like many anionic surfactants it can cause skin and eye irritation at higher concentrations or with prolonged contact. '
    'Environmental concerns are primarily related to aquatic toxicity before biodegradation.',
    82.00,
    'B',
    3,
    'surfactant',
    'Considered a newer, more biodegradable anionic surfactant class; not subject to specific bans but still evaluated under general surfactant and environmental regulations.',
    TRUE,
    'Primary anionic surfactant'
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
-- E570 Fatty Acids (mixture)
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
    'fatty acids (e570)',
    'Fatty Acids',
    'Mixture of fatty acids used as processing aids, emulsifiers or surfactant backbones.',
    'E570 refers to a mixture of fatty acids, typically derived from natural fats and oils. '
    'In cleaners and other consumer products these mixtures can act as processing aids, surfactant backbones or mild emulsifiers. '
    'Individual fatty acids used in foods and cosmetics have long safety histories and low systemic toxicity at normal use levels.',
    'Processing aid; emulsifier; surfactant backbone',
    'Safety depends on the exact composition and impurities, but common saturated and unsaturated fatty acids are generally low concern. '
    'Oxidation products or contaminants can contribute to irritation or off-odors.',
    86.00,
    'B',
    3,
    'emollient / surfactant backbone',
    'Approved as food additive E570 in many jurisdictions with purity criteria; cosmetic and detergent uses rely on general fatty acid safety evaluations.',
    TRUE,
    'Emulsifier / backbone for surfactants'
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
-- C8–C18 and C18 unsaturated components (fatty mixture)
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
    'c8-c18 and c18 unsatd',
    'C8–C18 and C18 unsaturated components',
    'Mixture of medium- and long-chain fatty components, often from plant oils, used in surfactant or detergent bases.',
    'This term typically describes a mixture of C8–C18 saturated and C18 unsaturated fatty derivatives originating from plant or animal oils. '
    'Such mixtures are used as building blocks for surfactants, as conditioning agents or as part of detergent bases. '
    'Toxicity is generally low and similar to other common fatty acid–derived materials.',
    'Surfactant backbone; fatty component',
    'Overall low systemic concern when well refined. Oxidation, rancidity or residual contaminants can contribute to irritation or off-odors but serious toxicity is not expected at normal use levels.',
    84.00,
    'B',
    2,
    'surfactant backbone',
    'Regulated under general rules for fatty substances in cosmetics and detergents; specific limits may apply for impurities like pesticides or process residues.',
    TRUE,
    'Fatty backbone in surfactant systems'
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
-- Generic Fragrance (mixture)
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
    'fragrance',
    'Fragrance',
    'Umbrella term for proprietary mixtures of aromatic chemicals used to scent products.',
    '“Fragrance” on a label usually represents a complex blend of natural and synthetic aroma chemicals, solvents and stabilizers. '
    'It is used purely to provide or mask scent and does not contribute directly to cleaning performance. '
    'Because suppliers often treat exact compositions as trade secrets, consumers and clinicians may not know which specific allergens or sensitizers are present.',
    'Fragrance; masking agent',
    'Fragrance mixtures are a leading cause of contact allergy and sensitization. '
    'Lack of full disclosure, potential for respiratory irritation and the presence of certain phthalates or sensitizing terpenes are key concerns, even at low percentages. [oai_citation:2‡AspenClean](https://aspenclean.com/blogs/sustainable-living/what-is-dioxane?utm_source=chatgpt.com)',
    45.00,
    'F',
    5,
    'fragrance / mixture',
    'Many jurisdictions require labeling of certain named fragrance allergens above thresholds, but generic “fragrance” declarations still allow large proprietary mixtures.',
    TRUE,
    'Scent / odor masking only'
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
-- Sodium Citrate (E331)
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
    'sodium citrates (e331)',
    'Sodium Citrate',
    'Citrate salt used to soften water, chelate metals and buffer pH in cleaners and detergents.',
    'Sodium citrate is the trisodium salt of citric acid and is widely used in foods, cosmetics and household products. '
    'In cleaning products it binds calcium and magnesium ions (softening water), helps prevent redeposition of soils and buffers pH. '
    'Toxicology and food-additive evaluations support a low hazard profile at normal exposure levels.',
    'Chelating agent; pH buffer; water softener',
    'Generally low concern. Large oral doses can cause gastrointestinal upset, but exposure from cleaners is much lower. '
    'No major carcinogenicity or reproductive toxicity red flags at the levels used in consumer products.',
    88.00,
    'A',
    4,
    'builder / chelator',
    'Approved as food additive E331 in many regions with purity criteria; commonly permitted in detergents and cleaners as a biodegradable chelating agent.',
    TRUE,
    'Builder / chelator / pH control'
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
-- Enzyme blends (Subtilisin / Amylase / Lipase / Mannanase)
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
    'subtilisin (protese) enzyme blend',
    'Subtilisin (Protease) Enzyme Blend',
    'Enzyme blend that breaks down protein-based stains in laundry and cleaning products.',
    'Subtilisin is a protease enzyme used in detergents and some hard-surface cleaners to break down protein soils such as food, blood and grass. '
    'It is typically encapsulated or formulated at low levels to limit dust and airborne exposure. '
    'Toxicology shows very low systemic toxicity but strong evidence of respiratory and skin sensitisation in workers handling concentrated enzymes.',
    'Enzyme; stain remover',
    'Major concern is respiratory and skin sensitization, especially in occupational settings with airborne enzyme dust or aerosols. '
    'Finished consumer products are formulated to reduce this risk but sensitized individuals may still react. [oai_citation:3‡ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0887233307001671?utm_source=chatgpt.com)',
    65.00,
    'D',
    5,
    'enzyme / stain remover',
    'Handled as a respiratory sensitizer under occupational safety frameworks; consumer products typically use encapsulated forms and low inclusion levels.',
    TRUE,
    'Protein-stain removal enzyme'
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
    'Enzyme blend that helps break down starch-based residues and food stains.',
    'Amylase enzymes hydrolyze starches and are used in laundry detergents and dish products to remove carbohydrate-rich residues. '
    'As with other detergent enzymes, systemic toxicity is low but the proteins can act as powerful sensitizers if inhaled as dust or aerosols.',
    'Enzyme; stain remover',
    'Main concern is respiratory and skin sensitization from occupational exposure to concentrated enzyme preparations. '
    'Consumer risk is reduced by encapsulation and low levels but not zero for sensitized users.',
    65.00,
    'D',
    3,
    'enzyme / stain remover',
    'Treated similarly to other detergent enzymes in safety assessments, with emphasis on controlling airborne exposure in manufacturing.',
    TRUE,
    'Starch-stain removal enzyme'
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
    'Enzyme blend that targets fats and oils in greasy stains.',
    'Lipase enzymes catalyze the breakdown of fats and oils and are widely used in modern detergents to improve removal of greasy soils. '
    'Toxicity testing shows low systemic hazard but, like other enzymes, they can cause occupational respiratory sensitization if inhaled as dust or aerosols.',
    'Enzyme; stain remover',
    'Primary concern is sensitization and respiratory irritation in workers handling concentrated enzyme forms. '
    'Encapsulation and low use levels lower but do not completely eliminate risk in consumer products.',
    65.00,
    'D',
    3,
    'enzyme / stain remover',
    'Regulatory and industry guidance generally mirror those for other detergent enzymes, focusing on dust control and labeling where appropriate.',
    TRUE,
    'Fat/oil-stain removal enzyme'
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
    'Enzyme blend that breaks down certain plant gums and polysaccharides found in stains.',
    'Mannanase enzymes target mannan-type polysaccharides from foods such as guar or locust bean gum, helping detergents remove complex residues. '
    'Hazard profile is similar to other detergent enzymes: low systemic toxicity but documented potential for respiratory and skin sensitisation in occupational settings.',
    'Enzyme; stain remover',
    'Key concern is sensitization for workers exposed to concentrated enzyme dust or aerosols. '
    'Encapsulated, low-level use in consumer products is considered lower risk but still flagged for sensitive individuals.',
    65.00,
    'D',
    3,
    'enzyme / stain remover',
    'Managed under general enzyme-sensitizer frameworks; often covered by internal company exposure limits rather than ingredient-specific bans.',
    TRUE,
    'Polysaccharide-stain removal enzyme'
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
-- Tetrasodium Glutamate Diacetate (biodegradable chelator)
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
    'tetrasodium glutamate diacetate',
    'Tetrasodium Glutamate Diacetate',
    'Biodegradable chelating agent used as a greener alternative to EDTA in cleaners and personal care products.',
    'Tetrasodium glutamate diacetate (GLDA) is a chelating agent derived from L-glutamic acid used to bind hardness ions and improve cleaning performance. '
    'It has been developed as a more biodegradable and lower-toxicity alternative to traditional aminopolycarboxylate chelators such as EDTA. '
    'Risk assessments describe low acute and chronic toxicity at typical use levels and favorable environmental fate compared with some older chelators. [oai_citation:4‡MakingCosmetics](https://www.makingcosmetics.com/STA-TSGD-01.html?lang=en_US&utm_source=chatgpt.com)',
    'Chelating agent; water softener',
    'Available data indicate low irritation and low systemic toxicity. Environmental concerns are mainly related to nutrient loading at very high releases, but it is readily biodegradable.',
    85.00,
    'B',
    4,
    'builder / chelator',
    'Frequently appears on “safer choice” and eco-label ingredient lists as an alternative to EDTA-type chelators, with emphasis on biodegradability and lower aquatic toxicity.',
    TRUE,
    'Biodegradable chelator / builder'
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
-- Glycerin / Glycerol (E422)
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
    'glycerol (e422)',
    'Glycerin',
    'Simple polyol used as a humectant and co-solvent in many consumer products.',
    'Glycerin (glycerol) is a three-carbon polyol used in foods, cosmetics, pharmaceuticals and household cleaners. '
    'It attracts and holds water (humectant), can dissolve some ingredients and helps stabilize formulations. '
    'Extensive use-history and toxicology show low acute and chronic toxicity at typical exposure levels.',
    'Humectant; solvent; viscosity modifier',
    'Generally low concern; high concentrations can be sticky and may cause mild irritation in some individuals. '
    'Impurities from poor-quality sources are a greater concern than glycerin itself.',
    90.00,
    'A',
    3,
    'solvent / humectant',
    'Recognized as food additive E422 and widely used in cosmetics and drugs with established specifications and purity criteria.',
    TRUE,
    'Humectant / co-solvent'
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
-- Bergamot Fruit Oil (essential oil)
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
    'citrus aurantium bergamia (bergamot) fruit oil',
    'Citrus Aurantium Bergamia (Bergamot) Fruit Oil',
    'Essential oil used for fragrance with known phototoxic and allergy concerns if not properly controlled.',
    'Bergamot oil is an essential oil expressed or distilled from the peel of Citrus bergamia fruit. '
    'It provides a characteristic citrus-floral scent and is used in perfumes and fragranced products. '
    'The oil naturally contains furocoumarins such as bergapten that can cause phototoxic reactions when skin is exposed to UV light, '
    'unless a “furocoumarin-free” grade is used. It can also trigger fragrance allergies in sensitive individuals. [oai_citation:5‡Branch Basics](https://branchbasics.com/blogs/cleaning/natural-surfactants?srsltid=AfmBOoqIOGHFO651OPKcYs441YGZJiXzlionJlEvrPjcDz0buMRDWBEz&utm_source=chatgpt.com)',
    'Fragrance; masking agent',
    'Phototoxicity on sun-exposed skin if non–furocoumarin-free oils are used in leave-on products. '
    'Also contributes to general fragrance allergy load and potential irritation.',
    55.00,
    'D',
    4,
    'fragrance / essential oil',
    'IFRA and cosmetic guidance place strict limits on bergapten-containing bergamot oil in leave-on products; rinse-off cleaners are less constrained but still monitored.',
    TRUE,
    'Fragrance note in scented cleaners'
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
-- Cedarwood Oil (essential oil)
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
    'juniperus virginiana (cedarwood) oil',
    'Juniperus Virginiana (Cedarwood) Oil',
    'Essential oil providing woody fragrance notes in cleaners and air-care products.',
    'Cedarwood oil from Juniperus virginiana provides a warm, woody scent and is used in fragrances and fragranced cleaners. '
    'Its main use is olfactory, not cleaning performance. Most data point to low systemic toxicity at typical levels, '
    'but, as with other essential oils, it can cause skin irritation or allergic reactions in susceptible individuals.',
    'Fragrance; masking agent',
    'Contributes to overall fragrance-sensitization burden. Undiluted oil can irritate skin and eyes; normal product levels are much lower but still may bother sensitive users.',
    70.00,
    'C',
    3,
    'fragrance / essential oil',
    'Falls under general essential oil and fragrance-allergen guidance; specific use limits may be set by IFRA standards for certain product categories.',
    TRUE,
    'Woody fragrance note'
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
-- Calcium Chloride (E509)
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
    'calcium chloride (e509)',
    'Calcium Chloride',
    'Inorganic salt used for moisture control, stabilization and as a functional salt in some cleaning and food applications.',
    'Calcium chloride is a calcium salt used in de-icing, moisture control, food processing and occasionally in cleaning products. '
    'In cleaners it can help control moisture, adjust ionic strength or stabilize formulations. '
    'Toxicology indicates low systemic toxicity but it is hygroscopic and solutions can be irritating to skin, eyes and mucous membranes at higher concentrations.',
    'Stabilizer; moisture control; functional salt',
    'Contact with concentrated solutions or dust can irritate or burn eyes and skin. Ingestion of large amounts may disturb electrolyte balance.',
    82.00,
    'B',
    3,
    'functional salt',
    'Approved as food additive E509 in many jurisdictions with purity standards; cleaning uses follow general chemical classification for irritation and corrosivity based on concentration.',
    TRUE,
    'Moisture control / stabilizer'
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
-- Sodium Chloride (salt)
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
    'salt',
    'Sodium Chloride',
    'Common salt used to control viscosity, act as a builder and adjust ionic strength in cleaners.',
    'Sodium chloride (NaCl) is common salt, used in cleaners and personal care products to adjust viscosity, support cleaning performance and stabilize formulations. '
    'It is abundant in the environment and has an extensive safety record as a food and cosmetic ingredient.',
    'Builder; viscosity modifier; functional salt',
    'Generally low concern at typical use levels. Very concentrated solutions or repeated exposure can dry or irritate skin and eyes; high oral intake has well-known cardiovascular implications but cleaner exposure is much lower.',
    88.00,
    'A',
    3,
    'functional salt / builder',
    'Widely approved for food and cosmetic uses; cleaning classifications focus mainly on irritation at high concentrations.',
    TRUE,
    'Viscosity modifier / builder'
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
-- Sodium Sulfate
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
    'sodium sulfate',
    'Sodium Sulfate',
    'Inorganic salt often used as a filler or builder in powder detergents and some cleaners.',
    'Sodium sulfate is an inorganic salt that can act as a filler, process aid or builder in detergents and cleaners. '
    'It is relatively inert in many formulations and mainly influences physical properties such as bulk density and flow.',
    'Builder; filler; processing aid',
    'Generally low toxicological concern. Dust from powders can irritate eyes and airways; environmental and systemic toxicity are low at typical consumer use levels.',
    82.00,
    'B',
    2,
    'functional salt / builder',
    'Regulated under generic mineral salt guidelines; not flagged as a high-concern ingredient but managed for dust and irritation in occupational settings.',
    TRUE,
    'Filler / builder in detergents'
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
-- Potassium Hydroxide (strong base)
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
    'potassium hydroxide (e525)',
    'Potassium Hydroxide',
    'Strong alkali used to saponify fats and adjust pH in cleaners and soaps.',
    'Potassium hydroxide (KOH) is a highly alkaline inorganic base used to make liquid soaps, degreasers and drain openers, and as a pH adjuster. '
    'In raw form and concentrated solutions it is strongly corrosive to skin, eyes and mucous membranes. '
    'In finished products it is often consumed in saponification reactions or neutralized, which dramatically lowers hazard, but the ingredient itself remains a high-concern caustic.',
    'pH adjuster; saponification agent',
    'Concentrated KOH can cause severe chemical burns and eye damage; ingestion is medical emergency level. '
    'Finished consumer products must be carefully formulated and labeled to avoid corrosive end-use conditions.',
    35.00,
    'F',
    4,
    'strong base / pH adjuster',
    'Classified as corrosive under GHS at higher concentrations. Food additive E525 is limited to tightly controlled levels and uses.',
    TRUE,
    'Alkaline pH adjuster / soap-making base'
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
-- Methylisothiazolinone (MI) preservative
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
    'methylisothiazolinone',
    'Methylisothiazolinone',
    'Potent isothiazolinone preservative with a well-documented history of contact allergy.',
    'Methylisothiazolinone (MI) is a powerful biocidal preservative used to control microbial growth in water-based products, including cleaners and some cosmetics. '
    'It is effective at low parts-per-million levels but has become one of the most frequent causes of allergic contact dermatitis following widespread use in leave-on and rinse-off products. [oai_citation:6‡Liebert Publishing](https://www.liebertpub.com/doi/10.1097/DER.0000000000000537?utm_source=chatgpt.com)',
    'Preservative; biocide',
    'Strong skin sensitizer. Regulatory bodies have restricted or banned MI in many leave-on cosmetics and tightened limits in rinse-off products due to high rates of allergy. '
    'Airborne and occupational exposures also raise concern.',
    25.00,
    'F',
    6,
    'preservative / biocide',
    'The EU has banned MI in leave-on cosmetics and significantly restricted use in rinse-off products; other jurisdictions have issued similar guidance or concentration limits. [oai_citation:7‡Liebert Publishing](https://www.liebertpub.com/doi/10.1097/DER.0000000000000537?utm_source=chatgpt.com)',
    TRUE,
    'High-strength preservative (discouraged in safer products)'
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
-- Benzisothiazolinone (BIT) preservative
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
    'benzisothiazolinone',
    'Benzisothiazolinone',
    'Isothiazolinone preservative used in paints and cleaning products with sensitization concerns.',
    'Benzisothiazolinone (BIT) is an isothiazolinone preservative widely used in paints, adhesives and household cleaning products to prevent microbial growth. '
    'Toxicological and regulatory reviews identify it as a skin and eye irritant and potent sensitizer, with documented cases of allergic contact dermatitis and systemic reactions. [oai_citation:8‡Wikipedia](https://en.wikipedia.org/wiki/Benzisothiazolinone)',
    'Preservative; biocide',
    'BIT is classified as an irritant and skin sensitizer and is toxic to aquatic life. '
    'Consumer exposure from cleaners is lower than for workers handling concentrated products, but allergy risk remains for sensitized individuals.',
    25.00,
    'F',
    6,
    'preservative / biocide',
    'The EU has concluded that BIT has a sensitizing potential of concern; it is not permitted in cosmetics there and is subject to strict classification and labeling in other uses. [oai_citation:9‡Wikipedia](https://en.wikipedia.org/wiki/Benzisothiazolinone)',
    TRUE,
    'High-strength preservative (discouraged in safer products)'
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
