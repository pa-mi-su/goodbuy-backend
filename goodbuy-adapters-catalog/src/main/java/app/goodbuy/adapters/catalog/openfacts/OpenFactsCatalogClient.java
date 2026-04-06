package app.goodbuy.adapters.catalog.openfacts;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.dto.ProductDetailDto.ImageDto;
import app.goodbuy.core.products.dto.ProductDetailDto.IngredientDto;
import app.goodbuy.core.products.ingredients.IngredientTextParser;
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OpenFactsCatalogClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(OpenFactsCatalogClient.class);

    private static final String DEFAULT_FIELDS = String.join(",",
            "code",
            "product_name",
            "product_name_en",
            "brands",
            "categories",
            "generic_name",
            "generic_name_en",
            "image_url",
            "image_front_url",
            "image_ingredients_url",
            "ingredients",
            "ingredients_text",
            "ingredients_text_en"
    );

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String sourceName;
    private final String defaultDomain;
    private final String userAgent;
    private final int readTimeoutMs;

    public OpenFactsCatalogClient(
            String baseUrl,
            String sourceName,
            String defaultDomain,
            String userAgent,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.sourceName = sourceName;
        this.defaultDomain = defaultDomain;
        this.userAgent = userAgent == null || userAgent.isBlank() ? "GoodBuy-Backend" : userAgent;
        this.readTimeoutMs = readTimeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        CatalogTransportException lastTransport = null;

        for (String candidate : candidateCodes(gtin14)) {
            try {
                HttpRequest req = HttpRequest.newBuilder(buildUri(candidate))
                        .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                        .header("Accept", "application/json")
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int status = res.statusCode();

                if (status == 404) {
                    continue;
                }
                if (status < 200 || status >= 300) {
                    if (status == 429 || status >= 500) {
                        throw new CatalogTransportException(sourceName + "_http_" + status);
                    }
                    log.warn("{} non-2xx status={} code={} body={}", sourceName, status, candidate, truncate(res.body(), 256));
                    continue;
                }

                JsonNode root = om.readTree(res.body());
                if (root.path("status").asInt(1) == 0) {
                    continue;
                }

                JsonNode product = root.path("product");
                if (product.isMissingNode() || product.isNull()) {
                    continue;
                }

                return Optional.of(mapProduct(candidate, product));
            } catch (HttpTimeoutException | ConnectException ex) {
                lastTransport = new CatalogTransportException(sourceName + "_timeout", ex);
            } catch (IOException ex) {
                lastTransport = new CatalogTransportException(sourceName + "_io", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                lastTransport = new CatalogTransportException(sourceName + "_interrupted", ex);
            }
        }

        if (lastTransport != null) {
            throw lastTransport;
        }
        return Optional.empty();
    }

    private ProductDetailDto mapProduct(String code, JsonNode product) {
        String gtin = firstNonBlank(text(product, "code"), code);
        String name = firstNonBlank(
                text(product, "product_name"),
                text(product, "product_name_en"),
                text(product, "generic_name"),
                text(product, "generic_name_en")
        );
        String brand = firstBrand(text(product, "brands"));
        String category = firstCategory(text(product, "categories"));
        String description = firstNonBlank(text(product, "generic_name"), text(product, "generic_name_en"));

        List<ImageDto> images = new ArrayList<>();
        addImage(images, text(product, "image_front_url"));
        addImage(images, text(product, "image_url"));
        addImage(images, text(product, "image_ingredients_url"));

        List<IngredientDto> ingredients = readIngredients(product);

        Map<String, String> titles = name == null ? null : Map.of("en", name);
        Map<String, String> manufacturer = brand == null ? null : Map.of("en", brand);

        return new ProductDetailDto(
                emptyToNull(gtin),
                emptyToNull(name),
                emptyToNull(brand),
                emptyToNull(category),
                emptyToNull(description),
                images,
                ingredients,
                titles,
                manufacturer,
                sourceName,
                defaultDomain,
                null,
                null
        );
    }

    private List<IngredientDto> readIngredients(JsonNode product) {
        List<IngredientDto> ingredients = new ArrayList<>();
        JsonNode ingredientNodes = product.path("ingredients");

        if (ingredientNodes.isArray()) {
            for (JsonNode ingredient : ingredientNodes) {
                String text = firstNonBlank(
                        text(ingredient, "text"),
                        text(ingredient, "ingredient"),
                        normalizeIngredientId(text(ingredient, "id"))
                );
                if (text == null || text.isBlank()) {
                    continue;
                }
                ingredients.add(new IngredientDto(
                        normalizeIngredientId(text(ingredient, "id")),
                        text,
                        text,
                        Map.of(),
                        boolOrNull(ingredient, "vegan"),
                        boolOrNull(ingredient, "vegetarian")
                ));
            }
        }

        if (!ingredients.isEmpty()) {
            return dedupeIngredients(ingredients);
        }

        String ingredientText = firstNonBlank(text(product, "ingredients_text_en"), text(product, "ingredients_text"));
        if (ingredientText == null || ingredientText.isBlank()) {
            return List.of();
        }

        return dedupeIngredients(IngredientTextParser.parse(ingredientText).stream()
                .map(item -> new IngredientDto(
                        normalizeIngredientId(item),
                        item,
                        item,
                        Map.of(),
                        null,
                        null
                ))
                .toList());
    }

    private static List<IngredientDto> dedupeIngredients(List<IngredientDto> raw) {
        LinkedHashMap<String, IngredientDto> byKey = new LinkedHashMap<>();
        for (IngredientDto ingredient : raw) {
            if (ingredient == null) {
                continue;
            }
            String key = firstNonBlank(ingredient.id(), ingredient.canonical(), ingredient.original());
            if (key == null || key.isBlank()) {
                continue;
            }
            byKey.putIfAbsent(key.trim().toLowerCase(), ingredient);
        }
        return List.copyOf(byKey.values());
    }

    private URI buildUri(String code) {
        return URI.create(baseUrl
                + "/api/v2/product/"
                + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "?fields="
                + URLEncoder.encode(DEFAULT_FIELDS, StandardCharsets.UTF_8));
    }

    private static List<String> candidateCodes(String raw) {
        if (raw == null) {
            return List.of();
        }
        String digits = raw.trim().replaceAll("\\s+", "");
        if (digits.isEmpty() || !digits.matches("\\d+")) {
            return List.of();
        }

        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(digits);
        if (digits.length() == 14 && digits.startsWith("00")) {
            out.add(digits.substring(2));
        }
        if (digits.length() == 14 && digits.startsWith("0")) {
            out.add(digits.substring(1));
        }
        if (digits.length() == 13) {
            out.add("0" + digits);
        }
        return List.copyOf(out);
    }

    private static String normalizeIngredientId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int idx = trimmed.lastIndexOf(':');
        return idx >= 0 && idx < trimmed.length() - 1 ? trimmed.substring(idx + 1) : trimmed;
    }

    private static Boolean boolOrNull(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            String raw = value.asText("").trim().toLowerCase();
            return switch (raw) {
                case "yes", "true", "1" -> true;
                case "no", "false", "0" -> false;
                default -> null;
            };
        }
        return null;
    }

    private static void addImage(List<ImageDto> images, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        boolean exists = images.stream().anyMatch(img -> url.equalsIgnoreCase(img.url()));
        if (!exists) {
            images.add(new ImageDto(url, null, null));
        }
    }

    private static String firstBrand(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.split(",")[0].trim();
    }

    private static String firstCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.split(",")[0].trim();
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
