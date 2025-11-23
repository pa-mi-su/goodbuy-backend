package app.goodbuy.favorites;

import app.goodbuy.adapters.core.favorites.FavoriteMapper;
import app.goodbuy.adapters.core.favorites.model.FavoriteEntity;
import app.goodbuy.adapters.core.favorites.repo.FavoriteRepository;
import app.goodbuy.adapters.core.favorites.service.FavoriteService;
import app.goodbuy.core.favorites.dto.FavoriteDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@Validated
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteRepository favoriteRepository;
    private final FavoriteService favoriteService;

    public FavoriteController(
            FavoriteRepository favoriteRepository,
            FavoriteService favoriteService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.favoriteService = favoriteService;
    }

    // ─────────────────────────────────────
    // GET: list favorites
    // ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<FavoriteDTO>> listFavorites(
            @RequestParam("userId") @NotBlank String userIdRaw
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("FavoriteController.listFavorites called with invalid userId='{}'", userIdRaw);
            return ResponseEntity.badRequest().build();
        }

        List<FavoriteEntity> entities = favoriteRepository.findByUserIdOrderBySavedAtDesc(userId);

        if (entities.isEmpty()) {
            // 204 → iOS treats as "no favorites yet"
            return ResponseEntity.noContent().build();
        }

        List<FavoriteDTO> dtos = entities.stream()
                .map(FavoriteMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    // ─────────────────────────────────────
    // GET: exists
    // ─────────────────────────────────────
    //
    // GET /api/v1/favorites/exists?userId=...&ean=...
    //
    // 200 → favorite exists
    // 404 → not a favorite
    // 400 → bad userId

    @GetMapping("/exists")
    public ResponseEntity<Void> exists(
            @RequestParam("userId") @NotBlank String userIdRaw,
            @RequestParam("ean") @NotBlank String ean
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("FavoriteController.exists invalid userId='{}'", userIdRaw);
            return ResponseEntity.badRequest().build();
        }

        boolean exists = favoriteRepository.existsByUserIdAndEan(userId, ean);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // ─────────────────────────────────────
    // POST: save a favorite
    // ─────────────────────────────────────
    //
    // iOS sends:
    //  POST /api/v1/favorites
    //  {
    //    "userId": "9630-...-4500",
    //    "ean": "00817939000052",
    //    "productName": "Method All-Purpose Cleaner ...",
    //    "brand": "Method",
    //    "ratingLetter": "B",
    //    "safetyScore": 3.5
    //  }
    //
    // If it already exists → 200 OK with existing DTO.
    // If new → 201 Created with new DTO.

    @PostMapping
    public ResponseEntity<FavoriteDTO> saveFavorite(
            @Valid @RequestBody SaveFavoriteRequest request
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(request.userId());
        } catch (IllegalArgumentException ex) {
            log.warn("FavoriteController.saveFavorite invalid userId='{}'", request.userId());
            return ResponseEntity.badRequest().build();
        }

        // Idempotent on (userId, ean)
        Optional<FavoriteEntity> existing =
                favoriteRepository.findByUserIdAndEan(userId, request.ean());

        if (existing.isPresent()) {
            FavoriteEntity entity = existing.get();
            // Optionally update snapshot fields
            entity.setProductName(request.productName());
            entity.setBrand(request.brand());
            entity.setRatingLetter(request.ratingLetter());
            entity.setSafetyScore(request.safetyScore());
            FavoriteEntity saved = favoriteRepository.save(entity);
            return ResponseEntity.ok(FavoriteMapper.toDTO(saved));
        }

        FavoriteEntity entity = new FavoriteEntity(userId, request.ean());
        entity.setProductName(request.productName());
        entity.setBrand(request.brand());
        entity.setRatingLetter(request.ratingLetter());
        entity.setSafetyScore(request.safetyScore());

        FavoriteEntity saved = favoriteRepository.save(entity);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(FavoriteMapper.toDTO(saved));
    }

    // ─────────────────────────────────────
    // DELETE: remove a favorite
    // ─────────────────────────────────────
    //
    // DELETE /api/v1/favorites?userId=...&ean=...
    //
    // 204 → deleted (or already gone, we treat as success)
    // 400 → bad userId

    @DeleteMapping
    public ResponseEntity<Void> deleteFavorite(
            @RequestParam("userId") @NotBlank String userIdRaw,
            @RequestParam("ean") @NotBlank String ean
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("FavoriteController.deleteFavorite invalid userId='{}'", userIdRaw);
            return ResponseEntity.badRequest().build();
        }

        favoriteService.deleteFavorite(userId, ean);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────

    public record SaveFavoriteRequest(
            @NotBlank String userId,
            @NotBlank String ean,
            String productName,
            String brand,
            String ratingLetter,
            Double safetyScore
    ) {}
}
