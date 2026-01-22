package app.goodbuy.api.auth;

import app.goodbuy.adapters.core.sessions.service.SessionService;
import app.goodbuy.adapters.core.users.model.AppUserEntity;
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
import java.time.Instant;
import java.util.Optional;

/**
 * Auth filter that:
 *
 *  - Skips public endpoints (/actuator, /api/v1/auth/magic-link/**, /api/v1/users/register, OPTIONS)
 *  - For everything else, requires a valid X-Session-Token header
 *  - Resolves the user via SessionService.findUserBySessionToken
 *  - Attaches the user as request attribute "goodbuyUser"
 *
 * Returns structured JSON errors so clients can detect session expiry and bounce cleanly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionTokenAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenAuthFilter.class);

    public static final String SESSION_HEADER = "X-Session-Token";
    public static final String AUTH_USER_ATTR = "goodbuyUser";

    private final SessionService sessionService;

    public SessionTokenAuthFilter(SessionService sessionService) {
        this.sessionService = sessionService;
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
            writeJsonError(
                    httpRes,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_REQUIRED",
                    "Valid session token is required",
                    path
            );
            return;
        }

        sessionToken = sessionToken.trim();

        final Optional<AppUserEntity> userOpt;
        try {
            // ✅ REAL SESSION TOKEN VALIDATION (NOT magic token)
            userOpt = sessionService.findUserBySessionToken(sessionToken);
        } catch (Exception ex) {
            // IMPORTANT:
            // This is NOT an auth failure (token might be fine).
            // Return 503 so clients do NOT logout the user.
            log.error("SessionTokenAuthFilter: session lookup error for path={}", path, ex);
            writeJsonError(
                    httpRes,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "AUTH_LOOKUP_FAILED",
                    "Authentication service error. Please try again.",
                    path
            );
            return;
        }

        if (userOpt.isEmpty()) {
            log.debug("SessionTokenAuthFilter: invalid/expired session token for path={}", path);
            writeJsonError(
                    httpRes,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_INVALID_OR_EXPIRED",
                    "Your session has expired. Please sign in again.",
                    path
            );
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

        // ✅ Allow the root endpoint (/) to be public (EB / simple ping)
        if ("/".equals(path)) return true;

        if (path.startsWith("/actuator")) return true;

        // Magic-link auth endpoints must be public
        if (path.startsWith("/api/v1/auth/magic-link")) return true;

        // Register must be public
        if (path.startsWith("/api/v1/users/register")) return true;

        return false;
    }

    private void writeJsonError(HttpServletResponse res,
                                int httpStatus,
                                String code,
                                String message,
                                String path) throws IOException {

        if (res.isCommitted()) return;

        try {
            res.resetBuffer(); // avoid partial writes from downstream
        } catch (Exception ignored) {
            // best-effort
        }

        res.setStatus(httpStatus);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Prevent caching of auth errors
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        res.setHeader("Pragma", "no-cache");

        String body = "{"
                + "\"error\":\"" + (httpStatus == 401 ? "unauthorized" : "error") + "\","
                + "\"code\":\"" + escapeJson(code) + "\","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"path\":\"" + escapeJson(path) + "\","
                + "\"ts\":\"" + Instant.now().toString() + "\""
                + "}";

        res.getWriter().write(body);
        res.flushBuffer();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
