package app.goodbuy.adapters.catalog.upcitemdb;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.dto.ProductDetailDto.ImageDto;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UpcItemDbCatalogClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(UpcItemDbCatalogClient.class);

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final int readTimeoutMs;

    public UpcItemDbCatalogClient(
            String baseUrl,
            String apiKey,
            String userAgent,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
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
                HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(candidate))
                        .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                        .header("Accept", "application/json")
                        .header("User-Agent", userAgent)
                        .GET();

                if (!apiKey.isBlank()) {
                    builder.header("user_key", apiKey);
                }

                HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = res.statusCode();

                if (status == 404) {
                    continue;
                }
                if (status < 200 || status >= 300) {
                    if (status == 429 || status >= 500) {
                        throw new CatalogTransportException("upcitemdb_http_" + status);
                    }
                    log.warn("UPCitemdb non-2xx status={} code={} body={}", status, candidate, truncate(res.body(), 256));
                    continue;
                }

                JsonNode root = om.readTree(res.body());
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    continue;
                }

                JsonNode item = items.get(0);
                String title = firstNonBlank(text(item, "title"), text(item, "name"));
                String brand = firstNonBlank(text(item, "brand"), text(item, "brand_name"));
                String description = firstNonBlank(text(item, "description"));
                String category = firstNonBlank(text(item, "category"));
                String barcode = firstNonBlank(text(item, "ean"), text(item, "upc"), candidate);

                List<ImageDto> images = new ArrayList<>();
                JsonNode imageArray = item.path("images");
                if (imageArray.isArray()) {
                    for (JsonNode image : imageArray) {
                        String url = image.asText(null);
                        if (url != null && !url.isBlank()) {
                            images.add(new ImageDto(url.trim(), null, null));
                        }
                    }
                }

                return Optional.of(new ProductDetailDto(
                        barcode,
                        title,
                        brand,
                        category,
                        description,
                        images,
                        List.of(),
                        title == null ? null : Map.of("en", title),
                        brand == null ? null : Map.of("en", brand),
                        "UPCITEMDB",
                        "unknown",
                        null,
                        null
                ));
            } catch (HttpTimeoutException | ConnectException ex) {
                lastTransport = new CatalogTransportException("upcitemdb_timeout", ex);
            } catch (IOException ex) {
                lastTransport = new CatalogTransportException("upcitemdb_io", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                lastTransport = new CatalogTransportException("upcitemdb_interrupted", ex);
            }
        }

        if (lastTransport != null) {
            throw lastTransport;
        }
        return Optional.empty();
    }

    private URI buildUri(String code) {
        return URI.create(baseUrl + "?upc=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
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
        if (digits.length() == 14 && digits.startsWith("00")) {
            out.add(digits.substring(2));
        }
        if (digits.length() == 14 && digits.startsWith("0")) {
            out.add(digits.substring(1));
        }
        out.add(digits);
        return List.copyOf(out);
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
