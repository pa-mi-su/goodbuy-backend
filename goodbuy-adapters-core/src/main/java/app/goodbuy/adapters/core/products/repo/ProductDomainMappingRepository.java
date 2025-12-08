package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductDomainMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for product_domain_mapping rules.
 *
 * For now we keep it very simple: callers just load all active rules
 * ordered by priority (lowest first) and apply them in memory.
 */
@Repository
public interface ProductDomainMappingRepository
        extends JpaRepository<ProductDomainMappingEntity, Long> {

    /**
     * Load all active mapping rules in priority order.
     *
     * Lowest priority value wins (0, 1, 2, ...).
     */
    List<ProductDomainMappingEntity> findByActiveTrueOrderByPriorityAsc();
}
