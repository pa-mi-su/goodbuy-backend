package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.pubchem.PubChemClient;
import app.goodbuy.adapters.core.ingredients.pubchem.PubChemRaw;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Validated
@RestController
@RequestMapping(path = "/api/ingredients", produces = MediaType.APPLICATION_JSON_VALUE)
public class IngredientController {

    private static final Logger log = LoggerFactory.getLogger(IngredientController.class);

    private static final int BATCH_LIMIT = 200;

    private final IngredientReadService service;
    private final PubChemClient pubChemClient;

    public IngredientController(
            IngredientReadService service,
            PubChemClient pubChemClient
    ) {
        this.service = service;
        this.pubChemClient = pubChemClient;
    }

    // ───────────────────────────────────────────────────────────────
    // A) GET /api/ingredients?q=something
    // ───────────────────────────────────────────────────────────────
    @GetMapping(params = "q")
    public IngredientDTO getOneByQuery(@RequestParam("q") String q) {
        String query = normalize(q);
        log.info("GET ingredient by query: raw='{}' → normalized='{}'", q, query);

        if (query.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_ingredient_name");
        }

        return service.findByNameOrAlias(query)
                .orElseThrow(() -> {
                    log.warn("Ingredient '{}' not found (q param)", query);
                    return new ResponseStatusException(NOT_FOUND, "Ingredient not found: " + query);
                });
    }

    // ───────────────────────────────────────────────────────────────
    // B) GET /api/ingredients/{nameOrKey}
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/{nameOrKey:.+}")
    public IngredientDTO getOne(@PathVariable("nameOrKey") String nameOrKey) {
        String query = normalize(nameOrKey);
        log.info("GET ingredient by path: raw='{}' → normalized='{}'", nameOrKey, query);

        if (query.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_ingredient_name");
        }

        return service.findByNameOrAlias(query)
                .orElseThrow(() -> {
                    log.warn("Ingredient '{}' not found (path)", query);
                    return new ResponseStatusException(NOT_FOUND, "Ingredient not found: " + query);
                });
    }

    // ───────────────────────────────────────────────────────────────
    // C) POST /api/ingredients/_batch
    // ───────────────────────────────────────────────────────────────
    @PostMapping(path = "/_batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<IngredientDTO> batch(@RequestBody List<String> names) {
        if (names == null) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_request");
        }

        var cleaned = names.stream()
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(BATCH_LIMIT)
                .toList();

        log.info("BATCH lookup {} ingredients → cleaned={} entries", names.size(), cleaned.size());

        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_request");
        }

        return service.findManyByNamesOrAliases(cleaned);
    }

    // ========================================================================
    // NEW FEATURE 1 — /enriched/{name} → triggers enrichment immediately
    // ========================================================================
    @GetMapping("/enriched/{name:.+}")
    public IngredientDTO enriched(@PathVariable("name") String name) {
        String query = normalize(name);
        log.info("GET ENRICHED ingredient: '{}'", query);

        return service.findByNameOrAlias(query)
                .orElseThrow(() -> {
                    log.warn("Enriched ingredient '{}' not found after DB+PubChem lookup", query);
                    return new ResponseStatusException(NOT_FOUND,
                            "Enriched ingredient not found: " + query);
                });
    }

    // ========================================================================
    // NEW FEATURE 2 — /raw/{name} → return raw PubChem JSON OR error details
    // ========================================================================
    @GetMapping("/raw/{name:.+}")
    public PubChemRaw raw(@PathVariable("name") String name) {
        String query = normalize(name);
        log.info("GET RAW PubChem response: '{}'", query);

        // Use existing PubChemClient.fetch(...)
        return pubChemClient.fetch(query);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = decodeOnce(s);
        s = decodeOnce(s);
        s = s.replace('+', ' ');
        return s.trim();
    }

    private String decodeOnce(String val) {
        try {
            return URLDecoder.decode(val, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException bad) {
            return val;
        }
    }
}
