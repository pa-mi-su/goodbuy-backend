package app.goodbuy.adapters.catalog.composite;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompositeExternalCatalogClientTest {

    @Test
    void returnsFirstSuccessfulClientResult() {
        ExternalCatalogClient miss = gtin -> Optional.empty();
        ExternalCatalogClient hit = gtin -> Optional.of(new ProductDetailDto(
                gtin,
                "Recovered Product",
                "Brand",
                "Cleaning",
                null,
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "OPEN-PRODUCTS-FACTS",
                "unknown",
                null,
                null
        ));

        CompositeExternalCatalogClient client = new CompositeExternalCatalogClient(List.of(
                new CompositeExternalCatalogClient.NamedClient("MISS", miss),
                new CompositeExternalCatalogClient.NamedClient("HIT", hit)
        ));

        ProductDetailDto result = client.findByGtin("00012345678901").orElseThrow();

        assertEquals("Recovered Product", result.name());
        assertEquals("OPEN-PRODUCTS-FACTS", result.source());
    }

    @Test
    void throwsLastTransportErrorWhenAllClientsFailTransport() {
        ExternalCatalogClient failing = gtin -> {
            throw new CatalogTransportException("catalog_down");
        };

        CompositeExternalCatalogClient client = new CompositeExternalCatalogClient(List.of(
                new CompositeExternalCatalogClient.NamedClient("FAIL1", failing),
                new CompositeExternalCatalogClient.NamedClient("FAIL2", failing)
        ));

        assertThrows(CatalogTransportException.class, () -> client.findByGtin("00012345678901"));
    }
}
