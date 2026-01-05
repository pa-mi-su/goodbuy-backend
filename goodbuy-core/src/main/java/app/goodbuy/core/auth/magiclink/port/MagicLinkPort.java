package app.goodbuy.core.auth.magiclink.port;

import app.goodbuy.core.auth.magiclink.MagicLinkSession;

import java.util.Optional;

/**
 * Core auth port for magic-link login.
 *
 * Implemented by adapters (DB + email) in goodbuy-adapters-core.
 * Called by API controllers in goodbuy-api.
 */
public interface MagicLinkPort {

    /**
     * Request a magic sign-in link for the given email.
     *
     * IMPORTANT:
     *  - MUST be idempotent.
     *  - MUST NOT leak whether the email exists.
     *  - If email exists, generate token + email it.
     *  - If email does not exist, do nothing (still return 200 to caller).
     */
    void requestMagicLink(
            String email,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    );

    /**
     * Consume a magic-link token and return a lightweight session identity.
     *
     * Requirements:
     *  - Token must be valid, not expired, and not used before.
     *  - If valid, mark as used and return user identity.
     *  - If invalid/expired/already-used, return Optional.empty().
     */
    Optional<MagicLinkSession> consumeMagicLink(
            String token,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    );
}
