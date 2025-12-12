package app.goodbuy.api.auth;

import app.goodbuy.adapters.core.users.model.AppUserEntity;
import app.goodbuy.adapters.core.users.service.MagicLinkService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Auth filter that:
 *
 *  - Skips public endpoints (/actuator, /api/v1/auth/magic-link/**, /api/v1/users/register, OPTIONS)
 *  - For everything else, requires a valid X-Session-Token header
 *  - Resolves the user via MagicLinkService.findUserBySessionToken
 *  - Attaches the user as request attribute "goodbuyUser"
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionTokenAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenAuthFilter.class);

    public static final String SESSION_HEADER = "X-Session-Token";
    public static final String AUTH_USER_ATTR = "goodbuyUser";

    private final MagicLinkService magicLinkService;

    public SessionTokenAuthFilter(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = safePath(httpReq);
        String method = httpReq.getMethod();

        // Public / bootstrap endpoints
        if (isPublicPath(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        String sessionToken = httpReq.getHeader(SESSION_HEADER);
        if (sessionToken == null || sessionToken.isBlank()) {
            log.debug("SessionTokenAuthFilter: missing {} for path={}", SESSION_HEADER, path);
            unauthorized(httpRes);
            return;
        }

        sessionToken = sessionToken.trim();

        final Optional<AppUserEntity> userOpt;
        try {
            userOpt = magicLinkService.findUserBySessionToken(sessionToken);
        } catch (Exception ex) {
            // Fail closed: if auth lookup fails, do NOT allow access
            log.error("SessionTokenAuthFilter: session lookup error for path={}", path, ex);
            unauthorized(httpRes);
            return;
        }

        if (userOpt.isEmpty()) {
            log.debug("SessionTokenAuthFilter: invalid/expired session token for path={}", path);
            unauthorized(httpRes);
            return;
        }

        AppUserEntity user = userOpt.get();
        httpReq.setAttribute(AUTH_USER_ATTR, user);

        log.debug("SessionTokenAuthFilter: authenticated userId={} email='{}' for path={}",
                user.getId(), user.getEmail(), path);

        chain.doFilter(request, response);
    }

    private static String safePath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return (uri == null) ? "" : uri;
    }

    private boolean isPublicPath(String path, String method) {
        // CORS preflight should always pass through
        if ("OPTIONS".equalsIgnoreCase(method)) return true;

        if (path.startsWith("/actuator")) return true;

        // Magic-link auth endpoints must be public
        if (path.startsWith("/api/v1/auth/magic-link")) return true;

        // Register must be public
        if (path.startsWith("/api/v1/users/register")) return true;

        return false;
    }

    private void unauthorized(HttpServletResponse res) throws IOException {
        if (res.isCommitted()) return;

        res.resetBuffer(); // avoid partial writes from downstream
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Keep it simple + consistent for clients.
        res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Valid session token is required\"}");
        res.flushBuffer();
    }
}
