package app.goodbuy.products;

import app.goodbuy.ingredients.IngredientOnDemandResearchService;
import app.goodbuy.adapters.core.products.scoring.ProductScoringAdapterService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final AsyncProductIngestionService asyncIngestionService = mock(AsyncProductIngestionService.class);
    private final IngredientOnDemandResearchService ingredientOnDemandResearchService = mock(IngredientOnDemandResearchService.class);
    private final ProductScoringAdapterService productScoringAdapterService = mock(ProductScoringAdapterService.class);

    @Test
    void returnsDbSnapshotWhenAlreadyStrictlyScored() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ProductDetailDto scored = dto("00012345678901", new BigDecimal("91.00"), "A");

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.of(scored));

        ProductService service = new ProductService(Optional.empty(), Optional.of(lookup), Optional.empty(), asyncIngestionService, Optional.empty(), ingredientOnDemandResearchService, Optional.of(productScoringAdapterService));

        ProductDetailDto result = service.getByGtinOrNull("00012345678901");

        assertEquals("A", result.ratingLetter());
        verify(asyncIngestionService, never()).enqueue(scored);
    }

    @Test
    void returnsExistingUnscoredProductWhenExternalMisses() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ExternalCatalogClient external = mock(ExternalCatalogClient.class);
        ProductDetailDto partial = dto("00012345678901", null, null);
        when(ingredientOnDemandResearchService.getOrStartResearch("water")).thenReturn(ingredient("water", 95, "A"));
        when(ingredientOnDemandResearchService.getOrStartResearch("glycerin")).thenReturn(ingredient("glycerin", 82, "B"));

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.of(partial));
        when(external.findByGtin("00012345678901")).thenReturn(Optional.empty());

        ProductService service = new ProductService(Optional.of(external), Optional.of(lookup), Optional.empty(), asyncIngestionService, Optional.empty(), ingredientOnDemandResearchService, Optional.of(productScoringAdapterService));

        ProductDetailDto result = service.getByGtinOrNull("00012345678901");

        assertEquals("A", result.ratingLetter());
        assertNotNull(result.safetyScore());
        assertEquals("water", result.ingredients().get(0).id());
        assertEquals("water", result.ingredients().get(0).canonical());
    }

    @Test
    void returnsExternalProductWithoutEnqueueWhenCatalogHasNoIngredients() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ExternalCatalogClient external = mock(ExternalCatalogClient.class);
        ProductDetailDto externalDto = dtoWithoutIngredients("00012345678901", null, null);

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.empty());
        when(external.findByGtin("00012345678901")).thenReturn(Optional.of(externalDto));
        when(ingredientOnDemandResearchService.getOrStartResearch("ingredient-0")).thenReturn(ingredient("ingredient-0", 70, "C"));
        when(ingredientOnDemandResearchService.getOrStartResearch("ingredient-1")).thenReturn(ingredient("ingredient-1", 82, "B"));

        ProductService service = new ProductService(Optional.of(external), Optional.of(lookup), Optional.empty(), asyncIngestionService, Optional.empty(), ingredientOnDemandResearchService, Optional.of(productScoringAdapterService));

        ProductDetailDto result = service.getByGtinOrNull("00012345678901");

        assertEquals(null, result.ratingLetter());
        verify(asyncIngestionService, never()).enqueue(externalDto);
    }

    @Test
    void returnsExternalProductWhenItHasMoreIngredientsThanPartialDbSnapshot() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ExternalCatalogClient external = mock(ExternalCatalogClient.class);
        ProductSnapshotPort snapshot = mock(ProductSnapshotPort.class);
        ProductDetailDto dbPartial = dtoWithIngredients("00012345678901", 1);
        ProductDetailDto externalDto = dtoWithIngredients("00012345678901", 2);

        when(lookup.findByGtin("00012345678901"))
                .thenReturn(Optional.of(dbPartial));
        when(external.findByGtin("00012345678901")).thenReturn(Optional.of(externalDto));
        when(ingredientOnDemandResearchService.getOrStartResearch("ingredient-0")).thenReturn(ingredient("ingredient-0", 82, "B"));
        when(ingredientOnDemandResearchService.getOrStartResearch("ingredient-1")).thenReturn(ingredient("ingredient-1", 70, "C"));

        ProductService service = new ProductService(Optional.of(external), Optional.of(lookup), Optional.of(snapshot), asyncIngestionService, Optional.empty(), ingredientOnDemandResearchService, Optional.of(productScoringAdapterService));

        ProductDetailDto result = service.getByGtinOrNull("00012345678901");

        assertEquals("C", result.ratingLetter());
        assertEquals("ingredient-0", result.ingredients().get(0).id());
        assertEquals("ingredient-0", result.ingredients().get(0).canonical());
        verify(asyncIngestionService).enqueue(externalDto);
    }

    private static ProductDetailDto dto(String gtin, BigDecimal safetyScore, String ratingLetter) {
        return new ProductDetailDto(
                gtin,
                "Demo Product",
                "Brand",
                "Cleaning",
                null,
                List.of(),
                List.of(
                        new ProductDetailDto.IngredientDto("water", "Water", "water", Map.of(), null, null),
                        new ProductDetailDto.IngredientDto("glycerin", "Glycerin", "glycerin", Map.of(), null, null)
                ),
                Map.of(),
                Map.of(),
                "GOODBUY-DB",
                "cleaning",
                safetyScore,
                ratingLetter
        );
    }

    private static ProductDetailDto dtoWithoutIngredients(String gtin, BigDecimal safetyScore, String ratingLetter) {
        return new ProductDetailDto(
                gtin,
                "Demo Product",
                "Brand",
                "Cleaning",
                null,
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "GOODBUY-DB",
                "cleaning",
                safetyScore,
                ratingLetter
        );
    }

    private static ProductDetailDto dtoWithIngredients(String gtin, int count) {
        return new ProductDetailDto(
                gtin,
                "Demo Product",
                "Brand",
                "Cleaning",
                null,
                List.of(),
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new ProductDetailDto.IngredientDto(
                                "ingredient-" + i,
                                "Ingredient " + i,
                                "ingredient-" + i,
                                Map.of(),
                                null,
                                null
                        ))
                        .toList(),
                Map.of(),
                Map.of(),
                "EAN-DB",
                "unknown",
                null,
                null
        );
    }

    private static app.goodbuy.core.ingredients.dto.IngredientDTO ingredient(String canonicalKey, int safetyScore, String ratingLetter) {
        return new app.goodbuy.core.ingredients.dto.IngredientDTO(
                1L,
                canonicalKey,
                canonicalKey,
                "summary",
                "description",
                "formula ingredient",
                "concerns",
                BigDecimal.valueOf(safetyScore),
                ratingLetter,
                0,
                "ingredient",
                "note",
                true,
                List.of("provisional"),
                List.of(canonicalKey),
                null
        );
    }
}
