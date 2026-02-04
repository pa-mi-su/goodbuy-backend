package app.goodbuy.adapters.core.citations.repo;

import app.goodbuy.adapters.core.citations.model.CitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CitationRepository extends JpaRepository<CitationEntity, Long> {
    Optional<CitationEntity> findByUrl(String url);
}
