package app.goodbuy.auth.magiclink;

/**
 * Service responsible for magic-link auth:
 *  - issuing links
 *  - validating and consuming tokens
 *
 * Implementation will be added in the next step:
 *  - Persist tokens in DB
 *  - Enforce expiry + single-use
 *  - Send the email via your mail provider
 */
public interface MagicLinkService {

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
     * Consume a magic-link token and return a lightweight session.
     *
     * Requirements:
     *  - Token must be valid, not expired, and not used before.
     *  - If valid, mark as used and return user identity.
     *  - If invalid/expired/already-used, return null (controller turns that
     *    into a 410 GONE via ResponseStatusException).
     */
    MagicLinkSession consumeMagicLink(
            String token,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    );
}
