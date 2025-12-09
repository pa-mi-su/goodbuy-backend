package app.goodbuy.adapters.core.users.service;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for registering / touching GoodBuy app users.
 *
 * Responsibilities:
 *  - Normalize + lightly validate email
 *  - Insert new app_user row if it does not exist
 *  - Update last_seen_at, platform, app_version on every hit
 */
@Service
@Transactional
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    // Super-light email sanity check, NOT a full RFC validator
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppUserRepository repo;

    public AppUserService(AppUserRepository repo) {
        this.repo = repo;
    }

    /**
     * Register a user by email or update their last_seen_at if they already exist.
     *
     * @param rawEmail   Email from the client (required, will be normalized/lowercased)
     * @param platform   Optional platform string, e.g. "iOS"
     * @param appVersion Optional app version, e.g. "1.0"
     * @return The persisted AppUserEntity
     */
    public AppUserEntity registerOrTouch(String rawEmail, String platform, String appVersion) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("email must not be null");
        }

        String email = normalizeEmail(rawEmail);

        if (!isValidEmail(email)) {
            log.warn("AppUserService.registerOrTouch called with invalid email='{}'", rawEmail);
            throw new IllegalArgumentException("Invalid email address");
        }

        OffsetDateTime now = OffsetDateTime.now();

        AppUserEntity user = repo.findByEmail(email)
                .orElseGet(() -> {
                    log.info("Creating new app_user for email='{}'", email);
                    AppUserEntity u = new AppUserEntity();
                    u.setEmail(email);
                    u.setCreatedAt(now);
                    return u;
                });

        // Always bump last_seen_at + latest client info
        user.setLastSeenAt(now);
        user.setPlatform(platform);
        user.setAppVersion(appVersion);

        AppUserEntity saved = repo.save(user);

        if (saved.getId() == null) {
            log.warn("AppUserService: saved user has null id for email='{}'", email);
        } else {
            log.debug("AppUserService: upserted user id={} email='{}'", saved.getId(), email);
        }

        return saved;
    }

    /**
     * Ensure a user exists with the given UUID id.
     *
     * Used when the mobile app only knows a UUID userId (no email yet),
     * e.g. for scan history.
     *
     * Rules:
     *  - If user exists → update last_seen_at (+ platform/appVersion if provided).
     *  - If not → create a new app_user with:
     *        id = userId
     *        email = synthetic placeholder (non-null, unique-ish)
     */
    public AppUserEntity ensureUserExistsById(UUID userId, String platform, String appVersion) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        OffsetDateTime now = OffsetDateTime.now();

        return repo.findById(userId)
                .map(existing -> {
                    existing.setLastSeenAt(now);
                    if (platform != null) {
                        existing.setPlatform(platform);
                    }
                    if (appVersion != null) {
                        existing.setAppVersion(appVersion);
                    }
                    log.debug("AppUserService.ensureUserExistsById: touched existing user id={}", existing.getId());
                    // Entity is managed; will be flushed at tx commit
                    return existing;
                })
                .orElseGet(() -> {
                    String syntheticEmail = buildSyntheticEmail(userId);
                    log.info("AppUserService.ensureUserExistsById: creating new app_user id={} email='{}' (synthetic)",
                            userId, syntheticEmail);

                    AppUserEntity u = new AppUserEntity();
                    // IMPORTANT: we bind this exact UUID so FK from scan_history matches
                    u.setId(userId);
                    u.setEmail(syntheticEmail);
                    u.setCreatedAt(now);
                    u.setLastSeenAt(now);
                    u.setPlatform(platform);
                    u.setAppVersion(appVersion);

                    AppUserEntity saved = repo.save(u);
                    log.debug("AppUserService.ensureUserExistsById: saved new user id={} email='{}'",
                            saved.getId(), saved.getEmail());
                    return saved;
                });
    }

    // ─────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────

    private String normalizeEmail(String raw) {
        String trimmed = raw.trim();
        // citext in Postgres is case-insensitive, but we still normalize to lower
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Build a synthetic but valid email for UUID-based users.
     *
     * Must:
     *  - be non-null
     *  - match EMAIL_PATTERN
     *  - be unique-ish per UUID
     */
    private String buildSyntheticEmail(UUID userId) {
        // Example: uid-7ca5...@anon.goodbuy.app
        return "uid-" + userId.toString() + "@anon.goodbuy.app";
    }
}
