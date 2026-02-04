package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Validated
@RestController
@RequestMapping(
        path = {"/api/ingredients", "/api/v1/ingredients"}, // ✅ support BOTH routes
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class IngredientController {

    private static final int BATCH_LIMIT = 200;

    private final IngredientReadService service;

    public IngredientController(IngredientReadService service) {
        this.service = service;
    }

    // A) GET /api/ingredients?q=Raw Name With Spaces
    //    GET /api/v1/ingredients?q=...
    @GetMapping(params = "q")
    public IngredientDTO getOneByQuery(@RequestParam("q") String q) {
        String query = normalize(q);
        if (query.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_ingredient_name");
        }

        return service.searchRanked(query)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Ingredient not found: " + query
                        )
                );
    }

    // B) GET /api/ingredients/{nameOrKey}
    //    GET /api/v1/ingredients/{nameOrKey}
    //
    // Keep this for backwards compatibility, but the client SHOULD prefer ?q=
    @GetMapping("/{nameOrKey:.+}")
    public IngredientDTO getOne(@PathVariable("nameOrKey") String nameOrKey) {
        String query = normalize(nameOrKey);
        if (query.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_ingredient_name");
        }

        return service.searchRanked(query)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Ingredient not found: " + query
                        )
                );
    }

    // POST /api/ingredients/_batch
    // POST /api/v1/ingredients/_batch
    @PostMapping(path = "/_batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<IngredientDTO> batch(@RequestBody List<String> names) {
        if (names == null) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_request");
        }

        var cleaned = names.stream()
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)) // dedupe, preserve order
                .stream()
                .limit(BATCH_LIMIT)
                .toList();

        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_request");
        }

        return service.searchManyRanked(cleaned);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // Query-form sometimes gives '+' for space
        s = s.replace('+', ' ');

        // decode up to twice to handle %2520 → %20 → " "
        s = decodeOnce(s);
        s = decodeOnce(s);

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
