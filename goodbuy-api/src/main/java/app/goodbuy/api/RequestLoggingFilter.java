package app.goodbuy.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQ_ID_HEADER = "X-Request-Id";
    private static final String REQ_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        long startNs = System.nanoTime();

        // Correlation ID: use incoming header or generate one
        String requestId = req.getHeader(REQ_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(REQ_ID_MDC_KEY, requestId);
        resp.setHeader(REQ_ID_HEADER, requestId);

        String method = req.getMethod();
        String path = req.getRequestURI();
        String ua = safe(req.getHeader("User-Agent"));
        String ip = clientIp(req);

        try {
            chain.doFilter(req, resp);
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            int status = resp.getStatus();

            // Minimal JSON line (easy to parse in CloudWatch / ELK)
            // Avoids pulling a JSON library just for logging.
            String json = String.format(
                    "{" +
                            "\"ts\":\"%s\"," +
                            "\"requestId\":\"%s\"," +
                            "\"method\":\"%s\"," +
                            "\"path\":\"%s\"," +
                            "\"status\":%d," +
                            "\"durationMs\":%d," +
                            "\"ip\":\"%s\"," +
                            "\"userAgent\":\"%s\"" +
                            "}",
                    Instant.now().toString(),
                    escape(requestId), escape(method), escape(path),
                    status, durationMs,
                    escape(ip), escape(ua)
            );

            // Use info for 2xx/3xx, warn for 4xx, error for 5xx
            if (status >= 500) {
                log.error(json);
            } else if (status >= 400) {
                log.warn(json);
            } else {
                log.info(json);
            }

            MDC.remove(REQ_ID_MDC_KEY);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        // Honor common proxy headers (you can tighten this later behind ALB)
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            return h.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return safe(req.getRemoteAddr());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // very small JSON escaper for quotes/backslashes/newlines
    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"'  -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> b.append(c);
            }
        }
        return b.toString();
    }
}
