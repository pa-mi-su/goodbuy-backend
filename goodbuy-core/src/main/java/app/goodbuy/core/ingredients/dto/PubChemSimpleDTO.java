package app.goodbuy.core.ingredients.dto;

import java.util.List;

/**
 * Super simple, human-facing PubChem view.
 * No nerdy fields, just stuff we can show to normal people.
 */
public record PubChemSimpleDTO(
        Integer cid,
        String pubchemUrl,
        String displayName,      // "Ethanol"
        List<String> commonNames, // ["ethyl alcohol", "grain alcohol", ...]
        String plainSummary,      // 1–2 sentence description
        List<String> mainHazards  // Short human sentences: "Highly flammable", etc.
) {}
