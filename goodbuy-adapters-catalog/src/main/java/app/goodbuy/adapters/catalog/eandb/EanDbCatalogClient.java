package app.goodbuy.adapters.catalog.eandb;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.adapters.catalog.ExternalCatalogClient;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.dto.ProductDetailDto.ImageDto;
import app.goodbuy.core.products.dto.ProductDetailDto.IngredientDto;
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
import java.time.Instant;
import java.util.*;

/**
 * EAN-DB client.
 *
 * Implements ExternalCatalogClient with the rich ProductDetailDto from core.
 */
public class EanDbCatalogClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(EanDbCatalogClient.class);

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    private final String baseUrl;      // e.g. https://ean-db.com/api/v2/product
    private final String bearerJwt;    // JWT
    private final int readTimeoutMs;
    private final String codeParamKey; // used only when baseUrl isn't /product
    private final String userAgent;

    public EanDbCatalogClient(String baseUrl, String bearerJwt, int connectTimeoutMs, int readTimeoutMs) {
        this(baseUrl, bearerJwt, connectTimeoutMs, readTimeoutMs,
                "ean", "GoodBuy-Backend/0.1 (+https://goodbuy.app)");
    }

    public EanDbCatalogClient(String baseUrl,
                              String bearerJwt,
                              int connectTimeoutMs,
                              int readTimeoutMs,
                              String codeParamKey,
                              String userAgent) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.bearerJwt = bearerJwt;
        this.readTimeoutMs = readTimeoutMs;
        this.codeParamKey = (codeParamKey == null || codeParamKey.isBlank()) ? "ean" : codeParamKey;
        this.userAgent = (userAgent == null || userAgent.isBlank()) ? "GoodBuy-Backend" : userAgent;

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ExternalCatalogClient
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) throws CatalogTransportException {
        try {
            JsonNode product = fetchProductNode(gtin14);
            if (product == null) {
                return Optional.empty();
            }

            // GTIN / name / brand / category / description
            String gtin = firstNonBlank(text(product, "barcode"), toEan13(gtin14));
            String name = firstNonBlank(
                    text(product, "titles", "en"),
                    firstValue(product.path("titles"))
            );
            String brand = firstNonBlank(
                    text(product, "manufacturer", "titles", "en"),
                    firstValue(product.path("manufacturer").path("titles")),
                    text(product, "manufacturer", "id")
            );
            String category = extractFirstCategoryTitle(product);
            String description = firstNonBlank(
                    text(product, "descriptions", "en"),
                    firstValue(product.path("descriptions"))
            );

            // Images
            List<ImageDto> images = new ArrayList<>();
            JsonNode imgs = product.path("images");
            if (imgs.isArray()) {
                imgs.forEach(img -> {
                    String url = text(img, "url");
                    Integer w = intOrNull(img, "width");
                    Integer h = intOrNull(img, "height");
                    if (url != null && !url.isBlank()) {
                        images.add(new ImageDto(url, w, h));
                    }
                });
            }

            // Ingredients (full)
            List<IngredientDto> ingredients = new ArrayList<>();
            JsonNode groups = product.path("metadata").path("generic").path("ingredients");
            if (groups.isArray()) {
                groups.forEach(g -> {
                    JsonNode arr = g.path("ingredientsGroup");
                    if (arr.isArray()) {
                        arr.forEach(node -> {
                            String id = text(node, "id");
                            String original = text(node, "originalNames", "en");
                            String canonical = text(node, "canonicalNames", "en");

                            Map<String, String> externalIds = new LinkedHashMap<>();
                            JsonNode ext = node.path("externalIds");
                            if (ext.isObject()) {
                                ext.fieldNames().forEachRemaining(k -> {
                                    String v = ext.path(k).asText(null);
                                    if (v != null && !v.isBlank()) {
                                        externalIds.put(k, v);
                                    }
                                });
                            }

                            Boolean isVegan = boolOrNull(node, "isVegan");
                            Boolean isVegetarian = boolOrNull(node, "isVegetarian");

                            ingredients.add(new IngredientDto(
                                    emptyToNull(id),
                                    emptyToNull(original),
                                    emptyToNull(canonical),
                                    externalIds.isEmpty() ? null : externalIds,
                                    isVegan,
                                    isVegetarian
                            ));
                        });
                    }
                });
            }

            // titles{lang:value}
            Map<String, String> titles = objectToLangMap(product.path("titles"));
            if (titles.isEmpty()) {
                titles = null;
            }

            // manufacturer{id,en}
            Map<String, String> manufacturer = new LinkedHashMap<>();
            String mId = text(product, "manufacturer", "id");
            String mEn = text(product, "manufacturer", "titles", "en");
            if (mId != null && !mId.isBlank()) {
                manufacturer.put("id", mId);
            }
            if (mEn != null && !mEn.isBlank()) {
                manufacturer.put("en", mEn);
            }
            if (manufacturer.isEmpty()) {
                manufacturer = null;
            }

            ProductDetailDto out = new ProductDetailDto(
                    emptyToNull(gtin),
                    emptyToNull(name),
                    emptyToNull(brand),
                    emptyToNull(category),
                    emptyToNull(description),
                    images,
                    ingredients,
                    titles,
                    manufacturer,
                    "EAN-DB"
            );

            log.debug(
                    "EAN-DB(detail) mapped {} → name={} brand={} imgs={} ingredients={}",
                    gtin14,
                    safe(out.name()),
                    safe(out.brand()),
                    out.images().size(),
                    out.ingredients().size()
            );

            return Optional.of(out);

        } catch (CatalogTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new CatalogTransportException("catalog_transport_error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HTTP + parsing
    // ─────────────────────────────────────────────────────────────────────────────

    private JsonNode fetchProductNode(String gtin14) throws Exception {
        URI uri = buildUri(gtin14);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US")
                .header("Authorization", "Bearer " + bearerJwt)
                .header("User-Agent", userAgent)
                .GET()
                .build();

        Instant t0 = Instant.now();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        long ms = Duration.between(t0, Instant.now()).toMillis();

        int sc = res.statusCode();
        String body = res.body();

        log.debug("EanDB GET {} -> status={} bytes={}", uri, sc, body == null ? 0 : body.length());

        if (sc == 404) {
            log.debug("EAN-DB miss status=404 durMs={} body={}", ms, truncate(body, 512));
            return null;
        }
        if (sc == 429 || sc == 503) {
            String ra = res.headers().firstValue("Retry-After").orElse("-");
            throw new CatalogTransportException("catalog_throttled_" + sc + " retryAfter=" + ra);
        }
        if (sc < 200 || sc >= 300) {
            log.debug("EanDB non-2xx body: {}", truncate(body, 512));
            throw new CatalogTransportException("catalog_http_" + sc + ": " + truncate(body, 512));
        }

        JsonNode root = om.readTree(body);
        JsonNode product = root.path("product");
        if (product.isMissingNode() || product.isNull()) {
            return null;
        }
        return product;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // URI helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private URI buildUri(String gtin14) {
        String ean13 = toEan13(gtin14);
        String enc = URLEncoder.encode(ean13, StandardCharsets.UTF_8);
        String base = this.baseUrl;

        if (base.matches(".*/product$")) {
            return URI.create(base + "/" + enc);
        }

        String sep = base.contains("?") ? "&" : "?";
        return URI.create(base + sep + codeParamKey + "=" + enc);
    }

    /** EAN-DB expects EAN-13; if we get GTIN-14 starting with 0, drop it. */
    private static String toEan13(String gtin14) {
        if (gtin14 != null && gtin14.length() == 14 && gtin14.charAt(0) == '0') {
            return gtin14.substring(1);
        }
        return gtin14;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private static String extractFirstCategoryTitle(JsonNode prod) {
        JsonNode cats = prod.path("categories");
        if (cats.isArray() && cats.size() > 0) {
            JsonNode titles = cats.get(0).path("titles");
            if (titles.isObject()) {
                String en = titles.path("en").asText(null);
                if (en != null && !en.isBlank()) {
                    return en;
                }
                var it = titles.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    if (e.getValue().isTextual() && !e.getValue().asText().isBlank()) {
                        return e.getValue().asText();
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, String> objectToLangMap(JsonNode obj) {
        if (obj == null || !obj.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        obj.fieldNames().forEachRemaining(k -> {
            String v = obj.path(k).asText(null);
            if (v != null && !v.isBlank()) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isIntegralNumber() ? v.asInt() : null;
    }

    private static Boolean boolOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asBoolean();
    }

    private static String text(JsonNode node, String... path) {
        if (node == null) return null;
        JsonNode cur = node;
        for (String p : path) {
            cur = cur.path(p);
        }
        String s = cur.isMissingNode() || cur.isNull() ? null : cur.asText(null);
        return (s != null && !s.isBlank()) ? s : null;
    }

    /** For objects like { "en": "...", "fr": "..." } pick the first non-blank value. */
    private static String firstValue(JsonNode titlesObj) {
        if (titlesObj == null || !titlesObj.isObject()) return null;
        var it = titlesObj.fieldNames();
        while (it.hasNext()) {
            String k = it.next();
            String v = titlesObj.path(k).asText(null);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : s.substring(0, max) + "…";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }
}
