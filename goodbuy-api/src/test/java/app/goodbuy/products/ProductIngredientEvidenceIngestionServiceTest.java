package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIngredientEvidenceIngestionServiceTest {

    @Test
    void queuesReprocessWhenManualIngredientTextParses() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository evidenceReportRepository = mock(ProductEvidenceReportRepository.class);
        ProductLookupPort lookupPort = mock(ProductLookupPort.class);
        ExternalCatalogClient externalCatalogClient = mock(ExternalCatalogClient.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductSnapshotPort snapshotPort = mock(ProductSnapshotPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00012345678901");
        entity.setReason(ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS);
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setProductName("Demo Product");
        entity.setBrand("Brand");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(evidenceReportRepository.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupPort.findByGtin("00012345678901")).thenReturn(Optional.of(existingDto()));

        ProductIngredientEvidenceIngestionService service = new ProductIngredientEvidenceIngestionService(
                evidenceReportService,
                evidenceReportRepository,
                Optional.of(lookupPort),
                Optional.of(externalCatalogClient),
                asyncService,
                Optional.empty(),
                Optional.of(snapshotPort)
        );

        var result = service.ingest(
                "00012345678901",
                "Demo Product",
                "Brand",
                "1.0",
                "ios",
                "user submitted label",
                "Ingredients: Water, Glycerin, Less than 2% of: Citric Acid, Fragrance",
                null,
                null,
                null,
                null
        );

        assertEquals("COMPLETED", result.ocrStatus());
        assertEquals(4, result.parsedIngredientCount());
        assertTrue(result.reprocessQueued());
        assertEquals("READY_TO_RESCAN", result.status());
        assertEquals("READY_TO_RESCAN", result.analysisStatus());
        verify(snapshotPort).saveSnapshot(argThat(dto ->
                dto != null
                        && "00012345678901".equals(dto.gtin())
                        && "AI-PRODUCT-INTAKE".equals(dto.source())
                        && dto.ingredients() != null
                        && dto.ingredients().size() == 4
                        && dto.images() != null
                        && dto.images().isEmpty()
        ));
        verify(evidenceReportRepository).save(entity);
    }

    @Test
    void ignoresDirectionsAndOnlyParsesIngredientPanelText() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository evidenceReportRepository = mock(ProductEvidenceReportRepository.class);
        ProductLookupPort lookupPort = mock(ProductLookupPort.class);
        ExternalCatalogClient externalCatalogClient = mock(ExternalCatalogClient.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductSnapshotPort snapshotPort = mock(ProductSnapshotPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00749174097279");
        entity.setReason(ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS);
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setProductName("Dishmate Liquid, Lavender - 25 fl oz");
        entity.setBrand("ECOS");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(evidenceReportRepository.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupPort.findByGtin("00749174097279")).thenReturn(Optional.of(existingDto()));

        ProductIngredientEvidenceIngestionService service = new ProductIngredientEvidenceIngestionService(
                evidenceReportService,
                evidenceReportRepository,
                Optional.of(lookupPort),
                Optional.of(externalCatalogClient),
                asyncService,
                Optional.empty(),
                Optional.of(snapshotPort)
        );

        var result = service.ingest(
                "00749174097279",
                "Dishmate Liquid, Lavender - 25 fl oz",
                "ECOS",
                "1.0",
                "ios",
                "user submitted label",
                """
                Squirt a quarter-size drop onto a sponge and clean away! Increase the amount for greasier items.
                ONLY 11 INGREDIENTS
                Ingredients: 1. Water 2. Sodium Coco-Sulfate (plant-derived,surfactant) 3. Cocamidopropylamine Oxide
                4. Lauramine Oxide (plant-derived) 5. Phenoxyethanol
                """,
                null,
                null,
                null,
                null
        );

        assertEquals("COMPLETED", result.ocrStatus());
        assertEquals(5, result.parsedIngredientCount());
        verify(snapshotPort).saveSnapshot(argThat(dto ->
                dto != null
                        && dto.ingredients() != null
                        && dto.ingredients().size() == 5
                        && dto.ingredients().stream().noneMatch(ing -> ing.original().toLowerCase().contains("squirt"))
                        && dto.ingredients().stream().noneMatch(ing -> ing.original().toLowerCase().contains("clean away"))
                        && dto.ingredients().stream().noneMatch(ing -> ing.original().toLowerCase().contains("greasier"))
        ));
    }

    @Test
    void parsesNumberedIngredientListWithoutExplicitIngredientsHeader() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository evidenceReportRepository = mock(ProductEvidenceReportRepository.class);
        ProductLookupPort lookupPort = mock(ProductLookupPort.class);
        ExternalCatalogClient externalCatalogClient = mock(ExternalCatalogClient.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductSnapshotPort snapshotPort = mock(ProductSnapshotPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00749174097279");
        entity.setReason(ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS);
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setProductName("Dishmate Liquid, Lavender - 25 fl oz");
        entity.setBrand("ECOS");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(evidenceReportRepository.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupPort.findByGtin("00749174097279")).thenReturn(Optional.of(existingDto()));

        ProductIngredientEvidenceIngestionService service = new ProductIngredientEvidenceIngestionService(
                evidenceReportService,
                evidenceReportRepository,
                Optional.of(lookupPort),
                Optional.of(externalCatalogClient),
                asyncService,
                Optional.empty(),
                Optional.of(snapshotPort)
        );

        var result = service.ingest(
                "00749174097279",
                "Dishmate Liquid, Lavender - 25 fl oz",
                "ECOS",
                "1.0",
                "ios",
                "user submitted label",
                """
                Squirt a quarter-size drop onto a sponge and clean away! Increase the amount for greasier items.
                ONLY 11 INGREDIENTS
                1. Water 2. Sodium Coco-Sulfate (plant-derived,surfactant) 3. Cocamidopropylamine Oxide
                4. Lauramine Oxide (plant-derived) 5. Phenoxyethanol
                """,
                null,
                null,
                null,
                null
        );

        assertEquals("COMPLETED", result.ocrStatus());
        assertEquals(5, result.parsedIngredientCount());
        assertTrue(result.reprocessQueued());
    }

    @Test
    void doesNotQueueReprocessWhenIngredientTextIsUnreadable() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository evidenceReportRepository = mock(ProductEvidenceReportRepository.class);
        ProductLookupPort lookupPort = mock(ProductLookupPort.class);
        ExternalCatalogClient externalCatalogClient = mock(ExternalCatalogClient.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductSnapshotPort snapshotPort = mock(ProductSnapshotPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00012345678901");
        entity.setReason(ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS);
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setProductName("Demo Product");
        entity.setBrand("Brand");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(evidenceReportRepository.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupPort.findByGtin("00012345678901")).thenReturn(Optional.of(existingDto()));

        ProductIngredientEvidenceIngestionService service = new ProductIngredientEvidenceIngestionService(
                evidenceReportService,
                evidenceReportRepository,
                Optional.of(lookupPort),
                Optional.of(externalCatalogClient),
                asyncService,
                Optional.empty(),
                Optional.of(snapshotPort)
        );

        var result = service.ingest(
                "00012345678901",
                "Demo Product",
                "Brand",
                "1.0",
                "ios",
                "user submitted label",
                "Warning: Keep out of reach of children.",
                null,
                null,
                null,
                null
        );

        assertEquals("PARSE_EMPTY", result.ocrStatus());
        assertEquals(0, result.parsedIngredientCount());
        assertFalse(result.reprocessQueued());
        assertEquals("REVIEW_REQUIRED", result.status());
        assertEquals("REVIEW_REQUIRED", result.analysisStatus());
        verify(evidenceReportRepository).save(entity);
    }

    @Test
    void carriesForwardExternalCatalogImagesWhenDbProductDoesNotExistYet() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository evidenceReportRepository = mock(ProductEvidenceReportRepository.class);
        ProductLookupPort lookupPort = mock(ProductLookupPort.class);
        ExternalCatalogClient externalCatalogClient = mock(ExternalCatalogClient.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductSnapshotPort snapshotPort = mock(ProductSnapshotPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00749174097279");
        entity.setReason(ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS);
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setProductName("Dishmate Liquid, Lavender - 25 fl oz");
        entity.setBrand("ECOS");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(evidenceReportRepository.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupPort.findByGtin("00749174097279")).thenReturn(Optional.empty());
        when(externalCatalogClient.findByGtin("00749174097279")).thenReturn(Optional.of(externalDto()));

        ProductIngredientEvidenceIngestionService service = new ProductIngredientEvidenceIngestionService(
                evidenceReportService,
                evidenceReportRepository,
                Optional.of(lookupPort),
                Optional.of(externalCatalogClient),
                asyncService,
                Optional.empty(),
                Optional.of(snapshotPort)
        );

        service.ingest(
                "00749174097279",
                "Dishmate Liquid, Lavender - 25 fl oz",
                "ECOS",
                "1.0",
                "ios",
                "user submitted label",
                "Ingredients: Water, Sodium Coco-Sulfate, Cocamidopropylamine Oxide",
                null,
                null,
                null,
                null
        );

        verify(snapshotPort).saveSnapshot(argThat(dto ->
                dto != null
                        && "00749174097279".equals(dto.gtin())
                        && dto.images() != null
                        && dto.images().size() == 1
                        && "https://cdn.example.com/dishmate-front.jpg".equals(dto.images().get(0).url())
        ));
    }

    private static ProductDetailDto existingDto() {
        return new ProductDetailDto(
                "00012345678901",
                "Demo Product",
                "Brand",
                "personal-care",
                "desc",
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "GOODBUY-DB",
                "personal-care",
                null,
                null
        );
    }

    private static ProductDetailDto externalDto() {
        return new ProductDetailDto(
                "00749174097279",
                "Dishmate Liquid, Lavender - 25 fl oz",
                "ECOS",
                "dish detergent",
                "desc",
                List.of(new ProductDetailDto.ImageDto("https://cdn.example.com/dishmate-front.jpg", 400, 800)),
                List.of(),
                Map.of(),
                Map.of(),
                "EAN-DB",
                "cleaning",
                null,
                null
        );
    }
}
