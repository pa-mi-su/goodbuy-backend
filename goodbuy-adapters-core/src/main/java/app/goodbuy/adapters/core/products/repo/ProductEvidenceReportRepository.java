package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductEvidenceReportRepository
        extends JpaRepository<ProductEvidenceReportEntity, Long> {

    Optional<ProductEvidenceReportEntity> findByEanAndReason(String ean, String reason);

    boolean existsByEanAndReason(String ean, String reason);
}
