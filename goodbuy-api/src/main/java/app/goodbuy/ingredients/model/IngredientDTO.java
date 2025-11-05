package app.goodbuy.ingredients.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class IngredientDTO {
    public Long id;
    public String canonicalKey;
    public String displayName;
    public String summary;
    public String description;
    public String func;              // iOS: funcUse
    public String concerns;
    public BigDecimal safetyScore;
    public String ratingLetter;
    public Integer referencesCount;
    public String category;
    public String regulationNotes;
    public boolean isActive;
    public List<String> tags;        // entity: List<String>
    public List<String> aliases;     // entity: List<IngredientAlias> -> List<String>
    public OffsetDateTime updatedAt;

    public static IngredientDTO of(Ingredient e) {
        if (e == null) return null;

        IngredientDTO dto = new IngredientDTO();
        dto.id = e.getId();
        dto.canonicalKey = e.getCanonicalKey();
        dto.displayName = e.getDisplayName();
        dto.summary = e.getSummary();
        dto.description = e.getDescription();
        dto.func = e.getFuncUse();
        dto.concerns = e.getConcerns();
        dto.safetyScore = e.getSafetyScore();
        dto.ratingLetter = e.getRatingLetter();
        dto.referencesCount = e.getReferencesCount();
        dto.category = e.getCategory();
        dto.regulationNotes = e.getRegulationNotes();
        dto.isActive = e.isActive();
        dto.updatedAt = e.getUpdatedAt();

        // tags: already List<String>
        dto.tags = safeList(e.getTags()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        // aliases: List<IngredientAlias> -> List<String> (alias text)
        dto.aliases = safeList(e.getAliases()).stream()
                .filter(Objects::nonNull)
                .map(IngredientAlias::getAlias)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(Comparator.comparing(String::toString, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return dto;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
