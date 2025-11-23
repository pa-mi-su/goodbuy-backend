package app.goodbuy.adapters.core.history.repo;

import app.goodbuy.adapters.core.history.model.ScanHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanHistoryRepository extends JpaRepository<ScanHistoryEntity, Long> {

    List<ScanHistoryEntity> findByUserIdOrderByScannedAtDesc(UUID userId);

    Optional<ScanHistoryEntity> findByUserIdAndEan(UUID userId, String ean); // ← ADD THIS
}
