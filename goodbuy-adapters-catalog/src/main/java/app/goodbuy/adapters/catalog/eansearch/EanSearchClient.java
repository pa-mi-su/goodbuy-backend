package app.goodbuy.adapters.catalog.eansearch;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.dto.ProductDetailDto.ImageDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight client for the EAN-Search.org API.
 * Maps into ProductDetailDto (core DTO).
 *
 * NOTE: EAN-Search returns limited data; we populate what we can
 * and leave the rest as null/empty.
 */
public class EanSearchClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(EanSearchClient.class);

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public EanSearchClient(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();

        log.info("EAN-Search client initialized baseUrl={} connect={}ms read={}ms",
                this.baseUrl, this.connectTimeoutMs, this.readTimeoutMs);
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        // EAN-Search expects EAN-13, not GTIN-14 → drop leading zero if present
        String ean13 = (gtin14 != null && gtin14.length() == 14 && gtin14.startsWith("0"))
                ? gtin14.substring(1)
                : gtin14;

        try {
            String uri = baseUrl
                    + "?op=barcode-ean&format=json"
                    + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&ean=" + URLEncoder.encode(ean13, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            String body = res.body();

            log.debug("EAN-Search GET {} -> status={} bytes={}", uri, sc, body == null ? 0 : body.length());

            if (sc == 404) {
                log.debug("EAN-Search miss gtin14={} (404)", gtin14);
                return Optional.empty();
            }
            if (sc < 200 || sc >= 300) {
                throw new CatalogTransportException("eansearch_http_" + sc + ": " + truncate(body, 400));
            }

            JsonNode root = om.readTree(body);
            JsonNode resultArray = root.path("result");
            if (!resultArray.isArray() || resultArray.size() == 0) {
                log.debug("EAN-Search empty result for {}", gtin14);
                return Optional.empty();
            }

            JsonNode first = resultArray.get(0);

            // Basic fields
            String ean = text(first, "ean");
            if (ean == null || ean.isBlank()) {
                ean = ean13;
            }

            String name = text(first, "name", "title", "product");
            String brand = text(first, "brand", "manufacturer", "company");
            String category = text(first, "category");
            String description = text(first, "description", "details"); // best-effort; may be null

            // Single image, if available
            String imgUrl = text(first, "image");
            List<ImageDto> images = (imgUrl != null && !imgUrl.isBlank())
                    ? List.of(new ImageDto(imgUrl, null, null))
                    : Collections.emptyList();

            // EAN-Search does not expose structured ingredients → empty
            List<ProductDetailDto.IngredientDto> ingredients = Collections.emptyList();

            // titles: if we have a name, expose as {"en": name}
            Map<String, String> titles = (name != null && !name.isBlank())
                    ? Map.of("en", name)
                    : null;

            // manufacturer map from brand if present
            Map<String, String> manufacturer = (brand != null && !brand.isBlank())
                    ? Map.of("en", brand)
                    : null;

            ProductDetailDto dto = new ProductDetailDto(
                    ean,
                    emptyToNull(name),
                    emptyToNull(brand),
                    emptyToNull(category),
                    emptyToNull(description),
                    images,
                    ingredients,
                    titles,
                    manufacturer,
                    "EAN-SEARCH"
            );

            log.debug("EAN-Search hit ean={} name={} brand={}", ean, safe(name), safe(brand));
            return Optional.of(dto);

        } catch (CatalogTransportException e) {
            // Adapter-level failure: treat as "no result", don't break the app
            log.warn("EAN-Search transport error for gtin14={}: {}", gtin14, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            // Any unexpected issue → log + empty
            log.warn("EAN-Search unexpected error for gtin14={}", gtin14, e);
            return Optional.empty();
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }
}
