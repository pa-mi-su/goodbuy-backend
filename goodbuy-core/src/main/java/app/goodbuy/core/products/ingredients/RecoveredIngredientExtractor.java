package app.goodbuy.core.products.ingredients;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecoveredIngredientExtractor {

    private static final List<String> INGREDIENT_SECTION_MARKERS = List.of(
            "ingredients:",
            "ingredient:",
            "other ingredients:",
            "inactive ingredients:",
            "active ingredients:"
    );

    private static final List<String> SECTION_STOP_MARKERS = List.of(
            "directions",
            "warning",
            "warnings",
            "drug facts",
            "questions",
            "distributed by",
            "distribuido por",
            "made in",
            "uses",
            "uses:",
            "purpose",
            "purpose:",
            "keep out of reach",
            "safety tip",
            "tear free",
            "no more tears",
            "no parabens",
            "no phthalates",
            "phthalates or dyes",
            "sulfates or dyes",
            "gentle enough",
            "hypoallergenic",
            "compare to",
            "safety seal",
            "how to use",
            "instructions"
    );

    private RecoveredIngredientExtractor() {}

    public static List<String> extract(String rawText) {
        String panel = ingredientPanelText(rawText);
        if (panel == null) {
            return List.of();
        }
        return filterEvidenceIngredients(IngredientTextParser.parse(panel));
    }

    private static List<String> filterEvidenceIngredients(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed == null || !looksLikeIngredientCandidate(trimmed)) {
                continue;
            }
            filtered.add(trimmed);
        }
        return filtered.stream().distinct().limit(64).toList();
    }

    private static String ingredientPanelText(String rawText) {
        String text = trimToNull(rawText);
        if (text == null) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (String marker : INGREDIENT_SECTION_MARKERS) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && (start == -1 || idx < start)) {
                start = idx + marker.length();
            }
        }

        if (start < 0) {
            return null;
        }

        String panel = text.substring(Math.min(start, text.length())).trim();
        String panelLower = panel.toLowerCase(Locale.ROOT);
        int end = panel.length();
        for (String stopMarker : SECTION_STOP_MARKERS) {
            int idx = panelLower.indexOf(stopMarker);
            if (idx > 0 && idx < end) {
                end = idx;
            }
        }

        return trimToNull(panel.substring(0, end));
    }

    private static boolean looksLikeIngredientCandidate(String value) {
        String lower = value.toLowerCase(Locale.ROOT).trim();
        if (lower.isBlank()) {
            return false;
        }
        if (lower.length() > 80) {
            return false;
        }
        if (!lower.matches(".*[a-z].*")) {
            return false;
        }
        if (lower.matches(".*\\b\\d{5,}\\b.*")) {
            return false;
        }
        if (lower.matches(".*\\b\\d+(\\.\\d+)?\\s*(ml|fl\\s?oz|floz|oz|g|mg|mcg|kg|lb|lbs)\\b.*")) {
            return false;
        }
        if (containsAny(lower,
                "made in",
                "distributed by",
                "compare to",
                "safety tip",
                "only 11 ingredients",
                "only ingredients",
                "tear free",
                "no more tears",
                "hypoallergenic",
                "gentle enough",
                "wash & shampoo",
                "bath and shampoo",
                "baño y champú",
                "no parabens",
                "no phthalates",
                "sulfates or dyes",
                "j&jci",
                "questions or comments",
                "squirt a",
                "clean away",
                "increase the amount",
                "greasier items",
                "onto a sponge",
                "how to use",
                "use a little",
                "apply to"
        )) {
            return false;
        }
        if (lower.matches(".*\\b(squirt|clean|increase|apply|use|rub|rinse|wipe)\\b.*")) {
            return false;
        }
        if (lower.startsWith("no ") || lower.startsWith("free of ") || lower.startsWith("free from ")) {
            return false;
        }
        if (lower.contains("®") || lower.contains("™")) {
            return false;
        }
        return true;
    }

    private static boolean containsAny(String lower, String... needles) {
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
