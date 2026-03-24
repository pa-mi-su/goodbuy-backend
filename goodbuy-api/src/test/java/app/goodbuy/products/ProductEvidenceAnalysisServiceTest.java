package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.port.ProductEvidenceAnalyzerPort;
import org.junit.jupiter.api.Test;

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
        verify(asyncService).enqueue(any());
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
}
