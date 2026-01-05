package app.goodbuy.adapters.core.users.service;

import app.goodbuy.adapters.core.notifications.MagicLoginEmailService;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.model.MagicLinkTokenEntity;
import app.goodbuy.adapters.core.users.repo.AppUserRepository;
import app.goodbuy.adapters.core.users.repo.MagicLinkTokenRepository;
import app.goodbuy.core.auth.magiclink.MagicLinkSession;
import app.goodbuy.core.auth.magiclink.port.MagicLinkPort;
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

@Service
public class MagicLinkTokenService implements MagicLinkPort {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkTokenService.class);

    private final AppUserRepository appUserRepository;
    private final MagicLinkTokenRepository tokenRepository;
    private final MagicLoginEmailService emailService;

    private final String magicLinkAppBase;
    private final int tokenTtlMinutes;

    public MagicLinkTokenService(
            AppUserRepository appUserRepository,
            MagicLinkTokenRepository tokenRepository,
            MagicLoginEmailService emailService,
            @Value("${goodbuy.auth.magic-login.app-link-base:goodbuyapp://magic-login?token=}")
            String magicLinkAppBase,
            @Value("${goodbuy.auth.magic-login.token-ttl-minutes:15}")
            int tokenTtlMinutes
    ) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.magicLinkAppBase = magicLinkAppBase;
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    // ─────────────────────────────────────────────────────────────
    // Port implementation (API talks to these)
    // ─────────────────────────────────────────────────────────────

    /**
     * Core contract:
     *  - MUST be idempotent
     *  - MUST NOT leak whether user exists
     *  - If email exists: create token + email it
     *  - If email does not exist: no-op
     */
    @Override
    @Transactional
    public void requestMagicLink(
            String email,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    ) {
        // Safe to treat blank as bad request (caller bug), but do NOT leak existence for real emails.
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
        }

        String trimmedEmail = email.trim().toLowerCase();

        log.info("MagicLinkTokenService.requestMagicLink: email='{}' platform='{}' appVersion='{}' ip={}",
                trimmedEmail, safe(platform), safe(appVersion), safe(ipAddress));

        // no-op if not found (no leak)
        createLoginTokenForEmail(trimmedEmail, ipAddress, userAgent);
    }

    /**
     * Core contract:
     *  - Return Optional.empty() for invalid/expired/used token (controller maps to 410)
     *  - Do NOT throw 404/410 from here for token invalid cases
     */
    @Override
    @Transactional
    public Optional<MagicLinkSession> consumeMagicLink(
            String token,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    ) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token must not be blank");
        }

        String trimmed = token.trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Optional<MagicLinkTokenEntity> tokenOpt = tokenRepository.findByToken(trimmed);
        if (tokenOpt.isEmpty()) {
            log.info("MagicLinkTokenService.consumeMagicLink: token not found token='{}' platform='{}' appVersion='{}' ip={}",
                    previewToken(trimmed), safe(platform), safe(appVersion), safe(ipAddress));
            return Optional.empty();
        }

        MagicLinkTokenEntity tokenEntity = tokenOpt.get();

        if (tokenEntity.isUsed() || tokenEntity.isExpiredAt(now)) {
            log.info("MagicLinkTokenService.consumeMagicLink: token expired/used tokenId={} userId={} expired={} used={}",
                    tokenEntity.getId(), tokenEntity.getUserId(), tokenEntity.isExpiredAt(now), tokenEntity.isUsed());
            return Optional.empty();
        }

        // mark used
        tokenEntity.setUsedAt(now);
        tokenEntity.setIpAddress(ipAddress);
        tokenEntity.setUserAgent(userAgent);
        tokenRepository.save(tokenEntity);

        Optional<AppUserEntity> userOpt = appUserRepository.findById(tokenEntity.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("MagicLinkTokenService.consumeMagicLink: user not found for tokenId={} userId={}",
                    tokenEntity.getId(), tokenEntity.getUserId());
            return Optional.empty();
        }

        AppUserEntity user = userOpt.get();
        user.setLastSeenAt(now);
        AppUserEntity saved = appUserRepository.save(user);

        log.info("MagicLinkTokenService.consumeMagicLink: SUCCESS userId={} email='{}' platform='{}' appVersion='{}' ip={}",
                saved.getId(), saved.getEmail(), safe(platform), safe(appVersion), safe(ipAddress));

        return Optional.of(new MagicLinkSession(saved.getId(), saved.getEmail()));
    }

    // ─────────────────────────────────────────────────────────────
    // Legacy/compat overloads (kept so other callers don't break)
    // ─────────────────────────────────────────────────────────────

    /**
     * Older internal signature some code may still call.
     * Keep it to avoid breakage during refactor.
     */
    @Transactional
    public boolean requestMagicLink(String email, String ipAddress, String userAgent) {
        // Keep same behavior as before: return whether a user existed (internal use only).
        // DO NOT use this from API endpoints (API must not leak existence).
        return createLoginTokenForEmail(email, ipAddress, userAgent).isPresent();
    }

    // ─────────────────────────────────────────────────────────────
    // Existing internal API (kept for now; useful elsewhere)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Optional<MagicLinkTokenEntity> createLoginTokenForEmail(
            String email,
            String ipAddress,
            String userAgent
    ) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
        }

        String trimmedEmail = email.trim().toLowerCase();

        Optional<AppUserEntity> userOpt = appUserRepository.findByEmailIgnoreCase(trimmedEmail);
        if (userOpt.isEmpty()) {
            log.info("MagicLinkTokenService.createLoginTokenForEmail: no user found for email='{}' (no-op)", trimmedEmail);
            return Optional.empty();
        }

        AppUserEntity user = userOpt.get();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(tokenTtlMinutes);

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

        log.info("MagicLinkTokenService.createLoginTokenForEmail: created magic link token id={} token='{}' for userId={} email='{}'",
                tokenEntity.getId(), tokenString, user.getId(), trimmedEmail);

        try {
            emailService.sendMagicLoginEmail(trimmedEmail, deepLink);
        } catch (Exception ex) {
            log.error("MagicLinkTokenService.createLoginTokenForEmail: failed to send magic login email for email='{}' tokenId={}. " +
                            "Token is still persisted; you can use the link manually in dev. Cause={}",
                    trimmedEmail, tokenEntity.getId(), ex.toString());
        }

        return Optional.of(tokenEntity);
    }

    /**
     * Strict internal method (throws 404/410). Keep it unchanged for internal callers.
     * The PORT method consumeMagicLink() uses the softer Optional.empty() behavior.
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
            log.info("MagicLinkTokenService.validateAndConsumeToken: token expired/used for tokenId={} userId={}",
                    tokenEntity.getId(), tokenEntity.getUserId());
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Magic login link has expired. Please request a new one."
            );
        }

        tokenEntity.setUsedAt(now);
        tokenEntity.setIpAddress(ipAddress);
        tokenEntity.setUserAgent(userAgent);
        tokenRepository.save(tokenEntity);

        AppUserEntity user = appUserRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found for magic login token."
                ));

        user.setLastSeenAt(now);
        AppUserEntity saved = appUserRepository.save(user);

        log.info("MagicLinkTokenService.validateAndConsumeToken: token consumed for userId={} email='{}'",
                saved.getId(), saved.getEmail());

        return saved;
    }

    /**
     * Session token validation based on the magic-link token table (legacy behavior).
     * NOTE: Your "real" sessions are in user_session; that logic lives in SessionService.
     */
    @Transactional(readOnly = true)
    public Optional<AppUserEntity> findUserEntityBySessionToken(String token) {
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
            log.info("MagicLinkTokenService.findUserEntityBySessionToken: token not valid (expired or not consumed) tokenId={}",
                    tokenEntity.getId());
            return Optional.empty();
        }

        return appUserRepository.findById(tokenEntity.getUserId());
    }

    @Transactional(readOnly = true)
    public boolean isValidSessionToken(String token) {
        return findUserEntityBySessionToken(token).isPresent();
    }

    // ─────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private static String previewToken(String token) {
        if (token == null) return "-";
        String t = token.trim();
        if (t.length() <= 10) return t;
        return t.substring(0, 4) + "…" + t.substring(t.length() - 4);
    }
}
