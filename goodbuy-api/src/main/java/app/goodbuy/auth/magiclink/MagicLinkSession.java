package app.goodbuy.auth.magiclink;

/**
 * Simple value object returned by MagicLinkService.consumeMagicLink().
 *
 * This is NOT a JPA entity; it's just a pure data holder.
 */
public record MagicLinkSession(
        String userId,
        String email
) {}
