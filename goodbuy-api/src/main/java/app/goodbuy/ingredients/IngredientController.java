package app.goodbuy.ingredients;

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

    private static final int BATCH_LIMIT = 200;

    private final IngredientReadService service;

    public IngredientController(IngredientReadService service) {
        this.service = service;
    }

    // GET /api/ingredients/{nameOrKey}
    @GetMapping("/{nameOrKey:.+}")
    public app.goodbuy.ingredients.model.IngredientDTO getOne(@PathVariable("nameOrKey") String nameOrKey) {
        String query = normalize(nameOrKey);
        if (query.isEmpty()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "invalid_ingredient_name");
        }

        return service.findByNameOrAlias(query)
                .orElseThrow(() ->
                        new ResponseStatusException(NOT_FOUND, "Ingredient not found: " + query));
    }

    // POST /api/ingredients/_batch   Body: ["sodium-bicarbonate","baking soda",...]
    @PostMapping(path = "/_batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<app.goodbuy.ingredients.model.IngredientDTO> batch(@RequestBody List<String> names) {
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

        return service.findManyByNamesOrAliases(cleaned);
    }

    // Helpers
    private String normalize(String raw) {
        if (raw == null) return "";
        try {
            // Accept percent-encoded inputs; do NOT lower-case to preserve display names.
            String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            return decoded.trim();
        } catch (IllegalArgumentException badEncoding) {
            return raw.trim();
        }
    }
}
