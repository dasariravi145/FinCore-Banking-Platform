package com.fincore.platform.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.common.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token bucket rate limiter for sensitive authentication endpoints.
 * Protects against brute-force credential stuffing and denial of service.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final boolean enabled;
    private final int capacity;
    private final int refillTokens;
    private final long refillDurationMs;
    private final ObjectMapper objectMapper;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${fincore.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${fincore.security.rate-limit.capacity:20}") int capacity,
            @Value("${fincore.security.rate-limit.refill-tokens:20}") int refillTokens,
            @Value("${fincore.security.rate-limit.refill-duration-seconds:60}") long refillDurationSeconds,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationMs = refillDurationSeconds * 1000L;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        // Apply rate limit specifically to auth endpoints
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/auth");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(capacity, refillTokens, refillDurationMs));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP: {} on URI: {}", clientIp, request.getRequestURI());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(refillDurationMs / 1000L));

            String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            ApiErrorResponse error = new ApiErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Rate limit exceeded. Too many authentication attempts. Please try again later.",
                    request.getRequestURI(),
                    correlationId
            );

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Thread-safe in-memory token bucket.
     */
    static class TokenBucket {
        private final int capacity;
        private final int refillTokens;
        private final long refillDurationMs;
        private final AtomicInteger tokens;
        private volatile long lastRefillTimestamp;

        public TokenBucket(int capacity, int refillTokens, long refillDurationMs) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillDurationMs = refillDurationMs;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            int current = tokens.get();
            if (current > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed > refillDurationMs) {
                long intervals = elapsed / refillDurationMs;
                int tokensToAdd = (int) (intervals * refillTokens);
                int updatedTokens = Math.min(capacity, tokens.get() + tokensToAdd);
                tokens.set(updatedTokens);
                lastRefillTimestamp = now;
            }
        }
    }
}

