package app.goodbuy.api.admin;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientMissingReportEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientMissingReportRepository;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.products.ProductRawIngredientReprocessingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientReviewAdminServiceTest {

    @Test
    void resolvesMissingIngredientAndReprocessesAffectedProducts() {
        IngredientMissingReportRepository missingReportRepository = mock(IngredientMissingReportRepository.class);
        IngredientRepository ingredientRepository = mock(IngredientRepository.class);
        ProductRawIngredientReprocessingService reprocessingService = mock(ProductRawIngredientReprocessingService.class);

        IngredientMissingReportEntity report = new IngredientMissingReportEntity();
        report.setIngredientName("Prenatal DHA Complex");
        report.setProductEan("00016500586579");
        report.setOccurredAt(Instant.now());
        report.setStatus("OPEN");

        when(ingredientRepository.findByCanonicalKeyIgnoreCase("prenatal dha complex")).thenReturn(Optional.empty());
        when(ingredientRepository.findByNameOrAlias("prenatal dha complex")).thenReturn(Optional.empty());
        when(ingredientRepository.saveAndFlush(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(missingReportRepository.findByIngredientNameIgnoreCaseAndStatus("Prenatal DHA Complex", "OPEN"))
                .thenReturn(List.of(report));
        when(reprocessingService.reprocessFromStoredRawText("00016500586579")).thenReturn(true);

        IngredientReviewAdminService service = new IngredientReviewAdminService(
                missingReportRepository,
                ingredientRepository,
                reprocessingService
        );

        var result = service.resolveMissingIngredient(
                "Prenatal DHA Complex",
                null,
                "Prenatal DHA Complex"
        );

        assertEquals("prenatal dha complex", result.canonicalKey());
        assertEquals(1, result.resolvedReports());
        assertEquals(1, result.reprocessedProducts());
        assertEquals("RESOLVED", report.getStatus());
        assertNotNull(report.getResolvedAt());
        assertEquals("prenatal dha complex", report.getResolvedCanonicalKey());
        verify(missingReportRepository).saveAll(any());
    }

    @Test
    void listsOpenQueue() {
        IngredientMissingReportRepository missingReportRepository = mock(IngredientMissingReportRepository.class);
        IngredientRepository ingredientRepository = mock(IngredientRepository.class);
        ProductRawIngredientReprocessingService reprocessingService = mock(ProductRawIngredientReprocessingService.class);

        IngredientMissingReportEntity report = new IngredientMissingReportEntity();
        report.setIngredientName("Unknown Blend");
        report.setProductEan("00016500586579");
        report.setOccurredAt(Instant.now());
        report.setStatus("OPEN");

        when(missingReportRepository.findByStatusOrderByOccurredAtDesc("OPEN")).thenReturn(List.of(report));

        IngredientReviewAdminService service = new IngredientReviewAdminService(
                missingReportRepository,
                ingredientRepository,
                reprocessingService
        );

        var result = service.listOpenQueue(10);
        assertEquals(1, result.size());
        assertEquals("Unknown Blend", result.get(0).ingredientName());
        assertTrue(result.get(0).productEan().contains("586579"));
    }
}
