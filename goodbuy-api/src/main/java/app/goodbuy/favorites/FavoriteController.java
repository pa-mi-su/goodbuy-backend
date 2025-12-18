package app.goodbuy.favorites;

import app.goodbuy.adapters.core.favorites.FavoriteMapper;
import app.goodbuy.adapters.core.favorites.model.FavoriteEntity;
import app.goodbuy.adapters.core.favorites.repo.FavoriteRepository;
import app.goodbuy.adapters.core.favorites.service.FavoriteService;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.core.favorites.dto.FavoriteDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@Validated
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private static final String AUTH_USER_ATTR = "goodbuyUser";

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
    // GET: list favorites (auth via X-Session-Token)
    // ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<FavoriteDTO>> listFavorites(HttpServletRequest request) {
        UUID userId = requireAuthenticatedUserId(request);

        List<FavoriteEntity> entities = favoriteRepository.findByUserIdOrderBySavedAtDesc(userId);

        if (entities.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<FavoriteDTO> dtos = entities.stream()
                .map(FavoriteMapper::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    // ─────────────────────────────────────
    // GET: exists (auth via X-Session-Token)
    // ─────────────────────────────────────
    //
    // GET /api/v1/favorites/exists?ean=...
    //
    // 200 → favorite exists
    // 404 → not a favorite

    @GetMapping("/exists")
    public ResponseEntity<Void> exists(
            @RequestParam("ean") @NotBlank String ean,
            HttpServletRequest request
    ) {
        UUID userId = requireAuthenticatedUserId(request);

        boolean exists = favoriteRepository.existsByUserIdAndEan(userId, ean);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // ─────────────────────────────────────
    // POST: save a favorite (auth via X-Session-Token)
    // ─────────────────────────────────────
    //
    // POST /api/v1/favorites
    // {
    //   "ean": "...",
    //   "productName": "...",
    //   "brand": "...",
    //   "ratingLetter": "B",
    //   "safetyScore": 3.5
    // }
    //
    // If it already exists → 200 OK with existing DTO.
    // If new → 201 Created with new DTO.

    @PostMapping
    public ResponseEntity<FavoriteDTO> saveFavorite(
            @Valid @RequestBody SaveFavoriteRequest requestBody,
            HttpServletRequest request
    ) {
        UUID userId = requireAuthenticatedUserId(request);

        String ean = requestBody.ean().trim();

        Optional<FavoriteEntity> existing =
                favoriteRepository.findByUserIdAndEan(userId, ean);

        if (existing.isPresent()) {
            FavoriteEntity entity = existing.get();
            entity.setProductName(requestBody.productName());
            entity.setBrand(requestBody.brand());
            entity.setRatingLetter(requestBody.ratingLetter());
            entity.setSafetyScore(requestBody.safetyScore());
            FavoriteEntity saved = favoriteRepository.save(entity);
            return ResponseEntity.ok(FavoriteMapper.toDTO(saved));
        }

        FavoriteEntity entity = new FavoriteEntity(userId, ean);
        entity.setProductName(requestBody.productName());
        entity.setBrand(requestBody.brand());
        entity.setRatingLetter(requestBody.ratingLetter());
        entity.setSafetyScore(requestBody.safetyScore());

        FavoriteEntity saved = favoriteRepository.save(entity);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(FavoriteMapper.toDTO(saved));
    }

    // ─────────────────────────────────────
    // DELETE: remove a favorite (auth via X-Session-Token)
    // ─────────────────────────────────────
    //
    // DELETE /api/v1/favorites?ean=...
    //
    // 204 → deleted (or already gone, we treat as success)

    @DeleteMapping
    public ResponseEntity<Void> deleteFavorite(
            @RequestParam("ean") @NotBlank String ean,
            HttpServletRequest request
    ) {
        UUID userId = requireAuthenticatedUserId(request);

        favoriteService.deleteFavorite(userId, ean.trim());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────

    private static UUID requireAuthenticatedUserId(HttpServletRequest request) {
        Object obj = request.getAttribute(AUTH_USER_ATTR);
        if (obj instanceof AppUserEntity user) {
            return user.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid session token is required");
    }

    // ─────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────

    public record SaveFavoriteRequest(
            @NotBlank String ean,
            String productName,
            String brand,
            String ratingLetter,
            Double safetyScore
    ) {}
}
