package app.goodbuy.adapters.core.favorites.service;

import app.goodbuy.adapters.core.favorites.repo.FavoriteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public FavoriteService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * Delete a favorite for a given user + EAN.
     * If it doesn't exist, this is a no-op.
     */
    @Transactional
    public void deleteFavorite(UUID userId, String ean) {
        favoriteRepository.deleteByUserIdAndEan(userId, ean);
    }
}
