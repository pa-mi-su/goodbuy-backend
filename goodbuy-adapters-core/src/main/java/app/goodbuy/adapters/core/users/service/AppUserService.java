package app.goodbuy.adapters.core.users.service;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
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
}
