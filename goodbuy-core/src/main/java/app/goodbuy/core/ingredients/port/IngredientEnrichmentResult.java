package app.goodbuy.core.ingredients.port;

import java.util.List;

public record IngredientEnrichmentResult(
        boolean enriched,
        String displayName,
        String summary,
        String description,
        String functionUse,
        String concerns,
        String category,
        String regulationNotes,
        Integer referencesCount,
        List<String> aliases,
        List<String> tags,
        List<String> sourceUrls,
        String provider,
        String citationTitle,
        String note,

        // ✅ Signals (persisted into ingredient_signals)
        // Keep nullable for "unknown".
        Integer iarcGroup,
        Boolean prop65Listed,
        Integer ewgScore,
        Boolean euProhibited,
        Boolean euRestricted,
        Boolean pubchemMutagen,
        Boolean pubchemReproductiveToxin,
        Boolean epaChronicToxicity,
        Boolean skinIrritant
) { }
