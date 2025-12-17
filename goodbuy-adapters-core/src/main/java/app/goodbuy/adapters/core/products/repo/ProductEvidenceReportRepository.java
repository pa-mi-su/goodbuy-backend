package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ProductEvidenceReportRepository
        extends JpaRepository<ProductEvidenceReportEntity, Long> {

    Optional<ProductEvidenceReportEntity> findByEanAndReason(String ean, String reason);

    // ✅ Legacy: ok for now, but do NOT use for “already reported” once status matters
    boolean existsByEanAndReason(String ean, String reason);

    // ✅ Production-ready: “already reported” should mean: active status exists
    boolean existsByEanAndReasonAndStatusIn(
            String ean,
            String reason,
            Collection<String> statuses
    );

    Optional<ProductEvidenceReportEntity> findByEanAndReasonAndStatusIn(
            String ean,
            String reason,
            Collection<String> statuses
    );
}
