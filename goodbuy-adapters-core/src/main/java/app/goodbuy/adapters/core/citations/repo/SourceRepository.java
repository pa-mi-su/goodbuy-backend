package app.goodbuy.adapters.core.citations.repo;

import app.goodbuy.adapters.core.citations.model.SourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceRepository extends JpaRepository<SourceEntity, Long> {
    Optional<SourceEntity> findByNameIgnoreCase(String name);
}
