package app.goodbuy.adapters.catalog.eandb;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.dto.ProductDetailDto.ImageDto;
import app.goodbuy.core.products.dto.ProductDetailDto.IngredientDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * EAN-DB client.
 *
 * Implements the core ExternalCatalogClient port with rich ProductDetailDto.
 *
 * IMPORTANT:
 *  - 404 => true catalog miss => Optional.empty()
 *  - 429/5xx/timeouts => catalog unavailable => CatalogTransportException (NOT Optional.empty)
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

    // Retry policy: only for transient errors
    private final int maxAttempts;
    private final long baseBackoffMs;

    public EanDbCatalogClient(String baseUrl,
                              String bearerJwt,
                              int connectTimeoutMs,
                              int readTimeoutMs) {
        this(baseUrl, bearerJwt, connectTimeoutMs, readTimeoutMs,
                "ean", "GoodBuy-Backend/0.1 (+https://goodbuy.app)",
                2, 350);
    }

    public EanDbCatalogClient(String baseUrl,
                              String bearerJwt,
                              int connectTimeoutMs,
                              int readTimeoutMs,
                              String codeParamKey,
                              String userAgent) {
        this(baseUrl, bearerJwt, connectTimeoutMs, readTimeoutMs,
                codeParamKey, userAgent,
                2, 350);
    }

    public EanDbCatalogClient(String baseUrl,
                              String bearerJwt,
                              int connectTimeoutMs,
                              int readTimeoutMs,
                              String codeParamKey,
                              String userAgent,
                              int maxAttempts,
                              long baseBackoffMs) {

        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        this.bearerJwt = bearerJwt;
        this.readTimeoutMs = readTimeoutMs;
        this.codeParamKey = (codeParamKey == null || codeParamKey.isBlank()) ? "ean" : codeParamKey;
        this.userAgent = (userAgent == null || userAgent.isBlank()) ? "GoodBuy-Backend" : userAgent;

        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = Math.max(50, baseBackoffMs);

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        JsonNode product = fetchProductNodeWithRetry(gtin14);
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
                    arr.forEach(node -> ingredients.addAll(mapIngredientNode(node)));
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
        if (mId != null && !mId.isBlank()) manufacturer.put("id", mId);
        if (mEn != null && !mEn.isBlank()) manufacturer.put("en", mEn);
        if (manufacturer.isEmpty()) manufacturer = null;

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
                "EAN-DB",
                "unknown",   // domain: classified later
                null,
                null
        );

        log.debug(
                "EAN-DB(detail) mapped {} → name={} brand={} imgs={} ingredients={}",
                gtin14,
                safe(out.name()),
                safe(out.brand()),
                out.images() == null ? 0 : out.images().size(),
                out.ingredients() == null ? 0 : out.ingredients().size()
        );

        return Optional.of(out);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HTTP + parsing (with candidate codes + retry)
    // ─────────────────────────────────────────────────────────────────────────────

    private JsonNode fetchProductNodeWithRetry(String rawCode) {
        // Fail fast: missing JWT is NOT a miss.
        if (bearerJwt == null || bearerJwt.isBlank()) {
            sneakyThrow(new CatalogTransportException("catalog_config_error: EAN-DB bearer token is empty"));
            return null; // unreachable
        }

        List<String> candidates = candidateCodes(rawCode);
        if (candidates.isEmpty()) {
            return null;
        }

        CatalogTransportException lastTransport = null;

        for (String code : candidates) {
            URI uri = buildUriFromCode(code);

            int attempt = 0;
            while (true) {
                attempt++;

                Instant t0 = Instant.now();
                try {
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                            .header("Accept", "application/json")
                            .header("Accept-Language", "en-US")
                            .header("Authorization", "Bearer " + bearerJwt)
                            .header("User-Agent", userAgent)
                            .GET()
                            .build();

                    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                    long ms = Duration.between(t0, Instant.now()).toMillis();

                    int sc = res.statusCode();
                    String body = res.body();
                    HttpHeaders headers = res.headers();

                    log.debug("EanDB GET {} -> status={} bytes={} code={} attempt={}/{} durMs={}",
                            uri, sc, body == null ? 0 : body.length(), code, attempt, maxAttempts, ms);

                    if (sc == 404) {
                        // True miss for this candidate; try next candidate
                        log.debug("EAN-DB miss status=404 code={} durMs={} attempt={} body={}",
                                code, ms, attempt, truncate(body, 256));
                        break;
                    }

                    if (isTransientStatus(sc)) {
                        String retryAfter = headers.firstValue("Retry-After").orElse(null);
                        String reqId = firstHeader(headers, "X-Request-Id", "x-request-id", "CF-RAY", "cf-ray");
                        log.warn("EAN-DB transient status={} code={} durMs={} attempt={}/{} retryAfter={} reqId={} body={}",
                                sc, code, ms, attempt, maxAttempts, safe(retryAfter), safe(reqId), truncate(body, 256));

                        if (attempt >= maxAttempts) {
                            lastTransport = new CatalogTransportException(
                                    "catalog_http_" + sc + " after " + attempt + " attempts (code=" + code + ")"
                            );
                            break;
                        }

                        sleepBackoff(attempt, retryAfter);
                        continue;
                    }

                    if (sc < 200 || sc >= 300) {
                        String reqId = firstHeader(headers, "X-Request-Id", "x-request-id", "CF-RAY", "cf-ray");
                        log.warn("EAN-DB non-2xx status={} code={} attempt={} reqId={} body={}",
                                sc, code, attempt, safe(reqId), truncate(body, 512));
                        lastTransport = new CatalogTransportException("catalog_http_" + sc + ": " + truncate(body, 512));
                        break;
                    }

                    // Parse JSON
                    JsonNode root = om.readTree(body);
                    JsonNode product = root.path("product");
                    if (product.isMissingNode() || product.isNull()) {
                        lastTransport = new CatalogTransportException("catalog_parse_error: missing 'product' node");
                        break;
                    }
                    return product;

                } catch (HttpTimeoutException | ConnectException e) {
                    long ms = Duration.between(t0, Instant.now()).toMillis();
                    log.warn("EAN-DB timeout/connect code={} attempt={}/{} durMs={} msg={}",
                            code, attempt, maxAttempts, ms, e.getMessage());

                    if (attempt >= maxAttempts) {
                        lastTransport = new CatalogTransportException(
                                "catalog_timeout after " + attempt + " attempts (code=" + code + ")", e
                        );
                        break;
                    }
                    sleepBackoff(attempt, null);

                } catch (IOException | InterruptedException e) {
                    long ms = Duration.between(t0, Instant.now()).toMillis();
                    log.warn("EAN-DB IO/interrupted code={} attempt={}/{} durMs={} type={} msg={}",
                            code, attempt, maxAttempts, ms, e.getClass().getSimpleName(), e.getMessage());

                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }

                    if (attempt >= maxAttempts) {
                        lastTransport = new CatalogTransportException(
                                "catalog_io_error after " + attempt + " attempts (code=" + code + ")", e
                        );
                        break;
                    }
                    sleepBackoff(attempt, null);

                } catch (Exception e) {
                    long ms = Duration.between(t0, Instant.now()).toMillis();
                    log.warn("EAN-DB unexpected code={} attempt={} durMs={} type={} msg={}",
                            code, attempt, ms, e.getClass().getSimpleName(), e.getMessage(), e);

                    lastTransport = new CatalogTransportException("catalog_unexpected_error (code=" + code + ")", e);
                    break;
                }
            }
        }

        if (lastTransport != null) {
            sneakyThrow(lastTransport);
        }
        return null;
    }

    /**
     * Generate code shapes to try, in order.
     *
     * Order:
     *  - If GTIN-14 starts with "00" => UPC-12 first (common for iOS scans)
     *  - EAN-13 (drop one leading 0)
     *  - original
     */
    private static List<String> candidateCodes(String raw) {
        if (raw == null) return List.of();

        String digits = raw.trim().replaceAll("\\s+", "");
        if (digits.isEmpty() || !digits.matches("\\d+")) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();

        if (digits.length() == 14) {
            if (digits.startsWith("00")) out.add(digits.substring(2)); // UPC-12
            if (digits.charAt(0) == '0') out.add(digits.substring(1)); // EAN-13
            out.add(digits); // GTIN-14
        } else if (digits.length() == 13) {
            out.add(digits);
            if (digits.charAt(0) == '0') out.add(digits.substring(1)); // UPC-12
        } else if (digits.length() == 12) {
            out.add(digits);       // UPC-12
            out.add("0" + digits); // EAN-13
        } else {
            out.add(digits);
        }

        return new ArrayList<>(out);
    }

    private static boolean isTransientStatus(int sc) {
        return sc == 429 || sc == 502 || sc == 503 || sc == 504;
    }

    private void sleepBackoff(int attempt, String retryAfterHeader) {
        long sleepMs = baseBackoffMs * (long) attempt;

        if (retryAfterHeader != null) {
            try {
                long raSec = Long.parseLong(retryAfterHeader.trim());
                sleepMs = Math.max(sleepMs, raSec * 1000L);
            } catch (Exception ignored) {
                // ignore
            }
        }

        sleepMs = Math.min(sleepMs, 2_000L);

        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstHeader(HttpHeaders headers, String... keys) {
        for (String k : keys) {
            Optional<String> v = headers.firstValue(k);
            if (v.isPresent() && !v.get().isBlank()) return v.get();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // URI helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private URI buildUriFromCode(String code) {
        String enc = URLEncoder.encode(code, StandardCharsets.UTF_8);
        String base = this.baseUrl;

        if (base.matches(".*/product$")) {
            return URI.create(base + "/" + enc);
        }

        String sep = base.contains("?") ? "&" : "?";
        return URI.create(base + sep + codeParamKey + "=" + enc);
    }

    /** Mapping fallback only. */
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
                if (en != null && !en.isBlank()) return en;
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
        if (obj == null || !obj.isObject()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        obj.fieldNames().forEachRemaining(k -> {
            String v = obj.path(k).asText(null);
            if (v != null && !v.isBlank()) out.put(k, v);
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
        for (String p : path) cur = cur.path(p);
        String s = cur.isMissingNode() || cur.isNull() ? null : cur.asText(null);
        return (s != null && !s.isBlank()) ? s : null;
    }

    private static String firstValue(JsonNode titlesObj) {
        if (titlesObj == null || !titlesObj.isObject()) return null;
        var it = titlesObj.fieldNames();
        while (it.hasNext()) {
            String k = it.next();
            String v = titlesObj.path(k).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : s.substring(0, max) + "…";
    }

    static List<IngredientDto> mapIngredientNode(JsonNode node) {
        String id = text(node, "id");
        String original = emptyToNull(text(node, "originalNames", "en"));
        String canonical = emptyToNull(text(node, "canonicalNames", "en"));

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

        List<String> expandedLabels = expandIngredientLabels(original, canonical);
        if (expandedLabels.isEmpty()) {
            return List.of();
        }

        if (expandedLabels.size() == 1) {
            return List.of(new IngredientDto(
                    emptyToNull(id),
                    expandedLabels.get(0),
                    emptyToNull(firstNonBlank(canonical, expandedLabels.get(0))),
                    externalIds.isEmpty() ? null : externalIds,
                    isVegan,
                    isVegetarian
            ));
        }

        List<IngredientDto> out = new ArrayList<>();
        for (String label : expandedLabels) {
            out.add(new IngredientDto(
                    null,
                    label,
                    label,
                    null,
                    isVegan,
                    isVegetarian
            ));
        }
        return out;
    }

    static List<String> expandIngredientLabels(String original, String canonical) {
        String base = firstNonBlank(original, canonical);
        if (base == null || base.isBlank()) {
            return List.of();
        }

        String trimmed = base.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (isLessThanHeaderOnly(lower)) {
            return List.of();
        }

        String stripped = stripLessThanPrefix(trimmed);
        if (!stripped.equals(trimmed)) {
            return splitIngredientList(stripped);
        }

        return List.of(trimmed);
    }

    private static boolean isLessThanHeaderOnly(String lower) {
        return lower.matches("^less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*$")
                || lower.matches("^contains\\s+less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*$")
                || lower.matches("^<\\s*\\d+\\s*%\\s+of\\s*:?\\s*$");
    }

    private static String stripLessThanPrefix(String value) {
        String stripped = value
                .replaceFirst("(?i)^contains\\s+less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "")
                .replaceFirst("(?i)^less\\s+than\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "")
                .replaceFirst("(?i)^<\\s*\\d+\\s*%\\s+of\\s*:?\\s*", "");
        return stripped.trim();
    }

    private static List<String> splitIngredientList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        return Arrays.stream(raw.split("\\s*,\\s*"))
                .map(String::trim)
                .map(token -> token.replaceAll("\\s*;$", "").trim())
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }

    /**
     * Sneaky-throw helper so we can throw CatalogTransportException even though the
     * ExternalCatalogClient interface method doesn't declare it.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
