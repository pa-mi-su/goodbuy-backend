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
     * Register a user by email.
     *
     * Behavior:
     *  - If email is NEW  → create user and return 201.
     *  - If email EXISTS → 409 CONFLICT with a clean message.
     *
     * No auto-login by email, no SQL error leaks.
     */
    @Transactional
    public AppUserEntity registerOrTouch(String email, String platform, String appVersion) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
        }

        String trimmedEmail = email.trim().toLowerCase();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Ensure AppUserRepository has: Optional<AppUserEntity> findByEmailIgnoreCase(String email);
        Optional<AppUserEntity> existingOpt = appUserRepository.findByEmailIgnoreCase(trimmedEmail);

        if (existingOpt.isPresent()) {
            AppUserEntity existing = existingOpt.get();
            log.info("AppUserService.registerOrTouch: email already registered for id={} email='{}'",
                    existing.getId(), existing.getEmail());

            // Do NOT auto-login or hand out the ID.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "That email is already registered with GoodBuy."
            );
        }

        AppUserEntity user = new AppUserEntity();
        user.setEmail(trimmedEmail);
        user.setPlatform(platform);
        user.setAppVersion(appVersion);
        user.setCreatedAt(now);
        user.setLastSeenAt(now);

        log.info("AppUserService.registerOrTouch: creating new user email='{}'", trimmedEmail);

        return appUserRepository.save(user);
    }

    // ─────────────────────────────────────────────────────
    // Profile helpers used by /me and /me/email
    // ─────────────────────────────────────────────────────

    /**
     * Lookup user by ID (UUID string). If not found, THROW.
     *
     * For /api/v1/users/me we surface a 404 so the client
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
     *
     * Behavior:
     *   - 400 if inputs are bad
     *   - 400 if userId format is invalid
     *   - 404 if user not found
     *   - 409 if another user already uses that email
     *   - No SQL leaks to the client
     */
    @Transactional
    public AppUserEntity updateEmail(String userId, String newEmail) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be blank");
        }
        if (newEmail == null || newEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
        }

        final UUID uuid;
        try {
            uuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId format");
        }

        AppUserEntity user = appUserRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        String trimmedEmail = newEmail.trim().toLowerCase();

        // If they submit the same email (ignoring case), treat as no-op.
        if (trimmedEmail.equalsIgnoreCase(user.getEmail())) {
            log.info("AppUserService.updateEmail: no-op (same email) for user id={}", userId);
            user.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
            return appUserRepository.save(user);
        }

        // Check if another user already owns this email.
        appUserRepository.findByEmailIgnoreCase(trimmedEmail).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                log.info("AppUserService.updateEmail: email '{}' already in use by id={}",
                        trimmedEmail, existing.getId());
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "That email is already in use."
                );
            }
        });

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
