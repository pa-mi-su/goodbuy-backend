package app.goodbuy.products.view;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProductGuidanceSignals {

    private ProductGuidanceSignals() {}

    static ProductGuidanceSummary build(
            String domain,
            String productName,
            String category,
            String scoringStatus,
            BigDecimal safetyScore,
            String ratingLetter,
            List<ProductIngredientView> ingredients
    ) {
        List<String> ingredientNames = ingredients == null ? List.of() : ingredients.stream()
                .map(ProductIngredientView::name)
                .filter(v -> v != null && !v.isBlank())
                .toList();

        String haystack = normalize(String.join(" | ",
                safe(productName),
                safe(category),
                String.join(" | ", ingredientNames)));

        Set<String> seen = new LinkedHashSet<>();
        List<ProductGuidanceSignalView> out = new ArrayList<>();

        addOverallSignal(out, seen, ratingLetter, safetyScore, scoringStatus);

        if (containsAny(haystack, "fragrance", "parfum", "perfume")) {
            add(out, seen, "Watch fragrance", "watch", "ingredient");
        }

        if (containsAny(haystack, "ptfe", "fluoropolymer", "perfluoro", "polyfluoro", "stain resistant", "waterproof", "non-stick")) {
            add(out, seen, "Possible PFAS-style treatment", "caution", "category");
        }

        if (containsAny(haystack, "phthalate", "paraben", "triclosan", "bpa", "bht", "bha", "oxybenzone")) {
            add(out, seen, "Hormone-related concern", "caution", "ingredient");
        }

        if (containsAny(haystack, "retinol", "retinyl", "boric acid", "phthalate", "oxybenzone")) {
            add(out, seen, "Pregnancy caution", "caution", "ingredient");
        }

        if (containsAny(haystack, "red 40", "red 40 lake", "yellow 5", "yellow 6", "blue 1", "blue 2", "fd&c")) {
            add(out, seen, "Color additives present", "watch", "ingredient");
        }

        if (hasSugarHeavyProfile(ingredientNames)) {
            add(out, seen, "High added sugar", "watch", "ingredient");
        }

        boolean cleaningDomain = normalize(domain).contains("cleaning");
        if (cleaningDomain && containsAny(haystack, "spray", "aerosol", "mist")) {
            add(out, seen, "Spray inhalation risk", "caution", "category");
        }

        if (cleaningDomain && containsAny(haystack, "bleach", "sodium hypochlorite", "lye", "ammonia", "hydrochloric acid")) {
            add(out, seen, "Corrosive cleaner", "caution", "ingredient");
        }

        if (cleaningDomain && containsAny(haystack, "bleach", "ammonia", "quat", "benzalkonium", "disinfectant", "fragrance", "spray")) {
            add(out, seen, "Use extra care around babies", "caution", "category");
        } else if (!cleaningDomain && hasSugarHeavyProfile(ingredientNames) && containsAny(haystack, "gummy", "chew", "fruit", "flavor", "flavours", "flavors")) {
            add(out, seen, "Better for adults than babies", "watch", "category");
        }

        if (containsAny(haystack, "limonene", "linalool", "citral", "geraniol", "coumarin")) {
            add(out, seen, "Allergen-sensitive users may want a closer look", "watch", "ingredient");
        }

        String confidence = confidence(scoringStatus, ingredients);

        return new ProductGuidanceSummary(out.stream().limit(4).toList(), confidence);
    }

    private static void addOverallSignal(
            List<ProductGuidanceSignalView> out,
            Set<String> seen,
            String ratingLetter,
            BigDecimal safetyScore,
            String scoringStatus
    ) {
        String letter = normalize(ratingLetter).toUpperCase(Locale.ROOT);
        int score = safetyScore == null ? -1 : safetyScore.intValue();

        if ("A".equals(letter) || "A+".equals(letter) || score >= 88) {
            add(out, seen, "Great for everyday", "positive", "ingredient");
            return;
        }
        if ("B".equals(letter) || score >= 74) {
            add(out, seen, "Pretty solid overall", "positive", "ingredient");
            return;
        }
        if ("C".equals(letter) || score >= 58) {
            add(out, seen, "Worth a closer look", "watch", "ingredient");
            return;
        }
        if ("D".equals(letter) || "F".equals(letter) || (score >= 0 && score < 58)) {
            add(out, seen, "Some tradeoffs to weigh", "caution", "ingredient");
            return;
        }
        if ("pending_ingredients".equalsIgnoreCase(scoringStatus) || "limited_ingredient_coverage".equalsIgnoreCase(scoringStatus)) {
            add(out, seen, "Guidance is still filling in", "info", "limited");
        }
    }

    private static boolean hasSugarHeavyProfile(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return false;
        }
        int limit = Math.min(3, ingredientNames.size());
        for (int i = 0; i < limit; i++) {
            String normalized = normalize(ingredientNames.get(i));
            if (containsAny(normalized, "sugar", "syrup", "corn syrup", "glucose", "fructose", "honey", "tapioca syrup")) {
                return true;
            }
        }
        return false;
    }

    private static String confidence(String scoringStatus, List<ProductIngredientView> ingredients) {
        long total = ingredients == null ? 0 : ingredients.size();
        long scored = ingredients == null ? 0 : ingredients.stream()
                .filter(i -> i.ratingLetter() != null && !i.ratingLetter().isBlank() && i.safetyScore() != null)
                .count();
        double coverage = total == 0 ? 0d : scored / (double) total;

        if ("scored".equalsIgnoreCase(scoringStatus) && coverage >= 0.85d) {
            return "high";
        }
        if (coverage >= 0.60d) {
            return "medium";
        }
        return "limited";
    }

    private static void add(List<ProductGuidanceSignalView> out, Set<String> seen, String label, String tone, String basis) {
        if (label == null || label.isBlank()) {
            return;
        }
        String key = normalize(label);
        if (seen.add(key)) {
            out.add(new ProductGuidanceSignalView(label, tone, basis));
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && haystack.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    record ProductGuidanceSummary(
            List<ProductGuidanceSignalView> signals,
            String confidence
    ) {}
}
