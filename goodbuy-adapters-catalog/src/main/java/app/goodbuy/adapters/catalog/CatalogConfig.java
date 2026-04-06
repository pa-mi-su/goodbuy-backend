package app.goodbuy.adapters.catalog;

import app.goodbuy.adapters.catalog.composite.CompositeExternalCatalogClient;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.adapters.catalog.eansearch.EanSearchClient;
import app.goodbuy.adapters.catalog.eandb.EanDbCatalogClient;
import app.goodbuy.adapters.catalog.openfacts.OpenFactsCatalogClient;
import app.goodbuy.adapters.catalog.upcitemdb.UpcItemDbCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires the external catalog client(s).
 */
@Configuration
@ConditionalOnProperty(name = "goodbuy.catalog.enabled", havingValue = "true")
public class CatalogConfig {

    private static final Logger log = LoggerFactory.getLogger(CatalogConfig.class);

    @Bean
    public ExternalCatalogClient externalCatalogClient(
            @Value("${goodbuy.catalog.provider:multi}") String provider,
            @Value("${goodbuy.catalog.lookup-order:openproductsfacts,openbeautyfacts,openfoodfacts,upcitemdb,eansearch,eandb}") String lookupOrder,

            @Value("${goodbuy.catalog.user-agent:GoodBuy-Backend/0.1 (+https://goodbuy.app)}") String userAgent,

            @Value("${goodbuy.catalog.openfoodfacts.base-url:https://world.openfoodfacts.org}") String openFoodFactsBaseUrl,
            @Value("${goodbuy.catalog.openfoodfacts.connect-timeout-ms:2000}") int openFoodFactsConnectTimeoutMs,
            @Value("${goodbuy.catalog.openfoodfacts.read-timeout-ms:3500}") int openFoodFactsReadTimeoutMs,

            @Value("${goodbuy.catalog.openbeautyfacts.base-url:https://world.openbeautyfacts.org}") String openBeautyFactsBaseUrl,
            @Value("${goodbuy.catalog.openbeautyfacts.connect-timeout-ms:2000}") int openBeautyFactsConnectTimeoutMs,
            @Value("${goodbuy.catalog.openbeautyfacts.read-timeout-ms:3500}") int openBeautyFactsReadTimeoutMs,

            @Value("${goodbuy.catalog.openproductsfacts.base-url:https://world.openproductsfacts.org}") String openProductsFactsBaseUrl,
            @Value("${goodbuy.catalog.openproductsfacts.connect-timeout-ms:2000}") int openProductsFactsConnectTimeoutMs,
            @Value("${goodbuy.catalog.openproductsfacts.read-timeout-ms:3500}") int openProductsFactsReadTimeoutMs,

            @Value("${goodbuy.catalog.upcitemdb.base-url:https://api.upcitemdb.com/prod/trial/lookup}") String upcItemDbBaseUrl,
            @Value("${goodbuy.catalog.upcitemdb.api-key:}") String upcItemDbApiKey,
            @Value("${goodbuy.catalog.upcitemdb.connect-timeout-ms:2000}") int upcItemDbConnectTimeoutMs,
            @Value("${goodbuy.catalog.upcitemdb.read-timeout-ms:3000}") int upcItemDbReadTimeoutMs,

            @Value("${goodbuy.catalog.eansearch.base-url:}") String eanSearchBaseUrl,
            @Value("${goodbuy.catalog.eansearch.api-key:}") String eanSearchApiKey,
            @Value("${goodbuy.catalog.eansearch.connect-timeout-ms:2000}") int eanSearchConnectTimeoutMs,
            @Value("${goodbuy.catalog.eansearch.read-timeout-ms:3000}") int eanSearchReadTimeoutMs,

            @Value("${goodbuy.catalog.eandb.base-url:}") String eanDbBaseUrl,
            @Value("${goodbuy.catalog.eandb.jwt:}") String eanDbJwt,
            @Value("${goodbuy.catalog.eandb.connect-timeout-ms:3000}") int eanDbConnectTimeoutMs,
            @Value("${goodbuy.catalog.eandb.read-timeout-ms:4000}") int eanDbReadTimeoutMs
    ) {
        List<CompositeExternalCatalogClient.NamedClient> clients = new ArrayList<>();

        for (String rawEntry : lookupOrder.split(",")) {
            String entry = rawEntry.trim().toLowerCase();
            if (entry.isEmpty()) {
                continue;
            }

            switch (entry) {
                case "openfoodfacts" -> clients.add(new CompositeExternalCatalogClient.NamedClient(
                        "OPEN-FOOD-FACTS",
                        new OpenFactsCatalogClient(
                                openFoodFactsBaseUrl,
                                "OPEN-FOOD-FACTS",
                                "food",
                                userAgent,
                                clamp(openFoodFactsConnectTimeoutMs),
                                clamp(openFoodFactsReadTimeoutMs)
                        )
                ));
                case "openbeautyfacts" -> clients.add(new CompositeExternalCatalogClient.NamedClient(
                        "OPEN-BEAUTY-FACTS",
                        new OpenFactsCatalogClient(
                                openBeautyFactsBaseUrl,
                                "OPEN-BEAUTY-FACTS",
                                "personal-care",
                                userAgent,
                                clamp(openBeautyFactsConnectTimeoutMs),
                                clamp(openBeautyFactsReadTimeoutMs)
                        )
                ));
                case "openproductsfacts" -> clients.add(new CompositeExternalCatalogClient.NamedClient(
                        "OPEN-PRODUCTS-FACTS",
                        new OpenFactsCatalogClient(
                                openProductsFactsBaseUrl,
                                "OPEN-PRODUCTS-FACTS",
                                "household",
                                userAgent,
                                clamp(openProductsFactsConnectTimeoutMs),
                                clamp(openProductsFactsReadTimeoutMs)
                        )
                ));
                case "upcitemdb" -> clients.add(new CompositeExternalCatalogClient.NamedClient(
                        "UPCITEMDB",
                        new UpcItemDbCatalogClient(
                                upcItemDbBaseUrl,
                                upcItemDbApiKey,
                                userAgent,
                                clamp(upcItemDbConnectTimeoutMs),
                                clamp(upcItemDbReadTimeoutMs)
                        )
                ));
                case "eansearch" -> {
                    if (hasText(eanSearchBaseUrl) && hasText(eanSearchApiKey)) {
                        clients.add(new CompositeExternalCatalogClient.NamedClient(
                                "EAN-SEARCH",
                                new EanSearchClient(
                                        eanSearchBaseUrl,
                                        eanSearchApiKey,
                                        clamp(eanSearchConnectTimeoutMs),
                                        clamp(eanSearchReadTimeoutMs)
                                )
                        ));
                    } else {
                        log.info("Catalog: skipping EAN-Search because base URL or API key is missing");
                    }
                }
                case "eandb" -> {
                    if (hasText(eanDbBaseUrl) && hasText(eanDbJwt)) {
                        clients.add(new CompositeExternalCatalogClient.NamedClient(
                                "EAN-DB",
                                new EanDbCatalogClient(
                                        eanDbBaseUrl,
                                        eanDbJwt,
                                        clamp(eanDbConnectTimeoutMs),
                                        clamp(eanDbReadTimeoutMs)
                                )
                        ));
                    } else {
                        log.info("Catalog: skipping EAN-DB because base URL or JWT is missing");
                    }
                }
                default -> log.warn("Catalog: ignoring unknown lookup source '{}'", entry);
            }
        }

        if (clients.isEmpty()) {
            throw new IllegalStateException("No external catalog clients were configured for provider=" + provider);
        }

        log.info("Catalog: provider={} order={} activeClients={}",
                provider, lookupOrder, clients.stream().map(CompositeExternalCatalogClient.NamedClient::name).toList());
        return new CompositeExternalCatalogClient(clients);
    }

    // ---- helpers ----
    private static int clamp(int ms) {
        // keep within sane bounds (100ms .. 30s)
        return Math.max(100, Math.min(ms, 30_000));
    }
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
