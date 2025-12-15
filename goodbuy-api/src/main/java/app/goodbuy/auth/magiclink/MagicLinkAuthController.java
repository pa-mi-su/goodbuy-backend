package app.goodbuy.auth.magiclink;

import app.goodbuy.adapters.core.sessions.service.SessionService;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.service.MagicLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/magic-link")
@Validated
public class MagicLinkAuthController {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkAuthController.class);

    private final MagicLinkService magicLinkService;
    private final SessionService sessionService;

    public MagicLinkAuthController(MagicLinkService magicLinkService,
                                   SessionService sessionService) {
        this.magicLinkService = magicLinkService;
        this.sessionService = sessionService;
    }

    @PostMapping("/request")
    public ResponseEntity<MagicLinkRequestResponse> requestMagicLink(
            @Valid @RequestBody MagicLinkRequest body,
            HttpServletRequest req
    ) {
        String ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");

        log.info("MagicLinkAuthController.requestMagicLink email='{}' platform='{}' appVersion='{}' ip={}",
                body.email(), body.platform(), body.appVersion(), ip);

        magicLinkService.createLoginTokenForEmail(body.email(), ip, ua);

        return ResponseEntity.ok(new MagicLinkRequestResponse("ok"));
    }

    @PostMapping("/consume")
    public ResponseEntity<MagicLinkConsumeResponse> consumeMagicLink(
            @Valid @RequestBody MagicLinkConsumeRequest body,
            HttpServletRequest req
    ) {
        String ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");

        log.info("MagicLinkAuthController.consumeMagicLink token='{}' ip={}", body.token(), ip);

        // 1) Consume magic link (mark used, validate expiry, update lastSeen, etc.)
        AppUserEntity user = magicLinkService.validateAndConsumeToken(body.token(), ip, ua);

        // 2) Create a REAL session token (stored in user_session)
        String sessionToken = sessionService.createSessionTokenForUser(user, ip, ua);

        log.info("MagicLinkAuthController.consumeMagicLink SUCCESS userId={} email={}",
                user.getId(), user.getEmail());

        return ResponseEntity.ok(
                new MagicLinkConsumeResponse(
                        user.getId().toString(),
                        user.getEmail(),
                        sessionToken
                )
        );
    }

    public record MagicLinkRequest(
            @NotBlank(message = "Email is required") String email,
            String platform,
            String appVersion
    ) {}

    public record MagicLinkRequestResponse(String status) {}

    public record MagicLinkConsumeRequest(
            @NotBlank(message = "Token is required") String token,
            String platform,
            String appVersion
    ) {}

    public record MagicLinkConsumeResponse(
            String userId,
            String email,
            String sessionToken
    ) {}
}
