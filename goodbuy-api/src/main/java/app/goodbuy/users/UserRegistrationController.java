package app.goodbuy.users;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.service.AppUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for registering GoodBuy app users by email.
 *
 * iOS will call:
 *   POST /api/v1/users/register
 *   {
 *     "email": "user@example.com",
 *     "platform": "iOS",
 *     "appVersion": "1.0"
 *   }
 *
 * Returns:
 *   {
 *     "id": "<uuid>",
 *     "email": "user@example.com",
 *     "platform": "iOS",
 *     "appVersion": "1.0",
 *     "lastSeenAt": "2025-01-01T12:00:00Z"
 *   }
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationController.class);

    private final AppUserService appUserService;

    public UserRegistrationController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

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
                user.getLastSeenAt().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ─────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────

    public static class UserRegistrationRequest {

        @NotBlank
        @Email
        private String email;

        private String platform;
        private String appVersion;

        public UserRegistrationRequest() {
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }
    }

    public static class UserRegistrationResponse {

        private String id;
        private String email;
        private String platform;
        private String appVersion;
        private String lastSeenAt;

        public UserRegistrationResponse() {}

        public UserRegistrationResponse(
                String id,
                String email,
                String platform,
                String appVersion,
                String lastSeenAt
        ) {
            this.id = id;
            this.email = email;
            this.platform = platform;
            this.appVersion = appVersion;
            this.lastSeenAt = lastSeenAt;
        }

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getPlatform() {
            return platform;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public String getLastSeenAt() {
            return lastSeenAt;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }

        public void setLastSeenAt(String lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
        }
    }
}
