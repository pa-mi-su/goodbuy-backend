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
    void returnsSuccessfulClientResultWhenOnlyOneProviderHits() {
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
    void prefersRicherResultWhenLaterProviderHasIngredients() {
        ExternalCatalogClient shallowHit = gtin -> Optional.of(new ProductDetailDto(
                gtin,
                "Weak Product",
                "Weak Brand",
                "Cleaning",
                null,
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "UPCITEMDB",
                "unknown",
                null,
                null
        ));
        ExternalCatalogClient richHit = gtin -> Optional.of(new ProductDetailDto(
                gtin,
                "Rich Product",
                "Rich Brand",
                "Cleaning",
                "Has ingredients",
                List.of(new ProductDetailDto.ImageDto("https://example.com/product.jpg", null, null)),
                List.of(
                        new ProductDetailDto.IngredientDto("water", "Water", "Water", Map.of(), null, null),
                        new ProductDetailDto.IngredientDto("soap", "Soap", "Soap", Map.of(), null, null)
                ),
                Map.of(),
                Map.of(),
                "OPEN-PRODUCTS-FACTS",
                "cleaning",
                null,
                null
        ));

        CompositeExternalCatalogClient client = new CompositeExternalCatalogClient(List.of(
                new CompositeExternalCatalogClient.NamedClient("SHALLOW", shallowHit),
                new CompositeExternalCatalogClient.NamedClient("RICH", richHit)
        ));

        ProductDetailDto result = client.findByGtin("00012345678901").orElseThrow();

        assertEquals("Rich Product", result.name());
        assertEquals(2, result.ingredients().size());
        assertEquals("OPEN-PRODUCTS-FACTS", result.source());
    }

    @Test
    void fillsMissingFieldsFromOtherHitsWhenBestResultIsSparse() {
        ExternalCatalogClient richIngredientsNoBrand = gtin -> Optional.of(new ProductDetailDto(
                gtin,
                "Recovered Product",
                null,
                "Cleaning",
                null,
                List.of(),
                List.of(new ProductDetailDto.IngredientDto("water", "Water", "Water", Map.of(), null, null)),
                Map.of(),
                Map.of(),
                "OPEN-PRODUCTS-FACTS",
                "cleaning",
                null,
                null
        ));
        ExternalCatalogClient identityHit = gtin -> Optional.of(new ProductDetailDto(
                gtin,
                "Recovered Product",
                "Known Brand",
                "Cleaning",
                "Helpful description",
                List.of(new ProductDetailDto.ImageDto("https://example.com/product.jpg", null, null)),
                List.of(),
                Map.of("en", "Recovered Product"),
                Map.of("en", "Known Brand"),
                "UPCITEMDB",
                "cleaning",
                null,
                null
        ));

        CompositeExternalCatalogClient client = new CompositeExternalCatalogClient(List.of(
                new CompositeExternalCatalogClient.NamedClient("RICH", richIngredientsNoBrand),
                new CompositeExternalCatalogClient.NamedClient("IDENTITY", identityHit)
        ));

        ProductDetailDto result = client.findByGtin("00012345678901").orElseThrow();

        assertEquals("Known Brand", result.brand());
        assertEquals(1, result.ingredients().size());
        assertEquals(1, result.images().size());
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
