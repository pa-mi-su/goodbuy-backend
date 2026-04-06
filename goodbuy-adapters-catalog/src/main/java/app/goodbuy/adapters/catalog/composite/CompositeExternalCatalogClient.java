package app.goodbuy.adapters.catalog.composite;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompositeExternalCatalogClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CompositeExternalCatalogClient.class);

    private final List<NamedClient> clients;

    public CompositeExternalCatalogClient(List<NamedClient> clients) {
        this.clients = clients == null ? List.of() : List.copyOf(clients);
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        CatalogTransportException lastTransport = null;
        List<ProductDetailDto> hits = new ArrayList<>();

        for (NamedClient named : clients) {
            try {
                Optional<ProductDetailDto> result = named.client().findByGtin(gtin14);
                if (result.isPresent()) {
                    ProductDetailDto dto = result.get();
                    hits.add(dto);
                    log.info("Composite catalog hit provider={} gtin14={} name={} brand={}",
                            named.name(), gtin14, safe(dto.name()), safe(dto.brand()));
                    continue;
                }
                log.debug("Composite catalog miss provider={} gtin14={}", named.name(), gtin14);
            } catch (CatalogTransportException ex) {
                lastTransport = ex;
                log.warn("Composite catalog transport failure provider={} gtin14={} msg={}",
                        named.name(), gtin14, ex.getMessage());
            }
        }

        if (!hits.isEmpty()) {
            return Optional.of(selectBestHit(hits));
        }

        if (lastTransport != null) {
            throw lastTransport;
        }
        return Optional.empty();
    }

    public record NamedClient(String name, ExternalCatalogClient client) {}

    private ProductDetailDto selectBestHit(List<ProductDetailDto> hits) {
        ProductDetailDto best = hits.stream()
                .max((left, right) -> Integer.compare(score(left), score(right)))
                .orElseThrow();

        String name = firstNonBlank(best.name(), hits.stream().map(ProductDetailDto::name).toList());
        String brand = firstNonBlank(best.brand(), hits.stream().map(ProductDetailDto::brand).toList());
        String category = firstNonBlank(best.category(), hits.stream().map(ProductDetailDto::category).toList());
        String description = firstNonBlank(best.description(), hits.stream().map(ProductDetailDto::description).toList());
        String domain = firstNonBlank(best.domain(), hits.stream().map(ProductDetailDto::domain).toList());
        List<ProductDetailDto.ImageDto> images = !isEmpty(best.images())
                ? best.images()
                : firstNonEmptyImages(hits);
        List<ProductDetailDto.IngredientDto> ingredients = !isEmpty(best.ingredients())
                ? best.ingredients()
                : firstNonEmptyIngredients(hits);
        Map<String, String> titles = !isEmpty(best.titles())
                ? best.titles()
                : firstNonEmptyMap(hits.stream().map(ProductDetailDto::titles).toList());
        Map<String, String> manufacturer = !isEmpty(best.manufacturer())
                ? best.manufacturer()
                : firstNonEmptyMap(hits.stream().map(ProductDetailDto::manufacturer).toList());

        return new ProductDetailDto(
                best.gtin(),
                name,
                brand,
                category,
                description,
                images,
                ingredients,
                titles,
                manufacturer,
                best.source(),
                domain,
                best.safetyScore(),
                best.ratingLetter()
        );
    }

    private int score(ProductDetailDto dto) {
        int score = 0;
        score += ingredientCount(dto) * 100;
        score += imageCount(dto) * 10;
        if (!isBlank(dto.name())) score += 5;
        if (!isBlank(dto.brand())) score += 3;
        if (!isBlank(dto.category())) score += 2;
        if (!isBlank(dto.description())) score += 1;
        return score;
    }

    private static int ingredientCount(ProductDetailDto dto) {
        return dto == null || dto.ingredients() == null ? 0 : dto.ingredients().size();
    }

    private static int imageCount(ProductDetailDto dto) {
        return dto == null || dto.images() == null ? 0 : dto.images().size();
    }

    private static List<ProductDetailDto.ImageDto> firstNonEmptyImages(List<ProductDetailDto> hits) {
        for (ProductDetailDto hit : hits) {
            if (!isEmpty(hit.images())) {
                return hit.images();
            }
        }
        return List.of();
    }

    private static List<ProductDetailDto.IngredientDto> firstNonEmptyIngredients(List<ProductDetailDto> hits) {
        for (ProductDetailDto hit : hits) {
            if (!isEmpty(hit.ingredients())) {
                return hit.ingredients();
            }
        }
        return List.of();
    }

    private static Map<String, String> firstNonEmptyMap(List<Map<String, String>> maps) {
        for (Map<String, String> map : maps) {
            if (!isEmpty(map)) {
                return map;
            }
        }
        return Map.of();
    }

    private static String firstNonBlank(String preferred, List<String> values) {
        if (!isBlank(preferred)) {
            return preferred;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return preferred;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean isEmpty(Map<?, ?> values) {
        return values == null || values.isEmpty();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
