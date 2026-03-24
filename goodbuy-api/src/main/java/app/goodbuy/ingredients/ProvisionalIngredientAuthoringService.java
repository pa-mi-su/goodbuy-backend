package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.core.ingredients.scoring.IngredientScoringEngine;
import app.goodbuy.core.ingredients.scoring.IngredientSignals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ProvisionalIngredientAuthoringService {

    private static final IngredientSignals EMPTY_SIGNALS = IngredientSignals.empty();

    private final IngredientScoringEngine scoringEngine = new IngredientScoringEngine();

    public void applyProvisionalProfile(Ingredient ingredient, String rawQuery, String canonicalKey) {
        if (ingredient == null) {
            return;
        }

        String displayName = firstNonBlank(ingredient.getDisplayName(), rawQuery, canonicalKey);
        String normalized = normalizeNeedle(firstNonBlank(canonicalKey, displayName));
        ProvisionalProfile profile = inferProfile(displayName, normalized);

        if (isBlank(ingredient.getDisplayName())) {
            ingredient.setDisplayName(displayName);
        }
        if (isBlank(ingredient.getSummary())) {
            ingredient.setSummary(profile.summary());
        }
        if (isBlank(ingredient.getDescription())) {
            ingredient.setDescription(profile.description());
        }
        if (isBlank(ingredient.getFuncUse())) {
            ingredient.setFuncUse(profile.funcUse());
        }
        if (isBlank(ingredient.getConcerns())) {
            ingredient.setConcerns(profile.concerns());
        }
        if (isBlank(ingredient.getCategory())) {
            ingredient.setCategory(profile.category());
        }
        if (isBlank(ingredient.getRegulationNotes())) {
            ingredient.setRegulationNotes("GoodBuy provisional read. We created this ingredient automatically from a product scan and will refine it as more evidence is gathered.");
        }
        if (ingredient.getReferencesCount() == null) {
            ingredient.setReferencesCount(0);
        }
        if (ingredient.getTags() == null || ingredient.getTags().isEmpty()) {
            ingredient.setTags(new ArrayList<>(profile.tags()));
        }
        if (ingredient.getSafetyScore() == null || isBlank(ingredient.getRatingLetter())) {
            ingredient.setSafetyScore(BigDecimal.valueOf(profile.score().safetyScore()));
            ingredient.setRatingLetter(profile.score().ratingLetter());
        }

        ensureAlias(ingredient, displayName);
        if (!normalized.isBlank() && !normalized.equalsIgnoreCase(displayName)) {
            ensureAlias(ingredient, normalized);
        }
    }

    private ProvisionalProfile inferProfile(String displayName, String normalized) {
        IngredientScoreResult curated = scoringEngine.score(normalized, EMPTY_SIGNALS);
        if (curated.isRated()) {
            if (isWater(normalized)) {
                return profile(
                        "solvent",
                        "water solvent",
                        "Primary carrier ingredient used to dissolve or carry the rest of the formula.",
                        displayName + " is mainly acting as the base liquid or carrier in this product.",
                        "Generally low concern in normal consumer use.",
                        curated,
                        List.of("solvent", "base ingredient")
                );
            }
            if (isVitaminLike(normalized)) {
                return profile(
                        "vitamin",
                        "nutrient ingredient",
                        "Nutrient ingredient used to supply a vitamin, mineral, or other supplement component.",
                        displayName + " is being used here as part of the product's nutrient or supplement blend.",
                        "Generally lower concern in normal product use, though dose and context still matter.",
                        curated,
                        List.of("vitamin", "nutrient")
                );
            }
            if (isColorAdditive(normalized)) {
                return profile(
                        "color additive",
                        "color additive",
                        "Color ingredient added mainly to change or standardize appearance.",
                        displayName + " is likely being used for color, coating, or visual consistency.",
                        "Color additives can be preference-sensitive and sometimes draw extra scrutiny.",
                        curated,
                        List.of("color additive")
                );
            }
            if (isFragranceLike(normalized)) {
                return profile(
                        "fragrance",
                        "fragrance ingredient",
                        "Fragrance-related ingredient used mainly for scent or flavor masking.",
                        displayName + " is likely contributing scent, flavor, or odor masking rather than core nutrition.",
                        "Fragrance-style ingredients are often less transparent than single-purpose ingredients.",
                        curated,
                        List.of("fragrance")
                );
            }
            return profile(
                    "ingredient",
                    "formula ingredient",
                    "Known ingredient in the formula with a provisional GoodBuy read.",
                    displayName + " is in the formula and has a first-pass GoodBuy profile while we continue to build out the full record.",
                    "This is an automatic plain-English read based on the ingredient name and current rules.",
                    curated,
                    List.of("provisional")
            );
        }

        if (isVitaminLike(normalized)) {
            return profile(
                    "vitamin",
                    "nutrient ingredient",
                    "Nutrient ingredient used to supply a vitamin, mineral, or supplement component.",
                    displayName + " looks like a nutrient or supplement ingredient.",
                    "Usually lower concern in normal product use, but product context still matters.",
                    new IngredientScoreResult(90, "A", List.of("Provisional rule: nutrient-style ingredient.")),
                    List.of("vitamin", "nutrient")
            );
        }
        if (isMineralLike(normalized)) {
            return profile(
                    "mineral",
                    "mineral ingredient",
                    "Mineral or salt ingredient used for nutrition, stability, or processing.",
                    displayName + " appears to be a mineral-style ingredient or inorganic compound.",
                    "Often lower concern in normal use, though amount and route still matter.",
                    new IngredientScoreResult(88, "B", List.of("Provisional rule: mineral-style ingredient.")),
                    List.of("mineral")
            );
        }
        if (isPlantLike(normalized)) {
            return profile(
                    "botanical",
                    "plant-derived ingredient",
                    "Plant-derived ingredient used for flavor, nutrient support, or formulation.",
                    displayName + " appears to be plant-derived or botanical.",
                    "Plant ingredients can vary in strength and evidence quality depending on source and use.",
                    new IngredientScoreResult(82, "B", List.of("Provisional rule: botanical ingredient.")),
                    List.of("botanical", "plant extract")
            );
        }
        if (isBinderOrExcipient(normalized)) {
            return profile(
                    "excipient",
                    "binder or stabilizer",
                    "Support ingredient used to bind, coat, stabilize, or shape the product.",
                    displayName + " looks like a support ingredient rather than the main active ingredient.",
                    "These ingredients are often functional excipients, coatings, or texture aids.",
                    new IngredientScoreResult(82, "B", List.of("Provisional rule: excipient/binder ingredient.")),
                    List.of("excipient", "stabilizer")
            );
        }
        if (isSweetener(normalized)) {
            return profile(
                    "sweetener",
                    "sweetener",
                    "Sweetening ingredient used for taste, texture, or coating.",
                    displayName + " is likely being used to sweeten or improve texture.",
                    "Sweeteners are often preference-sensitive rather than universally high concern.",
                    new IngredientScoreResult(78, "B", List.of("Provisional rule: sweetener ingredient.")),
                    List.of("sweetener")
            );
        }
        if (isColorAdditive(normalized)) {
            return profile(
                    "color additive",
                    "color additive",
                    "Color ingredient added mainly to change or standardize appearance.",
                    displayName + " appears to be used mainly for color or coating appearance.",
                    "Color additives often land in the middle because they are more preference-sensitive than essential.",
                    new IngredientScoreResult(70, "C", List.of("Provisional rule: color additive.")),
                    List.of("color additive")
            );
        }
        if (isFragranceLike(normalized)) {
            return profile(
                    "fragrance",
                    "fragrance ingredient",
                    "Fragrance-related ingredient used mainly for scent, flavor, or odor masking.",
                    displayName + " appears to be part of a scent or flavor system.",
                    "These ingredients often get a closer look because disclosure can be limited.",
                    new IngredientScoreResult(58, "D", List.of("Provisional rule: fragrance-style ingredient.")),
                    List.of("fragrance")
            );
        }
        if (isPreservativeLike(normalized)) {
            return profile(
                    "preservative",
                    "preservative",
                    "Preservative ingredient used to keep the formula stable and resist microbial growth.",
                    displayName + " is likely helping preserve the product and extend shelf life.",
                    "Preservatives vary a lot, so this first-pass read is intentionally cautious.",
                    new IngredientScoreResult(68, "C", List.of("Provisional rule: preservative-style ingredient.")),
                    List.of("preservative")
            );
        }
        if (isSurfactantLike(normalized)) {
            return profile(
                    "surfactant",
                    "cleaning or foaming ingredient",
                    "Surfactant ingredient used to clean, dissolve oils, or help the formula spread.",
                    displayName + " looks like a surfactant or cleaning support ingredient.",
                    "Surfactants can range from mild to harsher, so this is a middle-of-the-road starting read.",
                    new IngredientScoreResult(74, "B", List.of("Provisional rule: surfactant-style ingredient.")),
                    List.of("surfactant")
            );
        }

        return profile(
                "ingredient",
                "formula ingredient",
                "Ingredient listed in the product formula.",
                displayName + " is in the product, and GoodBuy created a first-pass record automatically from the scan.",
                "This ingredient is still being reviewed. The current score is a provisional starting point rather than a final verdict.",
                new IngredientScoreResult(70, "C", List.of("Provisional rule: generic formula ingredient.")),
                List.of("provisional")
        );
    }

    private ProvisionalProfile profile(
            String category,
            String funcUse,
            String summary,
            String description,
            String concerns,
            IngredientScoreResult score,
            List<String> tags
    ) {
        return new ProvisionalProfile(category, funcUse, summary, description, concerns, score, tags);
    }

    private void ensureAlias(Ingredient ingredient, String aliasValue) {
        if (ingredient == null || isBlank(aliasValue)) {
            return;
        }

        boolean exists = ingredient.getAliases().stream()
                .map(IngredientAlias::getAlias)
                .filter(v -> v != null && !v.isBlank())
                .anyMatch(v -> v.trim().equalsIgnoreCase(aliasValue.trim()));

        if (exists) {
            return;
        }

        IngredientAlias alias = new IngredientAlias();
        alias.setIngredient(ingredient);
        alias.setAlias(aliasValue.trim());
        ingredient.getAliases().add(alias);
    }

    private boolean isWater(String normalized) {
        return normalized.equals("water") || normalized.equals("aqua") || normalized.equals("purified water");
    }

    private boolean isVitaminLike(String normalized) {
        return containsAny(normalized,
                "vitamin", "folic acid", "biotin", "niacinamide", "riboflavin", "thiamine",
                "pyridoxine", "cyanocobalamin", "cholecalciferol", "retinyl", "tocopherol",
                "ascorbic acid", "pantothenate", "folate", "inositol", "dha", "epa");
    }

    private boolean isMineralLike(String normalized) {
        return containsAny(normalized,
                "oxide", "carbonate", "citrate", "fumarate", "gluconate", "sulfate", "chloride",
                "magnesium", "calcium", "zinc", "iron", "potassium", "selenium", "copper", "manganese", "iodide");
    }

    private boolean isPlantLike(String normalized) {
        return containsAny(normalized,
                "extract", "root", "leaf", "berry", "seed", "oil", "botanical", "herb", "fruit powder", "aloe", "ginger");
    }

    private boolean isBinderOrExcipient(String normalized) {
        return containsAny(normalized,
                "cellulose", "stearate", "silicon dioxide", "silica", "maltodextrin", "gelatin",
                "gum", "lecithin", "starch", "coating", "croscarmellose", "hypromellose", "glycerin", "glycerol");
    }

    private boolean isSweetener(String normalized) {
        return containsAny(normalized,
                "sucrose", "glucose", "fructose", "syrup", "sweetener", "xylitol", "sorbitol",
                "sucralose", "stevia", "aspartame", "acesulfame", "maltitol");
    }

    private boolean isColorAdditive(String normalized) {
        return containsAny(normalized,
                "color", "colour", "lake", "red 40", "yellow 5", "yellow 6", "blue 1", "blue 2",
                "titanium dioxide", "annatto", "caramel color", "fd&c");
    }

    private boolean isFragranceLike(String normalized) {
        return containsAny(normalized,
                "fragrance", "parfum", "perfume", "flavor", "flavour", "aroma", "limonene", "linalool", "citral");
    }

    private boolean isPreservativeLike(String normalized) {
        return containsAny(normalized,
                "benzoate", "sorbate", "paraben", "preservative", "hydantoin", "phenoxyethanol",
                "benzisothiazolinone", "methylisothiazolinone", "formaldehyde", "diazolidinyl", "imidazolidinyl");
    }

    private boolean isSurfactantLike(String normalized) {
        return containsAny(normalized,
                "sulfate", "sulfonate", "glucoside", "betaine", "surfactant", "laureth", "ceteareth",
                "cocamide", "ammonium lauryl", "sodium lauryl", "polysorbate");
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeNeedle(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ")
                .replace('’', '\'')
                .replace('–', '-')
                .replace('—', '-');
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record ProvisionalProfile(
            String category,
            String funcUse,
            String summary,
            String description,
            String concerns,
            IngredientScoreResult score,
            List<String> tags
    ) {}
}
