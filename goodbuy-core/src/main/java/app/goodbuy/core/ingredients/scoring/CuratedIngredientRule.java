package app.goodbuy.core.ingredients.scoring;

import java.util.List;
import java.util.Locale;

record CuratedIngredientRule(
        String id,
        Integer baseScoreOverride,
        Integer scoreCap,
        boolean ignorePubchem,
        List<String> reasons,
        List<String> exactKeys,
        List<String> containsTokens
) {

    boolean matches(String canonicalKey) {
        String normalized = normalize(canonicalKey);
        if (normalized.isBlank()) {
            return false;
        }

        return exactKeys.stream().map(CuratedIngredientRule::normalize).anyMatch(normalized::equals)
                || containsTokens.stream().map(CuratedIngredientRule::normalize).anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
