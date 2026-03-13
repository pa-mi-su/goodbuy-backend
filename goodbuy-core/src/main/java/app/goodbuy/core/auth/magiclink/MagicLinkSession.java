package app.goodbuy.core.auth.magiclink;

import java.util.UUID;

/**
 * Lightweight result of consuming a magic link.
 * API layer can then create a real session token (user_session) and return it.
 */
public record MagicLinkSession(
        UUID userId,
        String email
) {}
