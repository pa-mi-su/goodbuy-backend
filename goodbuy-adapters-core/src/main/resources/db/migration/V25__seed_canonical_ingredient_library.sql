INSERT INTO ingredients (
    canonical_key,
    display_name,
    summary,
    description,
    category,
    is_active
)
SELECT v.canonical_key,
       v.display_name,
       v.summary,
       v.description,
       v.category,
       TRUE
FROM (
    VALUES
        ('water', 'Water', 'Purified water used as a base ingredient.', 'Water is commonly used as a solvent or carrier ingredient in food, supplements, and personal care products.', 'base ingredient'),
        ('gelatin', 'Gelatin', 'Protein derived from animal collagen, often used in capsules and gummies.', 'Gelatin helps form capsules, gummies, and soft textures in supplements and foods.', 'gelling agent'),
        ('glycerin', 'Glycerin', 'Humectant that helps retain moisture.', 'Glycerin is a common moisture-retaining ingredient in supplements, personal care products, and foods.', 'humectant'),
        ('maltodextrin', 'Maltodextrin', 'Processed starch used as a filler or stabilizer.', 'Maltodextrin is commonly used to bulk, carry, or stabilize ingredients in foods and supplements.', 'filler'),
        ('calcium carbonate', 'Calcium Carbonate', 'Mineral ingredient used as a calcium source and white color additive.', 'Calcium carbonate is used in supplements and foods as a calcium source, bulking agent, and white pigment.', 'mineral'),
        ('microcrystalline cellulose', 'Microcrystalline Cellulose', 'Purified cellulose used as a tablet binder and filler.', 'Microcrystalline cellulose helps hold tablets together and improve consistency in supplements and medicines.', 'binder'),
        ('ascorbic acid', 'Ascorbic Acid', 'Vitamin C ingredient used for nutrition and antioxidant support.', 'Ascorbic acid is vitamin C and is used in supplements and foods for nutritional and antioxidant purposes.', 'vitamin'),
        ('magnesium oxide', 'Magnesium Oxide', 'Magnesium source commonly used in supplements.', 'Magnesium oxide is a mineral ingredient used to supply magnesium and sometimes as a processing aid.', 'mineral'),
        ('ferrous fumarate', 'Ferrous Fumarate', 'Iron source commonly used in supplements.', 'Ferrous fumarate is an iron ingredient used in fortified foods and supplements.', 'mineral'),
        ('dl-alpha-tocopherol acetate', 'Vitamin E Acetate', 'Vitamin E ingredient used in supplements.', 'Vitamin E acetate is a stable form of vitamin E used in supplements and some topical products.', 'vitamin'),
        ('niacinamide', 'Niacinamide', 'Vitamin B3 ingredient used in supplements and skin care.', 'Niacinamide is a form of vitamin B3 used in supplements and personal care products.', 'vitamin'),
        ('silicon dioxide', 'Silicon Dioxide', 'Anti-caking ingredient that helps powders flow.', 'Silicon dioxide helps prevent clumping in powdered and tablet products.', 'anti-caking agent'),
        ('titanium dioxide', 'Titanium Dioxide', 'White pigment and opacifier used in some coatings.', 'Titanium dioxide is used to whiten or opacify capsules, tablets, and personal care products.', 'color additive'),
        ('magnesium stearate', 'Magnesium Stearate', 'Lubricant used in tablet manufacturing.', 'Magnesium stearate helps powders move through manufacturing equipment and keeps tablets from sticking.', 'processing aid'),
        ('stearic acid', 'Stearic Acid', 'Fatty acid used as a lubricant or stabilizer.', 'Stearic acid is used in supplements and cosmetics to improve texture and manufacturing performance.', 'processing aid'),
        ('hypromellose', 'Hypromellose', 'Cellulose-based coating and capsule ingredient.', 'Hypromellose is a plant-derived capsule shell and coating ingredient.', 'capsule ingredient'),
        ('povidone', 'Povidone', 'Binder used in tablets and coatings.', 'Povidone helps ingredients bind together in tablets and coated products.', 'binder'),
        ('crospovidone', 'Crospovidone', 'Disintegrant used to help tablets break apart.', 'Crospovidone helps tablets dissolve after swallowing.', 'disintegrant'),
        ('croscarmellose sodium', 'Croscarmellose Sodium', 'Disintegrant used in tablets.', 'Croscarmellose sodium helps tablets break apart and dissolve more reliably.', 'disintegrant'),
        ('polyethylene glycol', 'Polyethylene Glycol', 'Processing and coating ingredient.', 'Polyethylene glycol is used in coatings, capsules, and processing systems.', 'processing aid'),
        ('soy lecithin', 'Soy Lecithin', 'Emulsifier derived from soy.', 'Soy lecithin helps oil- and water-based ingredients mix more evenly.', 'emulsifier'),
        ('lecithin', 'Lecithin', 'Emulsifier used to keep ingredients blended.', 'Lecithin helps keep mixtures stable and evenly dispersed.', 'emulsifier'),
        ('red 40 lake', 'Red 40 Lake', 'Synthetic color additive.', 'Red 40 lake is a synthetic color additive used in coated tablets, gummies, and foods.', 'color additive'),
        ('yellow 6 lake', 'Yellow 6 Lake', 'Synthetic color additive.', 'Yellow 6 lake is a synthetic color additive used in tablets, gummies, and foods.', 'color additive'),
        ('blue 2 lake', 'Blue 2 Lake', 'Synthetic color additive.', 'Blue 2 lake is a synthetic color additive used in coated products and foods.', 'color additive'),
        ('natural flavors', 'Natural Flavors', 'Flavoring mixture derived from natural sources.', 'Natural flavors are flavoring mixtures that can contain multiple source ingredients.', 'flavor'),
        ('artificial flavors', 'Artificial Flavors', 'Flavoring mixture made from synthetic flavor compounds.', 'Artificial flavors are flavoring mixtures built from synthetic flavor compounds.', 'flavor'),
        ('fragrance', 'Fragrance', 'Mixture used to add scent.', 'Fragrance is a scent mixture that may contain multiple components and is often not fully disclosed.', 'fragrance'),
        ('talc', 'Talc', 'Mineral powder used for absorbency and texture.', 'Talc is a mineral ingredient used in powders and some cosmetics.', 'mineral')
) AS v(canonical_key, display_name, summary, description, category)
WHERE NOT EXISTS (
    SELECT 1 FROM ingredients i WHERE lower(i.canonical_key) = lower(v.canonical_key)
);

INSERT INTO ingredient_alias (ingredient_id, alias)
SELECT i.id, v.alias
FROM (
    VALUES
        ('water', 'aqua'),
        ('water', 'purified water'),
        ('gelatin', 'gelatine'),
        ('ascorbic acid', 'vitamin c'),
        ('ascorbic acid', 'l-ascorbic acid'),
        ('niacinamide', 'nicotinamide'),
        ('dl-alpha-tocopherol acetate', 'vitamin e acetate'),
        ('dl-alpha-tocopherol acetate', 'tocopheryl acetate'),
        ('calcium carbonate', 'e170'),
        ('calcium carbonate', 'e170-i'),
        ('microcrystalline cellulose', 'cellulose gel'),
        ('silicon dioxide', 'silica'),
        ('titanium dioxide', 'tio2'),
        ('magnesium stearate', 'vegetable magnesium stearate'),
        ('hypromellose', 'hydroxypropyl methylcellulose'),
        ('povidone', 'polyvinylpyrrolidone'),
        ('croscarmellose sodium', 'crosscarmellose sodium'),
        ('polyethylene glycol', 'peg'),
        ('soy lecithin', 'lecithin (soy)'),
        ('lecithin', 'sunflower lecithin'),
        ('red 40 lake', 'fd&c red no. 40 aluminum lake'),
        ('yellow 6 lake', 'fd&c yellow no. 6 aluminum lake'),
        ('blue 2 lake', 'fd&c blue no. 2 aluminum lake'),
        ('natural flavors', 'natural flavor'),
        ('artificial flavors', 'artificial flavor'),
        ('fragrance', 'parfum')
) AS v(canonical_key, alias)
JOIN ingredients i
  ON lower(i.canonical_key) = lower(v.canonical_key)
ON CONFLICT (ingredient_id, alias) DO NOTHING;
