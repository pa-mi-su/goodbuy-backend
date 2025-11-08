package app.goodbuy.adapters.catalog;

import app.goodbuy.adapters.catalog.eandb.EanDbCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the external catalog client(s).
 * Currently only EAN-DB is supported.
 */
@Configuration
@ConditionalOnProperty(name = "goodbuy.catalog.enabled", havingValue = "true")
public class CatalogConfig {

    private static final Logger log = LoggerFactory.getLogger(CatalogConfig.class);

    /**
     * Single external catalog client: EAN-DB
     * Created only when:
     *   - goodbuy.catalog.enabled=true  (class-level)
     *   - goodbuy.catalog.provider=eandb (method-level)
     *
     * Expect base-url like: https://ean-db.com/api/v2/product
     * (The client will append "/{EAN13}" itself.)
     */
    @Bean
    @ConditionalOnProperty(name = "goodbuy.catalog.provider", havingValue = "eandb")
    public ExternalCatalogClient eandbClient(
            @Value("${goodbuy.catalog.eandb.base-url}") String baseUrl,
            @Value("${goodbuy.catalog.eandb.jwt}") String jwt,
            @Value("${goodbuy.catalog.eandb.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${goodbuy.catalog.eandb.read-timeout-ms:4000}") int readTimeoutMs
    ) {
        requireNonBlank(baseUrl, "goodbuy.catalog.eandb.base-url is required");
        requireNonBlank(jwt,      "goodbuy.catalog.eandb.jwt is required");

        final int ct = clamp(connectTimeoutMs);
        final int rt = clamp(readTimeoutMs);

        log.info("Catalog: provider=eandb baseUrl={} ct={}ms rt={}ms", baseUrl, ct, rt);
        return new EanDbCatalogClient(baseUrl, jwt, ct, rt);
    }

    // ---- helpers ----
    private static void requireNonBlank(String s, String msg) {
        if (s == null || s.isBlank()) throw new IllegalStateException(msg);
    }
    private static int clamp(int ms) {
        // keep within sane bounds (100ms .. 30s)
        return Math.max(100, Math.min(ms, 30_000));
    }
}
