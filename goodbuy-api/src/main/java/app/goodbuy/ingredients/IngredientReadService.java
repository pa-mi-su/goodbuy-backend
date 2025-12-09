package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.IngredientMapper;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class IngredientReadService {

    private final IngredientRepository repo;
    private final IngredientMapper mapper;

    public IngredientReadService(IngredientRepository repo, IngredientMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    /**
     * Ranked single search.
     *
     * Priority:
     *  1) exact canonical_key match (case-insensitive)
     *  2) exact alias match (case-insensitive)
     *  3) loose fallback (substring match across canonical/display/aliases)
     */
    public Optional<IngredientDTO> searchRanked(String needle) {
        String q = normalizeNeedle(needle);
        if (q.isEmpty()) {
            return Optional.empty();
        }

        // 1) canonical_key (strongest signal)
        Optional<Ingredient> byCanonical = repo.findByCanonicalKeyIgnoreCase(q);
        if (byCanonical.isPresent()) {
            return byCanonical.map(mapper::toDto);
        }

        // 2) exact alias (second tier, but still quite strong)
        var aliasHits = repo.findByAliasExact(q);
        if (!aliasHits.isEmpty()) {
            Ingredient bestAlias = aliasHits.stream()
                    .sorted(Comparator.comparingLong(Ingredient::getId))
                    .findFirst()
                    .orElseThrow();
            return Optional.of(mapper.toDto(bestAlias));
        }

        // 3) loose search fallback
        var looseMatches = repo.searchLoose(q);
        if (looseMatches.isEmpty()) {
            return Optional.empty();
        }

        Ingredient best = looseMatches.stream()
                .sorted(Comparator
                        .comparingInt((Ingredient i) -> rank(i, q))
                        .thenComparingLong(Ingredient::getId))
                .findFirst()
                .orElseThrow();

        return Optional.of(mapper.toDto(best));
    }

    /**
     * Ranked batch search.
     */
    public List<IngredientDTO> searchManyRanked(List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return List.of();
        }

        List<String> qList = needles.stream()
                .map(this::normalizeNeedle)
                .filter(s -> !s.isEmpty())
                .toList();

        if (qList.isEmpty()) {
            return List.of();
        }

        var found = repo.findManyByNamesOrAliases(qList);
        if (found.isEmpty()) {
            return List.of();
        }

        return found.stream()
                .sorted(Comparator
                        .comparingInt((Ingredient i) -> bestRank(i, qList))
                        .thenComparingLong(Ingredient::getId))
                .map(mapper::toDto)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // Ranking helpers
    // ─────────────────────────────────────────────────────────────

    private int rank(Ingredient i, String q) {
        String ck = safe(i.getCanonicalKey());
        String dn = safe(i.getDisplayName());

        var aliases = (i.getAliases() == null)
                ? List.<String>of()
                : i.getAliases().stream()
                .map(a -> safe(a.getAlias()))
                .toList();

        if (ck.equals(q)) return 0;
        if (dn.equals(q)) return 1;
        if (aliases.contains(q)) return 2;

        return 99;
    }

    private int bestRank(Ingredient i, List<String> qs) {
        return qs.stream()
                .mapToInt(q -> rank(i, q))
                .min()
                .orElse(99);
    }

    private String safe(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private String normalizeNeedle(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase().trim();
    }

    // Backwards-compatible API used by IngredientController

    public Optional<IngredientDTO> findByNameOrAlias(String q) {
        return searchRanked(q);
    }

    public List<IngredientDTO> findManyByNamesOrAliases(List<String> q) {
        return searchManyRanked(q);
    }
}
