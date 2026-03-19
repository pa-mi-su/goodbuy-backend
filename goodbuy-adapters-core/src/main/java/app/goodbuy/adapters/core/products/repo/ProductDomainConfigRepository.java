package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductDomainConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductDomainConfigRepository extends JpaRepository<ProductDomainConfigEntity, String> {
}
