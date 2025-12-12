package app.goodbuy.users;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.service.AppUserService;
import app.goodbuy.users.dto.UpdateEmailRequest;
import app.goodbuy.users.dto.UserProfileResponse;
import app.goodbuy.users.dto.UserRegistrationRequest;
import app.goodbuy.users.dto.UserRegistrationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoint for registering GoodBuy app users by email
 * and fetching/updating a simple profile.
 *
 * Registration call (public):
 *   POST /api/v1/users/register
 *
 * Profile calls (protected):
 *   GET /api/v1/users/me
 *   PUT /api/v1/users/me/email
 *
 * Auth for protected endpoints:
 *   - Client sends: X-Session-Token
 *   - Server derives user via SessionTokenAuthFilter and attaches:
 *       request.setAttribute("goodbuyUser", AppUserEntity)
 *
 * IMPORTANT:
 *   - DO NOT require X-GoodBuy-User-Id anymore.
 *   - User identity must come from the authenticated session token.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationController.class);

    /**
     * Must match what SessionTokenAuthFilter sets:
     * request.setAttribute("goodbuyUser", user)
     */
    private static final String AUTH_USER_ATTR = "goodbuyUser";

    private final AppUserService appUserService;

    public UserRegistrationController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    // ─────────────────────────────────────────────────────
    // Registration (PUBLIC)
    // ─────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> register(
            @Valid @RequestBody UserRegistrationRequest request
    ) {
        log.info("UserRegistrationController.register email='{}' platform='{}' appVersion='{}'",
                request.getEmail(), request.getPlatform(), request.getAppVersion());

        AppUserEntity user = appUserService.registerOrTouch(
                request.getEmail(),
                request.getPlatform(),
                request.getAppVersion()
        );

        UserRegistrationResponse resp = new UserRegistrationResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getPlatform(),
                user.getAppVersion(),
                user.getLastSeenAt() == null ? null : user.getLastSeenAt().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ─────────────────────────────────────────────────────
    // Profile: get current user (PROTECTED via X-Session-Token)
    // ─────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(HttpServletRequest request) {
        AppUserEntity authedUser = requireAuthenticatedUser(request);

        String userId = authedUser.getId().toString();
        log.info("UserRegistrationController.getMe userId={}", userId);

        // If you prefer strict behavior, replace getOrCreateById with a getByIdOrThrow.
        final AppUserEntity user;
        try {
            user = appUserService.getOrCreateById(userId);
        } catch (IllegalArgumentException ex) {
            log.warn("UserRegistrationController.getMe: invalid/unknown authed userId='{}'", userId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        long totalScans = appUserService.countScansForUser(userId);
        long favoritesCount = appUserService.countFavoritesForUser(userId);

        UserProfileResponse resp = new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getPlatform(),
                user.getAppVersion(),
                user.getLastSeenAt() == null ? null : user.getLastSeenAt().toString(),
                totalScans,
                favoritesCount
        );

        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────────────
    // Profile: update email (PROTECTED via X-Session-Token)
    // ─────────────────────────────────────────────────────

    @PutMapping("/me/email")
    public ResponseEntity<UserProfileResponse> updateEmail(
            @Valid @RequestBody UpdateEmailRequest body,
            HttpServletRequest request
    ) {
        AppUserEntity authedUser = requireAuthenticatedUser(request);

        String userId = authedUser.getId().toString();
        log.info("UserRegistrationController.updateEmail userId={} email='{}'",
                userId, body.getEmail());

        final AppUserEntity user;
        try {
            user = appUserService.updateEmail(userId, body.getEmail());
        } catch (IllegalArgumentException ex) {
            log.warn("UserRegistrationController.updateEmail: bad input for userId='{}': {}",
                    userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        long totalScans = appUserService.countScansForUser(userId);
        long favoritesCount = appUserService.countFavoritesForUser(userId);

        UserProfileResponse resp = new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getPlatform(),
                user.getAppVersion(),
                user.getLastSeenAt() == null ? null : user.getLastSeenAt().toString(),
                totalScans,
                favoritesCount
        );

        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────

    private static AppUserEntity requireAuthenticatedUser(HttpServletRequest request) {
        Object obj = request.getAttribute(AUTH_USER_ATTR);
        if (obj instanceof AppUserEntity user) {
            return user;
        }

        // If the filter didn’t attach the user (misconfig / accidentally public path),
        // treat as UNAUTHORIZED (not a 500).
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid session token is required");
    }
}
