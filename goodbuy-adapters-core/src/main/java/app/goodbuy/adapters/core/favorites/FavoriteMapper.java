package app.goodbuy.adapters.core.favorites;

import app.goodbuy.adapters.core.favorites.model.FavoriteEntity;
import app.goodbuy.core.favorites.dto.FavoriteDTO;

public class FavoriteMapper {

    public static FavoriteDTO toDTO(FavoriteEntity e) {
        return new FavoriteDTO(
                e.getId() != null ? e.getId() : 0L,
                e.getEan(),
                e.getProductName(),
                e.getBrand(),
                e.getRatingLetter(),
                e.getSafetyScore()
        );
    }
}
