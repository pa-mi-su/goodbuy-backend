package app.goodbuy.core.ingredients.scoring;

import java.util.List;

final class CuratedIngredientRules {

    private static final List<CuratedIngredientRule> RULES = List.of(
            new CuratedIngredientRule(
                    "talc",
                    null,
                    35,
                    false,
                    List.of(
                            "Curated rule: talc is treated as high concern because IARC classifies talc as probably carcinogenic to humans.",
                            "Curated rule: talc also carries a recognized asbestos-contamination risk in consumer products."
                    ),
                    List.of("talc"),
                    List.of("talc")
            ),
            new CuratedIngredientRule(
                    "fragrance",
                    null,
                    58,
                    false,
                    List.of(
                            "Curated rule: fragrance/parfum is capped because it is a disclosure-poor mixture and common irritation/allergen source."
                    ),
                    List.of("fragrance", "parfum", "perfume", "aroma", "flavor", "artificial flavor", "natural flavor"),
                    List.of("fragrance", "parfum", "perfume", "artificial flavor", "natural flavor")
            ),
            new CuratedIngredientRule(
                    "titanium-dioxide",
                    null,
                    60,
                    false,
                    List.of(
                            "Curated rule: titanium dioxide is capped because it has inhalation-linked carcinogenic concern and regulatory scrutiny."
                    ),
                    List.of("titanium dioxide"),
                    List.of("titanium dioxide")
            ),
            new CuratedIngredientRule(
                    "bha",
                    null,
                    45,
                    false,
                    List.of(
                            "Curated rule: BHA is capped due to carcinogenic concern signals and regulatory scrutiny."
                    ),
                    List.of("bha", "butylated hydroxyanisole"),
                    List.of("butylated hydroxyanisole")
            ),
            new CuratedIngredientRule(
                    "bht",
                    null,
                    60,
                    false,
                    List.of(
                            "Curated rule: BHT is capped due to endocrine and chronic-toxicity concern signals."
                    ),
                    List.of("bht", "butylated hydroxytoluene"),
                    List.of("butylated hydroxytoluene")
            ),
            new CuratedIngredientRule(
                    "phthalates",
                    null,
                    40,
                    false,
                    List.of(
                            "Curated rule: phthalates are capped because this class is associated with reproductive and endocrine concern."
                    ),
                    List.of("dibutyl phthalate", "diethyl phthalate", "dep", "dbp"),
                    List.of("phthalate")
            ),
            new CuratedIngredientRule(
                    "formaldehyde-releasers",
                    null,
                    35,
                    false,
                    List.of(
                            "Curated rule: formaldehyde and formaldehyde-releasing preservatives are treated as high concern."
                    ),
                    List.of("formaldehyde", "dmdm hydantoin", "quaternium-15", "diazolidinyl urea", "imidazolidinyl urea"),
                    List.of("formaldehyde", "hydantoin", "quaternium-15", "diazolidinyl urea", "imidazolidinyl urea")
            ),
            new CuratedIngredientRule(
                    "peg-ethoxylated",
                    null,
                    68,
                    false,
                    List.of(
                            "Curated rule: PEG and ethoxylated ingredients are capped because contamination concerns can matter more than the base compound."
                    ),
                    List.of(),
                    List.of("peg-", "polyethylene glycol", "ceteareth-", "steareth-", "laureth-")
            ),
            new CuratedIngredientRule(
                    "oxybenzone",
                    null,
                    45,
                    false,
                    List.of(
                            "Curated rule: oxybenzone is capped due to endocrine and sensitization concern."
                    ),
                    List.of("oxybenzone", "benzophenone-3"),
                    List.of("oxybenzone", "benzophenone-3")
            ),
            new CuratedIngredientRule(
                    "triclosan",
                    null,
                    40,
                    false,
                    List.of(
                            "Curated rule: triclosan is capped due to endocrine, resistance, and regulatory concern."
                    ),
                    List.of("triclosan"),
                    List.of("triclosan")
            ),
            new CuratedIngredientRule(
                    "butyl-propyl-paraben",
                    null,
                    50,
                    false,
                    List.of(
                            "Curated rule: higher-concern parabens are capped due to endocrine concern."
                    ),
                    List.of("butylparaben", "propylparaben"),
                    List.of("butylparaben", "propylparaben")
            ),
            new CuratedIngredientRule(
                    "methyl-ethyl-paraben",
                    null,
                    68,
                    false,
                    List.of(
                            "Curated rule: methylparaben and ethylparaben remain moderate concern due to endocrine debate and regulatory pressure."
                    ),
                    List.of("methylparaben", "ethylparaben"),
                    List.of("methylparaben", "ethylparaben")
            ),
            new CuratedIngredientRule(
                    "ascorbic-acid",
                    92,
                    null,
                    true,
                    List.of(
                            "Curated rule: ascorbic acid (vitamin C) is treated as low concern for normal consumer use.",
                            "Curated rule: bare PubChem flags are ignored for this ingredient because they overstate everyday consumer exposure risk."
                    ),
                    List.of("ascorbic acid", "vitamin c", "l-ascorbic acid"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "niacinamide",
                    90,
                    null,
                    true,
                    List.of(
                            "Curated rule: niacinamide is treated as low concern for normal consumer use."
                    ),
                    List.of("niacinamide", "nicotinamide"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "calcium-carbonate",
                    92,
                    null,
                    true,
                    List.of(
                            "Curated rule: calcium carbonate is treated as low concern for normal consumer use."
                    ),
                    List.of("calcium carbonate", "e170", "calcium carbonate (ci pigment white 18, e170-i)"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "microcrystalline-cellulose",
                    90,
                    null,
                    true,
                    List.of(
                            "Curated rule: microcrystalline cellulose is treated as low concern for normal consumer use."
                    ),
                    List.of("microcrystalline cellulose", "cellulose gel"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "magnesium-oxide",
                    90,
                    null,
                    true,
                    List.of(
                            "Curated rule: magnesium oxide is treated as low concern for normal consumer use."
                    ),
                    List.of("magnesium oxide"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "ferrous-fumarate",
                    88,
                    null,
                    true,
                    List.of(
                            "Curated rule: ferrous fumarate is treated as low concern at normal consumer use levels."
                    ),
                    List.of("ferrous fumarate"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "vitamin-e-acetate",
                    88,
                    null,
                    true,
                    List.of(
                            "Curated rule: vitamin E acetate is treated as low concern for normal oral/topical consumer use."
                    ),
                    List.of("vitamin e acetate", "dl-alpha-tocopherol acetate", "tocopheryl acetate"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "gelatin",
                    85,
                    null,
                    true,
                    List.of(
                            "Curated rule: gelatin is not treated as a generalized high-concern ingredient for the average consumer."
                    ),
                    List.of("gelatin"),
                    List.of()
            ),
            new CuratedIngredientRule(
                    "water",
                    95,
                    null,
                    true,
                    List.of(
                            "Curated rule: water is treated as low concern."
                    ),
                    List.of("water", "aqua", "purified water"),
                    List.of()
            )
    );

    private CuratedIngredientRules() {
    }

    static List<CuratedIngredientRule> findAll(String canonicalKey) {
        return RULES.stream().filter(rule -> rule.matches(canonicalKey)).toList();
    }
}
