package app.goodbuy.catalog;

import app.goodbuy.catalog.eansearch.EanSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers an ExternalCatalogClient when explicitly enabled and the provider is "eansearch".
 * Keeps the seam small so we can add more providers later without touching callers.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfig {

    private static final Logger log = LoggerFactory.getLogger(CatalogConfig.class);

    @Bean
    @ConditionalOnProperty(
            prefix = "goodbuy.catalog",
            name = {"enabled", "provider"},
            havingValue = "eansearch",
            matchIfMissing = false
    )
    public ExternalCatalogClient eanSearchCatalogClient(CatalogProperties props) {
        var p = props.getEansearch();

        // Fail-fast validation so we don't start with a broken client
        if (p.getBaseUrl() == null || p.getBaseUrl().isBlank()) {
            throw new IllegalStateException("goodbuy.catalog.eansearch.base-url is required when provider=eansearch");
        }
        if (p.getApiKey() == null || p.getApiKey().isBlank()) {
            throw new IllegalStateException("goodbuy.catalog.eansearch.api-key is required when provider=eansearch");
        }

        // Clamp timeouts to a sane range (100ms .. 30_000ms)
        int connectMs = Math.max(100, Math.min(p.getConnectTimeoutMs(), 30_000));
        int readMs    = Math.max(100, Math.min(p.getReadTimeoutMs(), 30_000));

        log.info("Catalog: registering EAN Search client (baseUrl={}, connectTimeoutMs={}, readTimeoutMs={})",
                p.getBaseUrl(), connectMs, readMs);

        return new EanSearchClient(
                p.getBaseUrl(),
                p.getApiKey(),
                connectMs,
                readMs
        );
    }
}
