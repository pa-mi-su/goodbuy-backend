package app.goodbuy.catalog;

import app.goodbuy.catalog.eansearch.EanSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers an ExternalCatalogClient bean (disabled by default).
 * No existing controller/service is changed yet.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfig {

    @Bean
    @ConditionalOnProperty(prefix = "goodbuy.catalog", name = "enabled", havingValue = "true")
    public ExternalCatalogClient externalCatalogClient(CatalogProperties props) {
        var p = props.getEansearch();
        return new EanSearchClient(
                p.getBaseUrl(),
                p.getApiKey(),
                p.getConnectTimeoutMs(),
                p.getReadTimeoutMs()
        );
    }
}
