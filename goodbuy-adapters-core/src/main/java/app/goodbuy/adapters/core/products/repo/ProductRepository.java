package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByEan(String ean);
}
