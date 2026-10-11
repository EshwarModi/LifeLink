package com.lifelink.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Servlet filter providing IP-based rate limiting on sensitive authentication endpoints:
 * {@code POST /login} and {@code POST /register}.
 *
 * <p>Key Security & Memory Guarantees:
 * <ul>
 *   <li><b>Spoofing Protection:</b> By default ({@code lifelink.rate-limit.trust-forwarded-headers=false}),
 *       token buckets are keyed strictly by {@link HttpServletRequest#getRemoteAddr()} and {@code X-Forwarded-For}
 *       is ignored so clients cannot rotate fake header values to bypass limits. Setting
 *       {@code trust-forwarded-headers=true} uses the first entry of {@code X-Forwarded-For} and is
 *       <b>only safe behind a trusted reverse proxy</b> that overwrites client-supplied headers.</li>
 *   <li><b>Bounded Memory:</b> Uses Caffeine caches with {@code expireAfterAccess(10 minutes)} and
 *       {@code maximumSize(10_000)} to prevent unbounded heap growth under IP-rotation attacks.</li>
 *   <li><b>Standards Compliance:</b> Returns HTTP 429 (Too Many Requests) with a {@code Retry-After}
 *       header indicating the number of seconds until a token refills.</li>
 * </ul>
 * </p>
 */
@Component
@Getter
@Setter
public class RateLimitingFilter extends OncePerRequestFilter {

    public static final int DEFAULT_LOGIN_CAPACITY = 10;
    public static final int DEFAULT_REGISTER_CAPACITY = 5;
    public static final int MAX_CACHE_SIZE = 10_000;
    public static final Duration CACHE_EXPIRY = Duration.ofMinutes(10);

    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY)
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    private final Cache<String, Bucket> registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY)
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    /**
     * Whether to trust the first IP in the {@code X-Forwarded-For} header.
     * Defaults to {@code false}. Only safe when deployed behind a trusted reverse proxy
     * that strips or overwrites client-supplied {@code X-Forwarded-For} headers.
     */
    @Value("${lifelink.rate-limit.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders = false;

    /**
     * Maximum allowed {@code POST /login} attempts per minute per IP (default: 10).
     */
    @Value("${lifelink.rate-limit.login-capacity:10}")
    private int loginCapacity = DEFAULT_LOGIN_CAPACITY;

    /**
     * Maximum allowed {@code POST /register} attempts per minute per IP (default: 5).
     */
    @Value("${lifelink.rate-limit.register-capacity:5}")
    private int registerCapacity = DEFAULT_REGISTER_CAPACITY;

    public void resetBuckets() {
        loginBuckets.invalidateAll();
        loginBuckets.cleanUp();
        registerBuckets.invalidateAll();
        registerBuckets.cleanUp();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String servletPath = resolveServletPath(request);

            if ("/login".equals(servletPath)) {
                String clientIp = extractClientIp(request);
                Bucket bucket = loginBuckets.get(clientIp, k -> createBucket(loginCapacity, Duration.ofMinutes(1)));
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (!probe.isConsumed()) {
                    long retryAfterSeconds = computeRetryAfterSeconds(probe);
                    sendRateLimitResponse(response, retryAfterSeconds,
                            "Too many login attempts. Please wait a minute before trying again.");
                    return;
                }
            } else if ("/register".equals(servletPath)) {
                String clientIp = extractClientIp(request);
                Bucket bucket = registerBuckets.get(clientIp, k -> createBucket(registerCapacity, Duration.ofMinutes(1)));
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (!probe.isConsumed()) {
                    long retryAfterSeconds = computeRetryAfterSeconds(probe);
                    sendRateLimitResponse(response, retryAfterSeconds,
                            "Too many registration attempts. Please wait a minute before trying again.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket(int capacity, Duration duration) {
        int safeCapacity = Math.max(1, capacity);
        Refill refill = Refill.intervally(safeCapacity, duration);
        Bandwidth limit = Bandwidth.classic(safeCapacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extracts the client IP address.
     *
     * <p>When {@code trustForwardedHeaders} is {@code false} (the default), this method ignores
     * {@code X-Forwarded-For} and returns {@link HttpServletRequest#getRemoteAddr()} directly.
     * When {@code trustForwardedHeaders} is {@code true}, it returns the first comma-separated entry
     * of {@code X-Forwarded-For} if present and non-blank; this setting is only safe behind a trusted
     * reverse proxy that overwrites the header.</p>
     */
    private String extractClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader != null && !xfHeader.trim().isEmpty()) {
                String firstIp = xfHeader.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveServletPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri != null && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private long computeRetryAfterSeconds(ConsumptionProbe probe) {
        long nanos = probe.getNanosToWaitForRefill();
        if (nanos <= 0) {
            return 60L;
        }
        return Math.max(1L, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    private void sendRateLimitResponse(HttpServletResponse response, long retryAfterSeconds, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(message);
    }
}
