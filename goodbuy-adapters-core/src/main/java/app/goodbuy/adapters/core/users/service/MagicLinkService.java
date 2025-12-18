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

@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final AppUserRepository appUserRepository;
    private final MagicLinkTokenRepository tokenRepository;
    private final MagicLoginEmailService emailService;

    private final String magicLinkAppBase;
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
            log.info("MagicLinkService.createLoginTokenForEmail: no user found for email='{}' (no-op)", trimmedEmail);
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

        log.info("MagicLinkService.createLoginTokenForEmail: created magic link token id={} token='{}' for userId={} email='{}'",
                tokenEntity.getId(), tokenString, user.getId(), trimmedEmail);

        try {
            emailService.sendMagicLoginEmail(trimmedEmail, deepLink);
        } catch (Exception ex) {
            log.error("MagicLinkService.createLoginTokenForEmail: failed to send magic login email for email='{}' tokenId={}. " +
                            "Token is still persisted; you can use the link manually in dev. Cause={}",
                    trimmedEmail, tokenEntity.getId(), ex.toString());
        }

        return Optional.of(tokenEntity);
    }

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

        log.info("MagicLinkService.validateAndConsumeToken: token consumed for userId={} email='{}'",
                saved.getId(), saved.getEmail());

        return saved;
    }

    public String createSessionToken(AppUserEntity user) {
        String sessionToken = UUID.randomUUID().toString().replace("-", "");

        log.info("MagicLinkService.createSessionToken: created session token for userId={} email='{}'",
                user.getId(), user.getEmail());

        return sessionToken;
    }

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

    /**
     * ✅ Optional helper for the auth filter:
     * - returns true if token header is present and looks like a valid session
     */
    @Transactional(readOnly = true)
    public boolean isValidSessionToken(String token) {
        return findUserBySessionToken(token).isPresent();
    }
}
