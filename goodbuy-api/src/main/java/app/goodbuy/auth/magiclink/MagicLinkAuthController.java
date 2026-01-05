package app.goodbuy.auth.magiclink;

import app.goodbuy.adapters.core.sessions.service.SessionService;
import app.goodbuy.core.auth.magiclink.MagicLinkSession;
import app.goodbuy.core.auth.magiclink.port.MagicLinkPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth/magic-link")
@Validated
public class MagicLinkAuthController {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkAuthController.class);

    private final MagicLinkPort magicLinkPort;
    private final SessionService sessionService;

    public MagicLinkAuthController(MagicLinkPort magicLinkPort,
                                   SessionService sessionService) {
        this.magicLinkPort = magicLinkPort;
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

        // MUST NOT leak whether user exists (port enforces that contract)
        magicLinkPort.requestMagicLink(body.email(), body.platform(), body.appVersion(), ip, ua);

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

        MagicLinkSession session = magicLinkPort
                .consumeMagicLink(body.token(), body.platform(), body.appVersion(), ip, ua)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.GONE,
                        "Magic login link has expired. Please request a new one."
                ));

        // Create a REAL session token (stored in user_session)
        // This keeps API from depending on adapters-core entity types.
        String sessionToken = sessionService.createSessionTokenForUserId(
                session.userId().toString(),
                ip,
                ua
        );

        log.info("MagicLinkAuthController.consumeMagicLink SUCCESS userId={} email={}",
                session.userId(), session.email());

        return ResponseEntity.ok(
                new MagicLinkConsumeResponse(
                        session.userId().toString(),
                        session.email(),
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
