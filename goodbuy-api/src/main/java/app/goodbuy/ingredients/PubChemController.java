package app.goodbuy.api.ingredients;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/ingredients/pubchem")
public class PubChemController {

    // Simple local RestTemplate; no extra Spring config needed for now.
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Proxy endpoint to see the raw PubChem JSON for a given ingredient name.
     *
     * Example:
     *   GET /api/ingredients/pubchem/raw/Sodium%20Bicarbonate
     *   GET /api/ingredients/pubchem/raw/Sodium%20Hypochlorite
     *
     * This does NOT touch the DB or IngredientDTO yet. It only lets us inspect
     * what PubChem actually returns so we can design our mapper later.
     */
    @GetMapping("/raw/{name}")
    public ResponseEntity<String> getPubChemRawByName(@PathVariable("name") String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"name must not be blank\"}");
        }

        String trimmed = name.trim();
        String encoded = UriUtils.encode(trimmed, StandardCharsets.UTF_8);

        // PubChem PUG REST: compound by name, JSON format
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/"
                + encoded
                + "/JSON";

        try {
            String body = restTemplate.getForObject(url, String.class);

            if (body == null || body.isBlank()) {
                return ResponseEntity.status(502)
                        .body("{\"error\":\"empty_response_from_pubchem\",\"name\":\"" + escape(trimmed) + "\"}");
            }

            // Just pass PubChem JSON straight through
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown error";
            String jsonError = "{\"error\":\"pubchem_request_failed\",\"name\":\""
                    + escape(trimmed)
                    + "\",\"message\":\""
                    + escape(msg)
                    + "\"}";
            return ResponseEntity.status(502).body(jsonError);
        }
    }

    /**
     * Very simple JSON-string-safe escaper to avoid breaking the error payload.
     */
    private String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
