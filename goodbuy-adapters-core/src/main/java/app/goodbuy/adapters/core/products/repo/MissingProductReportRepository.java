package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.MissingProductReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissingProductReportRepository
        extends JpaRepository<MissingProductReportEntity, Long> {

    /**
     * Returns the (single) missing-product record for a given EAN, if any.
     *
     * With a UNIQUE index on ean, this effectively enforces:
     *   - at most ONE row per EAN in product_missing_report.
     */
    Optional<MissingProductReportEntity> findByEan(String ean);
}
