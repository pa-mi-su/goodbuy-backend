package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductEvidenceAnalyzerPort;
import app.goodbuy.core.products.port.ProductIngredientOcrPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductEvidenceAnalysisServiceTest {

    @Test
    void queuesDraftWhenAnalysisConfidenceIsHigh() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository repo = mock(ProductEvidenceReportRepository.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductEvidenceAnalyzerPort analyzerPort = mock(ProductEvidenceAnalyzerPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00012345678901");
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);
        entity.setFrontImageS3Url("https://goodbuy-dev-images.s3.amazonaws.com/evidence/front.jpg");

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(repo.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzerPort.analyze(any())).thenReturn(Optional.of(
                new ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisResult(
                        "OPENAI",
                        "Pain Relief Tablets",
                        "GoodMed",
                        "medicine",
                        "OTC Medicine",
                        List.of("Acetaminophen", "Microcrystalline Cellulose", "Magnesium Stearate"),
                        92,
                        "high",
                        "Likely OTC pain reliever with a complete tablet ingredient panel.",
                        "{\"ok\":true}"
                )
        ));

        ProductEvidenceAnalysisService service = new ProductEvidenceAnalysisService(
                evidenceReportService,
                repo,
                asyncService,
                Optional.empty(),
                Optional.of(analyzerPort)
        );

        var result = service.analyze(
                "00012345678901",
                ProductEvidenceReportService.REASON_MISSING_PRODUCT,
                null,
                null,
                "1.0",
                "ios",
                "photos submitted",
                null,
                new byte[]{1},
                "image/jpeg",
                new byte[]{2},
                "image/jpeg"
        );

        assertEquals("DRAFT_CREATED", result.analysisStatus());
        assertTrue(result.draftQueued());

        ArgumentCaptor<ProductDetailDto> dtoCaptor = ArgumentCaptor.forClass(ProductDetailDto.class);
        verify(asyncService).enqueue(dtoCaptor.capture());
        assertEquals(1, dtoCaptor.getValue().images().size());
        assertEquals("https://goodbuy-dev-images.s3.amazonaws.com/evidence/front.jpg", dtoCaptor.getValue().images().get(0).url());
    }

    @Test
    void leavesEvidenceInReviewWhenConfidenceIsLow() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository repo = mock(ProductEvidenceReportRepository.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductEvidenceAnalyzerPort analyzerPort = mock(ProductEvidenceAnalyzerPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00012345678901");
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(repo.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzerPort.analyze(any())).thenReturn(Optional.of(
                new ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisResult(
                        "OPENAI",
                        "",
                        "",
                        "unknown",
                        "",
                        List.of("fragrance"),
                        41,
                        "low",
                        "Sparse OCR text and weak product identification.",
                        "{\"ok\":true}"
                )
        ));

        ProductEvidenceAnalysisService service = new ProductEvidenceAnalysisService(
                evidenceReportService,
                repo,
                asyncService,
                Optional.empty(),
                Optional.of(analyzerPort)
        );

        var result = service.analyze(
                "00012345678901",
                ProductEvidenceReportService.REASON_OUT_OF_DOMAIN,
                null,
                null,
                "1.0",
                "ios",
                "photos submitted",
                null,
                new byte[]{1},
                "image/jpeg",
                null,
                null
        );

        assertEquals("REVIEW_REQUIRED", result.analysisStatus());
        assertEquals(false, result.draftQueued());
    }

    @Test
    void onlyParsesIngredientPanelTextFromOcr() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository repo = mock(ProductEvidenceReportRepository.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductEvidenceAnalyzerPort analyzerPort = mock(ProductEvidenceAnalyzerPort.class);
        ProductIngredientOcrPort ocrPort = mock(ProductIngredientOcrPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00381371175666");
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(repo.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrPort.extract(any())).thenReturn(Optional.of(
                new ProductIngredientOcrPort.ProductIngredientOcrResult(
                        """
                        Johnson's No More Tears Wash & Shampoo
                        Gentle enough for daily use
                        Ingredients: Water, Cocamidopropyl Betaine, Sodium Benzoate
                        No parabens, phthalates or dyes
                        0.85 FLOZ (25 mL)
                        Made in Canada
                        """,
                        "textract",
                        null
                )
        ));
        when(analyzerPort.analyze(any())).thenReturn(Optional.of(
                new ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisResult(
                        "OPENAI",
                        "Johnson's Wash & Shampoo",
                        "Johnson's",
                        "personal-care",
                        "Personal Care",
                        List.of("Water", "Cocamidopropyl Betaine", "Sodium Benzoate", "No parabens"),
                        88,
                        "high",
                        "Likely baby wash and shampoo.",
                        "{\"ok\":true}"
                )
        ));

        ProductEvidenceAnalysisService service = new ProductEvidenceAnalysisService(
                evidenceReportService,
                repo,
                asyncService,
                Optional.of(ocrPort),
                Optional.of(analyzerPort)
        );

        var result = service.analyze(
                "00381371175666",
                ProductEvidenceReportService.REASON_MISSING_PRODUCT,
                null,
                null,
                "1.0",
                "ios",
                "photos submitted",
                null,
                new byte[]{1},
                "image/jpeg",
                new byte[]{2},
                "image/jpeg"
        );

        assertEquals(3, result.parsedIngredientCount());
        verify(asyncService).enqueue(any());
    }

    @Test
    void ignoresNoisyOcrTextWithoutIngredientSection() {
        ProductEvidenceReportService evidenceReportService = mock(ProductEvidenceReportService.class);
        ProductEvidenceReportRepository repo = mock(ProductEvidenceReportRepository.class);
        AsyncProductIngestionService asyncService = mock(AsyncProductIngestionService.class);
        ProductEvidenceAnalyzerPort analyzerPort = mock(ProductEvidenceAnalyzerPort.class);
        ProductIngredientOcrPort ocrPort = mock(ProductIngredientOcrPort.class);

        ProductEvidenceReportEntity entity = new ProductEvidenceReportEntity();
        entity.setEan("00381371175666");
        entity.setStatus(ProductEvidenceReportService.STATUS_REPORTED);

        when(evidenceReportService.reportWithStatus(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ProductEvidenceReportService.ProductEvidenceReportResult(entity, true));
        when(repo.save(any(ProductEvidenceReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrPort.extract(any())).thenReturn(Optional.of(
                new ProductIngredientOcrPort.ProductIngredientOcrResult(
                        """
                        Johnson's No More Tears
                        Wash & Shampoo
                        No parabens, phthalates or dyes
                        0.85 FLOZ (25 mL)
                        Made in Canada
                        """,
                        "textract",
                        null
                )
        ));
        when(analyzerPort.analyze(any())).thenReturn(Optional.of(
                new ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisResult(
                        "OPENAI",
                        "Johnson's Wash & Shampoo",
                        "Johnson's",
                        "personal-care",
                        "Personal Care",
                        List.of("Wash & Shampoo", "No parabens"),
                        55,
                        "medium",
                        "Noisy OCR text only.",
                        "{\"ok\":true}"
                )
        ));

        ProductEvidenceAnalysisService service = new ProductEvidenceAnalysisService(
                evidenceReportService,
                repo,
                asyncService,
                Optional.of(ocrPort),
                Optional.of(analyzerPort)
        );

        var result = service.analyze(
                "00381371175666",
                ProductEvidenceReportService.REASON_MISSING_PRODUCT,
                null,
                null,
                "1.0",
                "ios",
                "photos submitted",
                null,
                new byte[]{1},
                "image/jpeg",
                new byte[]{2},
                "image/jpeg"
        );

        assertEquals(0, result.parsedIngredientCount());
        assertEquals("REVIEW_REQUIRED", result.analysisStatus());
    }
}
