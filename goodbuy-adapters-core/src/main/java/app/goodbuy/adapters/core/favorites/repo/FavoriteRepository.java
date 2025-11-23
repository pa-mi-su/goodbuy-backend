package app.goodbuy.adapters.core.favorites.repo;

import app.goodbuy.adapters.core.favorites.model.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {

    List<FavoriteEntity> findByUserIdOrderBySavedAtDesc(UUID userId);

    boolean existsByUserIdAndEan(UUID userId, String ean);

    Optional<FavoriteEntity> findByUserIdAndEan(UUID userId, String ean);

    void deleteByUserIdAndEan(UUID userId, String ean);
}
