package app.goodbuy.adapters.core.users.service;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import app.goodbuy.adapters.core.history.repo.ScanHistoryRepository;
import app.goodbuy.adapters.core.favorites.repo.FavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    private final AppUserRepository appUserRepository;
    private final ScanHistoryRepository scanHistoryRepository;
    private final FavoriteRepository favoriteRepository;

    public AppUserService(AppUserRepository appUserRepository,
                          ScanHistoryRepository scanHistoryRepository,
                          FavoriteRepository favoriteRepository) {
        this.appUserRepository = appUserRepository;
        this.scanHistoryRepository = scanHistoryRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * Register a user by email, or "touch" their lastSeenAt if already present.
     *
     * This is used by the /api/v1/users/register endpoint.
     */
    @Transactional
    public AppUserEntity registerOrTouch(String email, String platform, String appVersion) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        String trimmedEmail = email.trim().toLowerCase();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Make sure AppUserRepository has: Optional<AppUserEntity> findByEmailIgnoreCase(String email);
        Optional<AppUserEntity> existingOpt = appUserRepository.findByEmailIgnoreCase(trimmedEmail);

        AppUserEntity user = existingOpt.orElseGet(AppUserEntity::new);

        if (user.getId() == null) {
            // new user
            user.setEmail(trimmedEmail);
            user.setPlatform(platform);
            user.setAppVersion(appVersion);
            user.setCreatedAt(now);
            log.info("AppUserService.registerOrTouch: creating new user email='{}'", trimmedEmail);
        } else {
            // existing user, keep email but update metadata
            log.info("AppUserService.registerOrTouch: touching existing user id={} email='{}'",
                    user.getId(), user.getEmail());
            user.setPlatform(platform != null ? platform : user.getPlatform());
            user.setAppVersion(appVersion != null ? appVersion : user.getAppVersion());
        }

        user.setLastSeenAt(now);

        return appUserRepository.save(user);
    }

    // ─────────────────────────────────────────────────────
    // Profile helpers used by /me and /me/email
    // ─────────────────────────────────────────────────────

    /**
     * Lookup user by ID (UUID string). If not found, THROW.
     *
     * For /api/v1/users/me we now surface a proper 404 so the client
     * can clear its local session and re-register if the backend DB
     * has been reset or the user row is gone.
     */
    @Transactional
    public AppUserEntity getOrCreateById(String userId) {
        if (userId == null || userId.isBlank()) {
            // Bad client input → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be blank");
        }

        final UUID uuid;
        try {
            uuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException ex) {
            // Malformed UUID → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId format");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AppUserEntity user = appUserRepository.findById(uuid)
                .orElseThrow(() -> {
                    log.warn("getOrCreateById: user not found for id={}", userId);
                    // Missing row → 404 so client can reset session
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });

        user.setLastSeenAt(now);
        return appUserRepository.save(user);
    }

    /**
     * Update the user's email address.
     */
    @Transactional
    public AppUserEntity updateEmail(String userId, String newEmail) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        UUID uuid = UUID.fromString(userId.trim());
        AppUserEntity user = appUserRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found for id=" + userId));

        String trimmedEmail = newEmail.trim().toLowerCase();
        user.setEmail(trimmedEmail);
        user.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));

        log.info("AppUserService.updateEmail: updated email for user id={} email='{}'",
                user.getId(), trimmedEmail);

        return appUserRepository.save(user);
    }

    /**
     * Total scans for this user (for profile metrics).
     *
     * Uses ScanHistoryRepository.countByUserId.
     */
    @Transactional(readOnly = true)
    public long countScansForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        try {
            UUID uuid = UUID.fromString(userId.trim());
            return scanHistoryRepository.countByUserId(uuid);
        } catch (IllegalArgumentException ex) {
            // bad UUID format → treat as 0 scans instead of blowing up profile screen
            log.warn("countScansForUser: invalid userId='{}'", userId);
            return 0L;
        }
    }

    /**
     * Total favorites for this user (for profile metrics).
     *
     * Uses FavoriteRepository.countByUserId.
     */
    @Transactional(readOnly = true)
    public long countFavoritesForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        try {
            UUID uuid = UUID.fromString(userId.trim());
            return favoriteRepository.countByUserId(uuid);
        } catch (IllegalArgumentException ex) {
            log.warn("countFavoritesForUser: invalid userId='{}'", userId);
            return 0L;
        }
    }

    // ─────────────────────────────────────────────────────
    // Legacy helper used by HistoryController
    // ─────────────────────────────────────────────────────

    /**
     * Legacy-style helper for code that already calls ensureUserExistsById(UUID, platform, appVersion).
     *
     * NOW: if the user exists, updates lastSeenAt and optionally platform/appVersion.
     *      if not, THROWS (we do NOT create placeholder users without email anymore).
     */
    @Transactional
    public AppUserEntity ensureUserExistsById(UUID userId, String platform, String appVersion) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found for id=" + userId));

        if (platform != null && !platform.isBlank()) {
            user.setPlatform(platform);
        }
        if (appVersion != null && !appVersion.isBlank()) {
            user.setAppVersion(appVersion);
        }
        user.setLastSeenAt(now);

        log.info("AppUserService.ensureUserExistsById: touched existing user id={}", user.getId());
        return appUserRepository.save(user);
    }
}
