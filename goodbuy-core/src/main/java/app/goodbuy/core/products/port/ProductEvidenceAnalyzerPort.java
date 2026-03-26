package app.goodbuy.core.products.port;

import java.util.List;
import java.util.Optional;

public interface ProductEvidenceAnalyzerPort {

    Optional<ProductEvidenceAnalysisResult> analyze(ProductEvidenceAnalysisRequest request);

    record ProductEvidenceAnalysisRequest(
            String productEan,
            String reason,
            String hintedProductName,
            String hintedBrandName,
            String rawExtractedText,
            String manualIngredientText,
            boolean frontImageProvided,
            boolean backImageProvided
    ) {}

    record ProductEvidenceAnalysisResult(
            String provider,
            String productName,
            String brandName,
            String domain,
            String category,
            List<String> likelyIngredients,
            Integer confidenceScore,
            String confidenceBand,
            String summary,
            String rawPayload
    ) {}
}
