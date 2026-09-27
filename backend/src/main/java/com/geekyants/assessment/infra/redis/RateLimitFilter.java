package com.geekyants.assessment.infra.redis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Distributed HTTP rate-limiting filter backed by Redis.
 * Protects platform APIs and auth endpoints against DDoS and brute-force traffic.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;

    public RateLimitFilter(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Skip rate-limiting on actuator health and static assets
        if (path.startsWith("/actuator") || path.startsWith("/gateway/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        boolean allowed;

        if (path.startsWith("/dev/tokens")) {
            // Stricter limit on token minting: 40 requests per minute
            allowed = rateLimiter.allowRequest("ratelimit:auth:" + clientIp, 40, Duration.ofMinutes(1));
        } else {
            // General API limit: 200 requests per minute per IP
            allowed = rateLimiter.allowRequest("ratelimit:api:" + clientIp, 200, Duration.ofMinutes(1));
        }

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("""
                    {"status":429,"error":"TOO_MANY_REQUESTS","message":"Enterprise API rate limit exceeded. Please back off and retry."}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
