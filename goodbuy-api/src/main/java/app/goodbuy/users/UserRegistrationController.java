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

/**
 * REST endpoint for registering GoodBuy app users by email
 * and fetching/updating a simple profile.
 *
 * iOS registration call:
 *   POST /api/v1/users/register
 *   {
 *     "email": "user@example.com",
 *     "platform": "iOS",
 *     "appVersion": "1.0"
 *   }
 *
 * Profile calls (use X-GoodBuy-User-Id header):
 *
 *   GET /api/v1/users/me
 *   PUT /api/v1/users/me/email
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationController.class);
    private static final String USER_HEADER = "X-GoodBuy-User-Id";

    private final AppUserService appUserService;

    public UserRegistrationController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    // ─────────────────────────────────────────────────────
    // Registration
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
    // Profile: get current user (by X-GoodBuy-User-Id)
    // ─────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(HttpServletRequest request) {
        String userId = extractUserId(request);
        log.info("UserRegistrationController.getMe userId={}", userId);

        final AppUserEntity user;
        try {
            user = appUserService.getOrCreateById(userId);
        } catch (IllegalArgumentException ex) {
            // Invalid UUID or user not found → client bug (never registered)
            log.warn("UserRegistrationController.getMe: invalid or unknown userId='{}'", userId);
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
    // Profile: update email
    // ─────────────────────────────────────────────────────

    @PutMapping("/me/email")
    public ResponseEntity<UserProfileResponse> updateEmail(
            @Valid @RequestBody UpdateEmailRequest body,
            HttpServletRequest request
    ) {
        String userId = extractUserId(request);
        log.info("UserRegistrationController.updateEmail userId={} email='{}'",
                userId, body.getEmail());

        final AppUserEntity user;
        try {
            user = appUserService.updateEmail(userId, body.getEmail());
        } catch (IllegalArgumentException ex) {
            // Either invalid userId or user not found or invalid email → treat as 400
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
    // Helper
    // ─────────────────────────────────────────────────────

    private static String extractUserId(HttpServletRequest request) {
        String userId = request.getHeader(USER_HEADER);
        if (userId == null || userId.isBlank()) {
            // You can later replace this with a proper 400 via @ExceptionHandler
            throw new IllegalStateException("Missing " + USER_HEADER + " header");
        }
        return userId.trim();
    }
}
