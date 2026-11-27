package com.example.kpi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every log line produced while serving a request with a correlation id,
 * and returns that id to the caller.
 *
 * <p>Without it, a user reporting "it failed" gives you nothing to search on:
 * log lines from concurrent requests interleave, and the stack trace behind a
 * 500 cannot be tied to the response the user saw. The id now appears in the
 * {@code X-Correlation-Id} response header, so a screenshot is enough to find
 * the exact request.</p>
 *
 * <p>Runs first so that everything downstream — including the security filters —
 * is covered.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Guards against a caller supplying something huge or unprintable. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = sanitise(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused, so leaving this set would tag the
            // next unrelated request with the previous one's id.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Accepts an inbound id only if it is short and alphanumeric. The value is
     * caller-controlled and ends up in the logs, so anything else risks log
     * injection — a newline would let a caller forge log lines.
     */
    private String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!allowed) {
                return null;
            }
        }
        return candidate;
    }
}

