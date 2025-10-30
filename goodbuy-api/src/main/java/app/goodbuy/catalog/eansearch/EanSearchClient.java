package app.goodbuy.catalog.eansearch;

import app.goodbuy.catalog.ExternalCatalogClient;
import app.goodbuy.products.ProductDto;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/**
 * Thin client for an external EAN/UPC lookup API.
 * Returns null when not found. Throws on transport errors.
 * NOTE: This class is defined but NOT YET WIRED into ProductService (no behavior change).
 */
public class EanSearchClient implements ExternalCatalogClient {

    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;

    public EanSearchClient(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Override
    public ProductDto lookupByGtin14(String gtin14) throws Exception {
        var uri = URI.create(String.format(
                "%s?op=barcode-lookup&ean=%s&format=json&key=%s",
                baseUrl, gtin14, apiKey));

        try {
            ResponseEntity<Map> resp = http.exchange(uri, HttpMethod.GET, null, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return mapToDto(gtin14, resp.getBody());
            }
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ProductDto mapToDto(String gtin14, Map<String, Object> body) {
        Map<String, Object> p = null;
        Object productObj = body.get("product");
        Object productsObj = body.get("products");

        if (productObj instanceof Map<?, ?> m) {
            p = (Map<String, Object>) m;
        } else if (productsObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            p = (Map<String, Object>) m;
        }
        if (p == null) return null;

        String name = firstNonBlank(p, "name", "title", "product", "description");
        String brand = firstNonBlank(p, "brand", "manufacturer");
        String cat = firstNonBlank(p, "category", "category_name");

        List<String> images = toStringList(p.get("images"));
        if (images == null || images.isEmpty()) {
            images = toStringList(p.get("image_urls"));
        }
        if (images == null || images.isEmpty()) {
            String single = asString(p.get("image"));
            if (single != null && !single.isBlank()) images = List.of(single.trim());
        }
        if (images == null) images = List.of();

        return new ProductDto(
                gtin14,
                emptyToNull(name),
                emptyToNull(brand),
                emptyToNull(cat) == null ? "cleaner" : cat,
                images,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static String firstNonBlank(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            String s = asString(m.get(k));
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private static String asString(Object o) {
        return (o instanceof String s) ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof String s && !s.isBlank()) out.add(s.trim());
            }
            return out;
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
