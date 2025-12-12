package app.goodbuy.adapters.core.users.service;

import app.goodbuy.adapters.core.notifications.MagicLoginEmailService;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.model.MagicLinkTokenEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import app.goodbuy.adapters.core.users.repo.MagicLinkTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Core logic for passwordless magic-link login:
 *
 *  - createLoginTokenForEmail(email, ip, ua)
 *      -> if user exists, creates token, sends email via SES
 *      -> if user does NOT exist, no-op (for security) and returns Optional.empty()
 *
 *  - validateAndConsumeToken(token, ip, ua)
 *      -> validates token, marks used, returns the AppUserEntity
 *
 *  - createSessionToken(user)
 *      -> creates an opaque session token string for the user
 *
 *  - findUserBySessionToken(token)
 *      -> resolve a user from a (magic-link) session token
 */
@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final AppUserRepository appUserRepository;
    private final MagicLinkTokenRepository tokenRepository;
    private final MagicLoginEmailService emailService;

    /**
     * Base deep-link for the iOS app, e.g.:
     *   goodbuyapp://magic-login?token=
     */
    private final String magicLinkAppBase;

    /**
     * Lifetime of a token in minutes.
     */
    private final int tokenTtlMinutes;

    public MagicLinkService(AppUserRepository appUserRepository,
                            MagicLinkTokenRepository tokenRepository,
                            MagicLoginEmailService emailService,
                            @Value("${goodbuy.auth.magic-login.app-link-base:goodbuyapp://magic-login?token=}")
                            String magicLinkAppBase,
                            @Value("${goodbuy.auth.magic-login.token-ttl-minutes:15}")
                            int tokenTtlMinutes) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.magicLinkAppBase = magicLinkAppBase;
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    // ─────────────────────────────────────────────────────
    // Create magic link for an email (request flow)
    // ─────────────────────────────────────────────────────

    /**
     * Create a login token if the email exists, send email, and return the token entity.
     *
     * Security: this method NEVER throws 404 for "email not found" because your
     * controller wants to always respond 200 with a generic message.
     */
    @Transactional
    public Optional<MagicLinkTokenEntity> createLoginTokenForEmail(String email,
                                                                   String ipAddress,
                                                                   String userAgent) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
        }

        String trimmedEmail = email.trim().toLowerCase();

        Optional<AppUserEntity> userOpt = appUserRepository.findByEmailIgnoreCase(trimmedEmail);
        if (userOpt.isEmpty()) {
            // Do NOT reveal that the email doesn't exist.
            log.info("MagicLinkService.createLoginTokenForEmail: no user found for email='{}' (no-op)", trimmedEmail);
            return Optional.empty();
        }

        AppUserEntity user = userOpt.get();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(tokenTtlMinutes);

        // Random opaque token that will go into the deep link
        String tokenString = UUID.randomUUID().toString().replace("-", "");

        MagicLinkTokenEntity tokenEntity = new MagicLinkTokenEntity();
        tokenEntity.setId(UUID.randomUUID());
        tokenEntity.setUserId(user.getId());
        tokenEntity.setToken(tokenString);
        tokenEntity.setCreatedAt(now);
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setUsedAt(null);
        tokenEntity.setIpAddress(ipAddress);
        tokenEntity.setUserAgent(userAgent);

        tokenRepository.save(tokenEntity);

        String deepLink = magicLinkAppBase + tokenString;

        log.info("MagicLinkService.createLoginTokenForEmail: created magic link token id={} token='{}' for userId={} email='{}'",
                tokenEntity.getId(), tokenString, user.getId(), trimmedEmail);

        // ── IMPORTANT DEV BEHAVIOR ───────────────────────────
        // In dev, SES may not have credentials. We MUST NOT let that
        // blow up the transaction, or the token row gets rolled back.
        // So: try to send the email, but swallow failures.
        try {
            emailService.sendMagicLoginEmail(trimmedEmail, deepLink);
        } catch (Exception ex) {
            log.error("MagicLinkService.createLoginTokenForEmail: failed to send magic login email for email='{}' tokenId={}. " +
                            "Token is still persisted; you can use the link manually in dev. Cause={}",
                    trimmedEmail, tokenEntity.getId(), ex.toString());
        }

        return Optional.of(tokenEntity);
    }

    // ─────────────────────────────────────────────────────
    // Validate & consume token (consume flow)
    // ─────────────────────────────────────────────────────

    /**
     * Validate and consume a magic-link token, returning the associated user.
     *
     * - 400 if token blank
     * - 404 if token not found
     * - 410 if expired or already used
     */
    @Transactional
    public AppUserEntity validateAndConsumeToken(String token, String ipAddress, String userAgent) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token must not be blank");
        }

        MagicLinkTokenEntity tokenEntity = tokenRepository.findByToken(token.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Magic login link is invalid."
                ));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (tokenEntity.isUsed() || tokenEntity.isExpiredAt(now)) {
            log.info("MagicLinkService.validateAndConsumeToken: token expired/used for tokenId={} userId={}",
                    tokenEntity.getId(), tokenEntity.getUserId());
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Magic login link has expired. Please request a new one."
            );
        }

        // Mark token as used
        tokenEntity.setUsedAt(now);
        // Optionally update last IP / UA for audit
        tokenEntity.setIpAddress(ipAddress);
        tokenEntity.setUserAgent(userAgent);
        tokenRepository.save(tokenEntity);

        // Load user
        AppUserEntity user = appUserRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found for magic login token."
                ));

        user.setLastSeenAt(now);
        AppUserEntity saved = appUserRepository.save(user);

        log.info("MagicLinkService.validateAndConsumeToken: token consumed for userId={} email='{}'",
                saved.getId(), saved.getEmail());

        return saved;
    }

    // ─────────────────────────────────────────────────────
    // Session token creation (for client to store)
    // ─────────────────────────────────────────────────────

    /**
     * Create an opaque session token for the given user.
     *
     * For now this just generates a random string and logs it.
     * (We’re currently reusing the magic-link token as the session token,
     * but this is here if we later want a separate session table.)
     */
    public String createSessionToken(AppUserEntity user) {
        String sessionToken = UUID.randomUUID().toString().replace("-", "");

        log.info("MagicLinkService.createSessionToken: created session token for userId={} email='{}'",
                user.getId(), user.getEmail());

        return sessionToken;
    }

    // ─────────────────────────────────────────────────────
    // Resolve user from session token (used by filter)
    // ─────────────────────────────────────────────────────

    /**
     * Resolve a user from a session token.
     *
     * We currently treat the magic-link token itself as the session token:
     *  - token must exist
     *  - must be expiredAt > now
     *  - must have been consumed at least once (usedAt != null)
     */
    @Transactional(readOnly = true)
    public Optional<AppUserEntity> findUserBySessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String trimmed = token.trim();

        Optional<MagicLinkTokenEntity> tokenOpt = tokenRepository.findByToken(trimmed);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        MagicLinkTokenEntity tokenEntity = tokenOpt.get();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (tokenEntity.isExpiredAt(now) || tokenEntity.getUsedAt() == null) {
            log.info("MagicLinkService.findUserBySessionToken: token not valid (expired or not consumed) tokenId={}",
                    tokenEntity.getId());
            return Optional.empty();
        }

        return appUserRepository.findById(tokenEntity.getUserId());
    }
}
