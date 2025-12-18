package app.goodbuy.adapters.core.sessions.service;

import app.goodbuy.adapters.core.sessions.model.UserSessionEntity;
import app.goodbuy.adapters.core.sessions.repo.UserSessionRepository;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final UserSessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;

    /**
     * Session lifetime in minutes (configurable per env).
     *
     * Examples:
     *  - dev:  10080 (7 days) or 1440 (1 day)
     *  - uat:  10080 (7 days)
     *  - prod: 43200 (30 days) or 10080 (7 days)
     */
    private final int sessionTtlMinutes;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    public SessionService(
            UserSessionRepository sessionRepository,
            AppUserRepository appUserRepository,
            @Value("${goodbuy.auth.session.token-ttl-minutes:43200}")
            int sessionTtlMinutes
    ) {
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    // ─────────────────────────────────────────────────────
    // Create a real session token (stored in user_session)
    // ─────────────────────────────────────────────────────

    /**
     * Creates a new session for the given user and returns the opaque token string
     * that the client stores as X-Session-Token.
     */
    @Transactional
    public String createSessionTokenForUser(AppUserEntity user, String ipAddress, String userAgent) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user must not be null");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(sessionTtlMinutes);

        String token = generateOpaqueToken();

        UserSessionEntity session = new UserSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setToken(token);
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(null);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);

        sessionRepository.save(session);

        log.info("SessionService.createSessionTokenForUser: created session id={} userId={} expiresAt={}",
                session.getId(), user.getId(), expiresAt);

        return token;
    }

    // ─────────────────────────────────────────────────────
    // Validate session token -> resolve user
    // ─────────────────────────────────────────────────────

    /**
     * Resolve a user from a real session token stored in user_session:
     *  - token must exist
     *  - must not be expired
     *  - must not be revoked
     */
    @Transactional(readOnly = true)
    public Optional<AppUserEntity> findUserBySessionToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String trimmed = token.trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Optional<UserSessionEntity> sessionOpt = sessionRepository.findByToken(trimmed);
        if (sessionOpt.isEmpty()) return Optional.empty();

        UserSessionEntity session = sessionOpt.get();

        if (!session.isActiveAt(now)) {
            log.info("SessionService.findUserBySessionToken: inactive session token sessionId={} userId={} expired={} revoked={}",
                    session.getId(),
                    session.getUserId(),
                    session.isExpiredAt(now),
                    session.isRevoked());
            return Optional.empty();
        }

        return appUserRepository.findById(session.getUserId());
    }

    /**
     * Small helper for filters/controllers.
     */
    @Transactional(readOnly = true)
    public boolean isValidSessionToken(String token) {
        return findUserBySessionToken(token).isPresent();
    }

    // ─────────────────────────────────────────────────────
    // Revoke helpers (optional; used later for logout)
    // ─────────────────────────────────────────────────────

    /**
     * Revoke a session token immediately (logout).
     * Returns true if a session was found and revoked.
     */
    @Transactional
    public boolean revokeSessionToken(String token) {
        if (token == null || token.isBlank()) return false;

        String trimmed = token.trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Optional<UserSessionEntity> sessionOpt = sessionRepository.findByToken(trimmed);
        if (sessionOpt.isEmpty()) return false;

        UserSessionEntity session = sessionOpt.get();
        if (session.getRevokedAt() != null) return true;

        session.setRevokedAt(now);
        sessionRepository.save(session);

        log.info("SessionService.revokeSessionToken: revoked sessionId={} userId={}",
                session.getId(), session.getUserId());

        return true;
    }

    // ─────────────────────────────────────────────────────
    // Token generator
    // ─────────────────────────────────────────────────────

    /**
     * Generates a URL-safe opaque token.
     * 32 random bytes -> ~43 chars base64url (no padding).
     */
    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return base64Url.encodeToString(bytes);
    }
}
