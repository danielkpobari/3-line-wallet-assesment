package com.example.test.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Outermost filter: gives every request a correlation id and writes one access-log line when it completes.
 *
 * <ul>
 *   <li>Reuses a well-formed incoming {@code X-Request-Id} or generates one, puts it in the logging MDC as
 *       {@code requestId}, echoes it in the response and stores it on audit events.</li>
 *   <li>Runs before Spring Security, so 401/403 responses carry the id and are logged too.</li>
 *   <li>Logs method, path (no query string, which may hold personal data), status and duration. The
 *       {@code userId} MDC key, set later by {@link CurrentUserMdcInterceptor}, is included automatically.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        // Only trust ids that cannot inject anything into logs.
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(CurrentUserMdcInterceptor.MDC_USER_ID);
        }
    }

    private static void logAccess(HttpServletRequest request, HttpServletResponse response, long millis) {
        int status = response.getStatus();
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            log.debug("{} {} -> {} ({} ms)", request.getMethod(), path, status, millis);
        } else if (status >= 500) {
            log.error("{} {} -> {} ({} ms)", request.getMethod(), path, status, millis);
        } else if (status >= 400) {
            log.warn("{} {} -> {} ({} ms)", request.getMethod(), path, status, millis);
        } else {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, millis);
        }
    }
}
