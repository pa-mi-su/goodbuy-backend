package app.goodbuy.core.favorites.dto;

public record FavoriteDTO(
        long id,
        String ean,
        String productName,
        String brand,
        String ratingLetter,
        Double safetyScore
) {}
