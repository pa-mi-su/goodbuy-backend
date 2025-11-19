package app.goodbuy.adapters.core.products.cache;

import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductCachePort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.products.util.BarcodeNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Database-backed implementation of ProductCachePort.
 *
 * Stores ProductDetailDto as JSON in product_cache table.
 * - READ: best-effort, invalid/undecodable entries are ignored.
 * - WRITE: best-effort, failures never break the main flow.
 * - Keys are canonical GTIN-14 using BarcodeNormalizer.
 *
 * Additionally, on successful cache save, it forwards the DTO to ProductSnapshotPort
 * so we can persist a normalized snapshot into:
 *   - products
 *   - product_ingredients
 */
@Component
public class ProductCacheAdapter implements ProductCachePort {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheAdapter.class);

    private final ProductCacheRepository repo;
    private final ObjectMapper objectMapper;
    private final BarcodeNormalizer barcodeNormalizer;
    private final ProductSnapshotPort snapshotPort;   // <-- new

    public ProductCacheAdapter(
            ProductCacheRepository repo,
            ObjectMapper objectMapper,
            BarcodeNormalizer barcodeNormalizer,
            ProductSnapshotPort snapshotPort
    ) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.barcodeNormalizer = barcodeNormalizer;
        this.snapshotPort = snapshotPort;

        log.info("product-cache: ProductSnapshotPort wired: {}",
                snapshotPort != null ? snapshotPort.getClass().getName() : "null");
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String rawGtin) {
        String key = safeNormalize(rawGtin);
        if (key == null) {
            log.debug("product-cache: skip lookup, invalid/raw gtin={}", rawGtin);
            return Optional.empty();
        }

        log.debug("product-cache: lookup GTIN={}", key);

        return repo.findById(key)
                .flatMap(entity -> {
                    if (entity == null) {
                        log.debug("product-cache: no row for GTIN={}", key);
                        return Optional.empty();
                    }

                    String json = entity.getJsonPayload();
                    if (json == null || json.isBlank()) {
                        log.debug("product-cache: empty payload for GTIN={}", key);
                        return Optional.empty();
                    }

                    try {
                        ProductDetailDto dto = objectMapper.readValue(json, ProductDetailDto.class);
                        log.debug("product-cache: hit GTIN={}", key);
                        return Optional.of(dto);
                    } catch (Exception e) {
                        log.warn("product-cache: failed to deserialize GTIN={} (ignoring cache entry)", key, e);
                        return Optional.empty();
                    }
                });
    }

    /**
     * Save DTO into cache and return it (per ProductCachePort contract).
     * Any persistence error is logged and ignored.
     *
     * Also forwards the DTO to ProductSnapshotPort so we can:
     *   - upsert into products
     *   - rebuild product_ingredients links
     */
    @Override
    public ProductDetailDto save(ProductDetailDto dto) {
        if (dto == null) {
            return null;
        }

        String key = safeNormalize(dto.gtin());
        if (key == null) {
            log.debug("product-cache: skip save, invalid/missing gtin in dto");
            return dto;
        }

        // 1) Store JSON into product_cache (best-effort)
        try {
            String json = objectMapper.writeValueAsString(dto);

            ProductCacheEntity entity = new ProductCacheEntity();
            entity.setGtin(key);
            entity.setJsonPayload(json);
            entity.setSource(dto.source());

            repo.save(entity);
            log.debug("product-cache: stored GTIN={} (source={})", key, dto.source());
        } catch (Exception e) {
            // DO NOT break requests on cache failure
            log.warn("product-cache: failed to store GTIN={} (non-fatal): {}", key, e.getMessage());
        }

        // 2) Forward to snapshot adapter (also best-effort)
        try {
            if (snapshotPort != null) {
                snapshotPort.saveSnapshot(dto);
            } else {
                log.debug("product-cache: snapshotPort is null, skipping snapshot for GTIN={}", key);
            }
        } catch (Exception e) {
            // Again, never break the main request due to snapshot issues
            log.warn("product-cache: failed to snapshot GTIN={} (non-fatal): {}", key, e.getMessage());
        }

        return dto;
    }

    /**
     * Use BarcodeNormalizer but never throw.
     * Returns canonical GTIN-14 or null if invalid.
     */
    private String safeNormalize(String rawGtin) {
        if (rawGtin == null || rawGtin.isBlank()) {
            return null;
        }
        try {
            return barcodeNormalizer.normalizeToGtin14OrThrow(rawGtin);
        } catch (ResponseStatusException ex) {
            log.debug("product-cache: invalid gtin={} ({}), skipping cache", rawGtin, ex.getReason());
            return null;
        }
    }
}
